"""Inject filtered Vp530Demo classes.dex into a packaged APK and re-sign.

Preserves each zip entry's compression method so native .so libs stay STORED.
"""
from __future__ import annotations

import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: inject_palm_dex.py <apk> <palm.dex> <build-tools-dir>", file=sys.stderr)
        return 2
    apk = Path(sys.argv[1])
    palm_dex = Path(sys.argv[2])
    build_tools = Path(sys.argv[3])
    if not apk.is_file() or not palm_dex.is_file():
        print("apk or dex missing", file=sys.stderr)
        return 1

    tmp = apk.with_suffix(".inject.apk")
    if tmp.exists():
        tmp.unlink()

    with zipfile.ZipFile(apk, "r") as zin, zipfile.ZipFile(tmp, "w") as zout:
        names = set(zin.namelist())
        next_idx = 2
        while f"classes{next_idx}.dex" in names:
            next_idx += 1
        dest_dex = f"classes{next_idx}.dex"

        for info in zin.infolist():
            # Drop old signatures; apksigner will rewrite them.
            if info.filename.startswith("META-INF/"):
                continue
            data = zin.read(info.filename)
            out = zipfile.ZipInfo(filename=info.filename)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.date_time = info.date_time
            zout.writestr(out, data)

        dex_info = zipfile.ZipInfo(filename=dest_dex)
        dex_info.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(dex_info, palm_dex.read_bytes())

    zipalign = build_tools / ("zipalign.exe" if sys.platform.startswith("win") else "zipalign")
    apksigner = build_tools / ("apksigner.bat" if sys.platform.startswith("win") else "apksigner")
    aligned = apk.with_suffix(".aligned.apk")
    if aligned.exists():
        aligned.unlink()
    subprocess.check_call([str(zipalign), "-f", "4", str(tmp), str(aligned)])
    tmp.unlink(missing_ok=True)

    debug_ks = Path.home() / ".android" / "debug.keystore"
    subprocess.check_call(
        [
            str(apksigner),
            "sign",
            "--ks",
            str(debug_ks),
            "--ks-pass",
            "pass:android",
            "--key-pass",
            "pass:android",
            "--ks-key-alias",
            "androiddebugkey",
            "--out",
            str(apk),
            str(aligned),
        ]
    )
    aligned.unlink(missing_ok=True)
    print(f"injected {dest_dex} into {apk.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
