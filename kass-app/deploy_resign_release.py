#!/usr/bin/env python3
"""Re-sign the release APK so Android 5 and 6 accept the v1 signature."""
from __future__ import annotations

import os
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(r"c:\Users\user\AndroidStudioProjects\kass-app")
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "XnderTechSMS.apk"
OUT = ROOT / "SmartSMS-4.3.102-release.apk"
SIGNER = Path(r"C:\Users\user\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat")


def props() -> dict[str, str]:
    data: dict[str, str] = {}
    for line in (ROOT / "keystore.properties").read_text(encoding="utf-8").splitlines():
        if "=" not in line or line.strip().startswith("#"):
            continue
        key, value = line.split("=", 1)
        data[key.strip()] = value.strip()
    return data


def main() -> int:
    cfg = props()
    env = os.environ.copy()
    env["KS_PASS"] = cfg["storePassword"]
    env["KEY_PASS"] = cfg["keyPassword"]
    ks = ROOT / cfg["storeFile"]
    subprocess.check_call(
        [
            str(SIGNER),
            "sign",
            "--ks",
            str(ks),
            "--ks-key-alias",
            cfg["keyAlias"],
            "--ks-pass",
            "env:KS_PASS",
            "--key-pass",
            "env:KEY_PASS",
            "--min-sdk-version",
            "17",
            "--v1-signing-enabled",
            "true",
            "--v2-signing-enabled",
            "true",
            "--v3-signing-enabled",
            "true",
            "--v4-signing-enabled",
            "false",
            str(APK),
        ],
        env=env,
    )
    verify = subprocess.check_output(
        [str(SIGNER), "verify", "--verbose", "--print-certs", str(APK)],
        text=True,
        errors="replace",
    )
    print(verify)
    with zipfile.ZipFile(APK) as zf:
        names = zf.namelist()
        sf = next(n for n in names if n.startswith("META-INF/") and n.endswith(".SF"))
        head = zf.read(sf).decode("utf-8", "replace").splitlines()[:8]
        print("SF", sf)
        print("\n".join(head))
        abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") >= 2})
        print("abis", " ".join(abis))
        bad = [n for n in names if n.startswith("lib/armeabi/")]
        print("obsolete armeabi", len(bad))
    shutil.copyfile(APK, OUT)
    print("copied", OUT, OUT.stat().st_size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
