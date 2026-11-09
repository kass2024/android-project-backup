#!/usr/bin/env python3
"""Send new HeyStar staff and student infos from a computer on the school Wi-Fi.

The website runs off the school network, so it cannot open 192.168.x.x.
This helper listens on 127.0.0.1:8788. Settings → Update infos calls it and
shows each person as person/merge is sent.

Existing people, enrolled faces, matching cards, and branding are left as they
are. Nothing is deleted, and device/setUiConfig is never called.
"""
from __future__ import annotations

import ctypes
import json
import os
import re
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from base64 import b64encode
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "127.0.0.1"
PORT = 8788
SYNC_LOCK = threading.Lock()


def card_norm(raw) -> str:
    text = str(raw or "").strip()
    if not text:
        return ""
    if re.fullmatch(r"\d+", text):
        return text
    return re.sub(r"[^A-Fa-f0-9]", "", text).upper()


def plan_changes(existing: dict, people: list) -> tuple[list, int]:
    """Only new people, edited names, and new or changed cards."""
    jobs = []
    skipped = 0
    for person in people:
        sn = str(person.get("sn") or "").strip()
        if not re.fullmatch(r"[ST]\d+", sn):
            continue
        name = str(person.get("name") or "Person").strip()[:60] or "Person"
        want_card = card_norm(person.get("cardNo") or "")
        card_label = str(person.get("card") or "").strip() or want_card
        role = "Student" if sn.startswith("S") else "Staff"
        have = existing.get(sn)
        if have is not None:
            have_name = str(have.get("name") or "").strip()
            have_card = card_norm(have.get("cardNo") or "")
            name_same = have_name.lower() == name.lower()
            card_same = want_card == "" or have_card.lower() == want_card.lower()
            if name_same and card_same:
                skipped += 1
                continue
            parts = []
            if not name_same:
                parts.append("name")
            if want_card and not card_same:
                parts.append("card " + card_label)
            line = f"{role} · {name} — " + (", ".join(parts) if parts else "updated")
            jobs.append(
                {
                    "sn": sn,
                    "name": name,
                    "cardNo": want_card,
                    "role": role,
                    "is_new": False,
                    "rename": not name_same,
                    "card": bool(want_card) and not card_same,
                    "line": line,
                }
            )
            continue
        extra = f" card {card_label}" if card_label else ""
        jobs.append(
            {
                "sn": sn,
                "name": name,
                "cardNo": want_card,
                "role": role,
                "is_new": True,
                "rename": False,
                "card": want_card != "",
                "line": f"{role} · {name} — added{extra}",
            }
        )
    return jobs, skipped


def origin_ok(origin: str) -> bool:
    if not origin:
        return True
    parts = urllib.parse.urlparse(origin)
    host = (parts.hostname or "").lower()
    if parts.scheme == "https" and host == "schoolmis.xanderglobalacademy.com":
        return True
    if parts.scheme == "http" and host in ("localhost", "127.0.0.1"):
        return True
    return False


def web_ok(web: str) -> bool:
    parts = urllib.parse.urlparse(web)
    host = (parts.hostname or "").lower()
    if parts.scheme == "https" and host == "schoolmis.xanderglobalacademy.com":
        return True
    if parts.scheme == "http" and host in ("localhost", "127.0.0.1"):
        return True
    return False


def device_post(ip: str, password: str, path: str, body: dict, timeout: int = 20, retries: int = 3) -> dict:
    data = json.dumps(body).encode("utf-8")
    auth = "Basic " + b64encode(f"admin:{password}".encode()).decode()
    last_error: Exception | None = None
    for attempt in range(max(1, retries)):
        req = urllib.request.Request(
            f"http://{ip}:8090/cgi-bin/js/{path.lstrip('/')}",
            data=data,
            method="POST",
            headers={
                "Content-Type": "application/json; charset=UTF-8",
                "Authorization": auth,
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8", "replace")
            payload = json.loads(raw)
            if not isinstance(payload, dict):
                raise RuntimeError("Terminal returned an unexpected response")
            return payload
        except urllib.error.HTTPError as exc:
            raw = exc.read().decode("utf-8", "replace")
            try:
                payload = json.loads(raw)
            except Exception:
                payload = {"code": str(exc.code), "msg": raw[:180] or str(exc.reason)}
            if isinstance(payload, dict):
                return payload
            raise
        except (ConnectionResetError, TimeoutError, urllib.error.URLError) as exc:
            last_error = exc
            time.sleep(0.4 * (attempt + 1))
    raise RuntimeError(f"Terminal connection dropped ({last_error})")


def computer_ipv4() -> str:
    """Address this computer uses to reach the school Wi-Fi."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
    except Exception:
        return ""
    finally:
        sock.close()
    if ip.startswith("127.") or ip.startswith("169.254."):
        return ""
    return ip


def tcp_open(ip: str, port: int = 8090, timeout: float = 0.3) -> bool:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        sock.connect((ip, port))
        return True
    except Exception:
        return False
    finally:
        sock.close()


def scan_port_8090(prefix: str) -> list[str]:
    found: list[str] = []
    lock = threading.Lock()

    def check(host: str) -> None:
        if tcp_open(host):
            with lock:
                found.append(host)

    batch: list[threading.Thread] = []
    for i in range(1, 255):
        thread = threading.Thread(target=check, args=(prefix + str(i),), daemon=True)
        batch.append(thread)
        thread.start()
        if len(batch) >= 64:
            for thread in batch:
                thread.join()
            batch = []
    for thread in batch:
        thread.join()
    return found


def search_device_key(value) -> str:
    if isinstance(value, list):
        for item in value:
            hit = search_device_key(item)
            if hit:
                return hit
        return ""
    if not isinstance(value, dict):
        return ""
    for key in ("deviceKey", "sn", "devSn", "serialNo", "serial", "device_sn"):
        hit = str(value.get(key) or "").strip()
        if hit and hit.lower() not in ("null", "none"):
            return hit
    for item in value.values():
        hit = search_device_key(item)
        if hit:
            return hit
    return ""


def probe_terminal(ip: str, password: str, device_key: str) -> dict | None:
    passwords: list[str] = []
    for candidate in (password, "123456"):
        candidate = str(candidate or "").strip()
        if candidate and candidate not in passwords:
            passwords.append(candidate)
    if not passwords:
        passwords = ["123456"]
    for pw in passwords:
        try:
            ping = device_post(ip, pw, "device/getConfig", {"type": 1}, timeout=4, retries=1)
        except Exception:
            continue
        if str(ping.get("code") or "") != "000":
            continue
        key = search_device_key(ping)
        if device_key and key and key.lower() != device_key.lower():
            continue
        return {"device_ip": ip, "device_key": key or device_key}
    return None


def find_terminal(saved_ip: str, device_key: str, password: str) -> dict:
    """Use the saved address when it answers. Otherwise scan this Wi-Fi."""
    saved_ip = str(saved_ip or "").strip()
    device_key = str(device_key or "").strip()
    if re.fullmatch(r"(?:\d{1,3}\.){3}\d{1,3}", saved_ip):
        hit = probe_terminal(saved_ip, password, device_key)
        if hit:
            hit["source"] = "lan"
            hit["message"] = f"Found the terminal at {saved_ip} on this Wi-Fi."
            return hit
    local = computer_ipv4()
    parts = local.split(".")
    matches: list[dict] = []
    if len(parts) == 4 and not local.startswith("172."):
        for ip in scan_port_8090(".".join(parts[:3]) + "."):
            if ip == saved_ip or ip == local:
                continue
            hit = probe_terminal(ip, password, device_key)
            if hit:
                matches.append(hit)
    chosen = None
    if len(matches) == 1:
        chosen = matches[0]
    elif device_key:
        for hit in matches:
            if str(hit.get("device_key") or "").lower() == device_key.lower():
                chosen = hit
                break
    if chosen:
        old = f"Could not open {saved_ip}. " if saved_ip else ""
        chosen["source"] = "lan"
        chosen["message"] = old + f"Found the terminal at {chosen['device_ip']} on this Wi-Fi."
        return chosen
    return {
        "device_ip": saved_ip,
        "device_key": device_key,
        "source": "none",
        "message": (
            f"Could not open {saved_ip}. No HeyStar terminal answered on this Wi-Fi."
            if saved_ip
            else "No HeyStar terminal answered on this Wi-Fi."
        ),
    }


def auth_failed(res: dict) -> bool:
    msg = str(res.get("msg") or "").lower()
    code = str(res.get("code") or "")
    return code in ("401", "403") or "auth" in msg


def fetch_json(url: str, timeout: int = 90) -> dict:
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        payload = json.loads(resp.read().decode("utf-8", "replace"))
    if not isinstance(payload, dict):
        raise RuntimeError("Server roster was not a JSON object")
    return payload


def post_json(url: str, body: dict, timeout: int = 20) -> None:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        resp.read()


def list_people(ip: str, password: str, device_key: str) -> dict:
    found = {}
    page = 1
    while page <= 100:
        res = device_post(
            ip,
            password,
            "person/findList",
            {"deviceKey": device_key, "index": page, "length": 40},
            timeout=25,
        )
        if str(res.get("code") or "") != "000":
            raise RuntimeError(str(res.get("msg") or "Could not read the terminal list"))
        rows = res.get("data")
        if isinstance(rows, dict):
            rows = rows.get("rows") or rows.get("list") or rows.get("records") or [rows]
        if not isinstance(rows, list):
            rows = []
        before = len(found)
        for row in rows:
            if not isinstance(row, dict):
                continue
            sn = str(row.get("sn") or row.get("personSn") or "").strip()
            if not sn:
                continue
            found[sn] = {
                "name": str(row.get("name") or "").strip(),
                "cardNo": card_norm(row.get("cardNo") or row.get("card") or ""),
            }
        if len(rows) < 40 or len(found) == before:
            break
        page += 1
    return found


def run_sync(fields: dict, send) -> None:
    school_id = int(fields.get("school_id") or 0)
    ip = str(fields.get("device_ip") or "").strip()
    device_key = str(fields.get("device_key") or "").strip()
    password = str(fields.get("password") or "").strip() or "123456"
    web = str(fields.get("web") or "").strip().rstrip("/")
    if school_id <= 0:
        send({"type": "error", "success": 0, "message": "Enter a school ID."})
        return
    if not re.fullmatch(r"(?:\d{1,3}\.){3}\d{1,3}", ip):
        send({"type": "error", "success": 0, "message": "Enter the terminal IP address."})
        return
    if not device_key:
        send({"type": "error", "success": 0, "message": "Enter the device key so people already on the terminal are kept."})
        return
    if not web_ok(web):
        send({"type": "error", "success": 0, "message": "The school server address is not allowed."})
        return

    send({"type": "progress", "sent": 0, "total": 0, "label": "Checking the terminal on this Wi-Fi…"})
    try:
        ping = device_post(ip, password, "device/getConfig", {"type": 1}, timeout=4, retries=1)
    except Exception:
        ping = {"code": "ERR", "msg": "Could not connect"}
    if str(ping.get("code") or "") != "000" and not auth_failed(ping):
        send({"type": "progress", "sent": 0, "total": 0, "label": f"Could not open {ip}. Searching this Wi-Fi…", "device_ip": ip})
        found = find_terminal("", device_key, password)
        found_ip = str(found.get("device_ip") or "")
        if found.get("source") == "lan" and found_ip:
            ip = found_ip
            send({"type": "progress", "sent": 0, "total": 0, "label": found.get("message") or f"Found the terminal at {ip}.", "device_ip": ip})
            try:
                ping = device_post(ip, password, "device/getConfig", {"type": 1}, timeout=4, retries=1)
            except Exception as exc:
                send({"type": "error", "success": 0, "message": f"Could not open {ip}. {exc}", "device_ip": ip})
                return
        else:
            send({"type": "error", "success": 0, "message": str(found.get("message") or f"Could not open {ip}."), "device_ip": ip})
            return
    if auth_failed(ping) and password != "123456":
        password = "123456"
        try:
            ping = device_post(ip, password, "device/getConfig", {"type": 1}, timeout=5)
        except Exception as exc:
            send({"type": "error", "success": 0, "message": f"Cannot reach the terminal at {ip}. Stay on the same Wi-Fi. {exc}"})
            return
    if str(ping.get("code") or "") != "000":
        send({"type": "error", "success": 0, "message": "The terminal refused the password or request. Nothing was changed."})
        return

    send({"type": "progress", "sent": 0, "total": 0, "label": "Reading people already on the terminal. Branding and faces stay."})
    try:
        existing = list_people(ip, password, device_key)
    except Exception as exc:
        send({"type": "error", "success": 0, "message": f"Could not read the terminal list. Nothing was changed. {exc}"})
        return

    send({"type": "progress", "sent": 0, "total": 0, "label": "Loading staff and students from the server…"})
    try:
        staff_payload = fetch_json(f"{web}/api/heystar_staff?school_id={school_id}")
        student_payload = fetch_json(f"{web}/api/heystar_students?school_id={school_id}")
    except Exception as exc:
        send({"type": "error", "success": 0, "message": f"Could not load staff and students from the server. {exc}"})
        return
    people = list(staff_payload.get("staff") or []) + list(student_payload.get("students") or [])
    jobs, skipped = plan_changes(existing, people)
    total = len(jobs)
    send(
        {
            "type": "progress",
            "sent": 0,
            "total": total,
            "label": (
                "Nothing new. Existing staff, students, faces, and branding stay on the terminal."
                if total == 0
                else f"Sending {total} update{'s' if total != 1 else ''}. Faces and branding stay."
            ),
        }
    )

    added = renamed = cards = staff_sent = students_sent = 0
    errors = []
    changes = []
    for index, job in enumerate(jobs, start=1):
        body = {"type": 1, "sn": job["sn"], "name": job["name"], "verifyStyle": 0}
        if job["cardNo"]:
            body["cardNo"] = job["cardNo"]
        try:
            res = device_post(ip, password, "person/merge", body, timeout=20)
            ok = str(res.get("code") or "") == "000"
            err = str(res.get("msg") or "person merge failed")
        except Exception as exc:
            ok = False
            err = str(exc)
        if not ok:
            errors.append(f"{job['sn']}: {err}")
            send(
                {
                    "type": "progress",
                    "sent": index,
                    "total": total,
                    "label": "Could not send " + job["line"],
                    "line": "Not sent: " + job["line"],
                }
            )
            continue
        if job["is_new"]:
            added += 1
        if job["rename"]:
            renamed += 1
        if job["card"]:
            cards += 1
        if job["role"] == "Student":
            students_sent += 1
        else:
            staff_sent += 1
        changes.append(job["line"])
        send(
            {
                "type": "progress",
                "sent": index,
                "total": total,
                "label": job["line"],
                "line": job["line"],
            }
        )

    if not errors:
        try:
            post_json(f"{web}/api/heystar_staff_synced", {"school_id": school_id})
        except Exception:
            pass

    message = (
        f"Sent {added} new, {renamed} names, {cards} cards "
        f"({staff_sent} staff, {students_sent} students). Left {skipped} unchanged."
    )
    if errors:
        message += f" {len(errors)} could not be sent."
    message += " Faces, matching cards, and branding were not changed."
    send(
        {
            "type": "done",
            "success": 0 if errors else 1,
            "message": message,
            "added": added,
            "renamed": renamed,
            "cards_synced": cards,
            "staff_sent": staff_sent,
            "students_sent": students_sent,
            "skipped_existing": skipped,
            "changes": changes[:80],
            "errors": errors[:12],
        }
    )


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        print("heystar-lan", self.address_string(), fmt % args)

    def _cors(self) -> bool:
        origin = self.headers.get("Origin") or ""
        if origin and not origin_ok(origin):
            self.send_response(403)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return False
        return True

    def _cors_headers(self) -> None:
        origin = self.headers.get("Origin") or ""
        if origin:
            self.send_header("Access-Control-Allow-Origin", origin)
            self.send_header("Vary", "Origin")
        self.send_header("Access-Control-Allow-Private-Network", "true")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

    def do_OPTIONS(self) -> None:
        if not self._cors():
            return
        self.send_response(204)
        self._cors_headers()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def do_GET(self) -> None:
        if not self._cors():
            return
        path = urllib.parse.urlparse(self.path).path
        if path != "/health":
            self.send_response(404)
            self._cors_headers()
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        raw = b'{"ok":1}'
        self.send_response(200)
        self._cors_headers()
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def do_POST(self) -> None:
        if not self._cors():
            return
        path = urllib.parse.urlparse(self.path).path
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length else b""
        try:
            fields = json.loads(raw.decode("utf-8") or "{}")
        except Exception:
            fields = {}
        if not isinstance(fields, dict):
            fields = {}
        if path == "/discover":
            found = find_terminal(
                str(fields.get("device_ip") or ""),
                str(fields.get("device_key") or ""),
                str(fields.get("password") or ""),
            )
            raw_out = json.dumps(found, ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self._cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(raw_out)))
            self.end_headers()
            self.wfile.write(raw_out)
            return
        if path != "/sync":
            self.send_response(404)
            self._cors_headers()
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        try:
            fields = json.loads(raw.decode("utf-8") or "{}")
        except Exception:
            fields = {}
        if not isinstance(fields, dict):
            fields = {}
        self.send_response(200)
        self._cors_headers()
        self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Transfer-Encoding", "chunked")
        self.send_header("X-Accel-Buffering", "no")
        self.end_headers()
        closed = {"done": False}

        def send(event: dict) -> None:
            if closed["done"]:
                return
            raw = (json.dumps(event, ensure_ascii=False) + "\n").encode("utf-8")
            try:
                self.wfile.write(f"{len(raw):X}\r\n".encode("ascii"))
                self.wfile.write(raw)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
            except Exception:
                closed["done"] = True

        def finish() -> None:
            if closed["done"]:
                return
            closed["done"] = True
            try:
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
            except Exception:
                pass

        if not SYNC_LOCK.acquire(blocking=False):
            send({"type": "error", "success": 0, "message": "An update is already running on this computer."})
            finish()
            return
        try:
            run_sync(fields, send)
        except Exception as exc:
            send({"type": "error", "success": 0, "message": f"Update stopped: {exc}"})
        finally:
            SYNC_LOCK.release()
            finish()


def self_test() -> int:
    existing = {
        "T1": {"name": "Alice", "cardNo": "100"},
        "T2": {"name": "Bob", "cardNo": "200"},
        "S9": {"name": "Cara", "cardNo": "300"},
    }
    people = [
        {"sn": "T1", "name": "Alice", "cardNo": "100", "card": "AA"},
        {"sn": "T2", "name": "Robert", "cardNo": "200", "card": "BB"},
        {"sn": "S9", "name": "Cara", "cardNo": "301", "card": "CC"},
        {"sn": "S10", "name": "Dan", "cardNo": "400", "card": "DD"},
        {"sn": "T3", "name": "Eve", "cardNo": "", "card": ""},
        {"sn": "X1", "name": "Skip", "cardNo": "1"},
        {"sn": "T4", "name": "Frank", "cardNo": "", "card": ""},
    ]
    existing["T4"] = {"name": "Frank", "cardNo": "999"}
    jobs, skipped = plan_changes(existing, people)
    by_sn = {job["sn"]: job for job in jobs}
    assert skipped == 2, skipped  # Alice unchanged; Frank keeps device card
    assert "T1" not in by_sn
    assert by_sn["T2"]["rename"] and not by_sn["T2"]["card"]
    assert by_sn["S9"]["card"] and by_sn["S9"]["role"] == "Student"
    assert by_sn["S10"]["is_new"] and by_sn["S10"]["role"] == "Student"
    assert by_sn["T3"]["is_new"] and by_sn["T3"]["role"] == "Staff"
    assert "T4" not in by_sn
    assert "X1" not in by_sn
    print("self-test ok", len(jobs), "jobs", skipped, "kept")
    return 0


def program_path() -> str:
    if getattr(sys, "frozen", False):
        return os.path.abspath(sys.executable)
    return os.path.abspath(__file__)


def set_console_title(title: str) -> None:
    if os.name != "nt":
        return
    try:
        ctypes.windll.kernel32.SetConsoleTitleW(title)
    except Exception:
        pass


def pause_if_packaged() -> None:
    """Keep the packaged window open so a double-click does not flash closed."""
    if not getattr(sys, "frozen", False):
        return
    if not sys.stdin or not sys.stdin.isatty():
        return
    try:
        print()
        input("Press Enter to close this window...")
    except (EOFError, KeyboardInterrupt):
        pass


def install_startup() -> bool:
    """Start this same file each time the Windows user signs in."""
    target = program_path()
    work = os.path.dirname(target)
    appdata = os.environ.get("APPDATA") or ""
    startup = os.path.join(appdata, "Microsoft", "Windows", "Start Menu", "Programs", "Startup")
    if not appdata:
        print("Could not add the helper to Windows startup. APPDATA is missing.")
        return False
    os.makedirs(startup, exist_ok=True)
    lnk = os.path.join(startup, "HeyStar update helper.lnk")

    def ps_quote(value: str) -> str:
        return "'" + value.replace("'", "''") + "'"

    script = (
        "$ws = New-Object -ComObject WScript.Shell; "
        f"$s = $ws.CreateShortcut({ps_quote(lnk)}); "
        f"$s.TargetPath = {ps_quote(target)}; "
        f"$s.WorkingDirectory = {ps_quote(work)}; "
        "$s.WindowStyle = 1; "
        "$s.Description = 'HeyStar LAN update helper'; "
        "$s.Save()"
    )
    result = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print("Could not add the helper to Windows startup.")
        detail = (result.stderr or result.stdout or "").strip()
        if detail:
            print(detail)
        return False
    print("The helper will also start each time you sign in to Windows.")
    print(lnk)
    return True


def helper_already_running() -> bool:
    """True when this computer is already accepting helper requests."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(0.5)
    try:
        sock.connect((HOST, PORT))
        return True
    except Exception:
        return False
    finally:
        sock.close()


def main() -> int:
    set_console_title("HeyStar update helper")
    if "--self-test" in sys.argv:
        return self_test()
    if any(arg.lower() in ("install", "--install") for arg in sys.argv[1:]):
        install_startup()
        print()
    if helper_already_running():
        print("The helper is already running on this computer.")
        print("Press Update infos now. You can close this window.")
        print()
        print("To start it automatically every time you sign in, run:")
        print("  HeyStarUpdateHelper.exe install")
        pause_if_packaged()
        return 0
    try:
        server = ThreadingHTTPServer((HOST, PORT), Handler)
    except OSError:
        print("The helper is already running on this computer.")
        print("Press Update infos now. You can close this window.")
        print()
        print("To start it automatically every time you sign in, run:")
        print("  HeyStarUpdateHelper.exe install")
        pause_if_packaged()
        return 0
    bundled = " Python is built into this program." if getattr(sys, "frozen", False) else ""
    print("HeyStar update helper", flush=True)
    print("Leave this window open, then press Update infos in the school system.", flush=True)
    print(f"Listening on http://{HOST}:{PORT}.{bundled}", flush=True)
    print(flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print()
        print("The helper stopped.")
        pause_if_packaged()
        return 0
    pause_if_packaged()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
