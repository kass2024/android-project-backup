#!/usr/bin/env python3
"""Inject FLAG_MUTABLE UsbHidManager$1 into the DK USB AAR."""
from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(
    r"C:\Users\user\AndroidStudioProjects\Staff Attendance USB"
    r"\Face recognition algorithm SDK V2.1.0\demo"
    r"\MIPS_AI_Basic_BinocularIDCheckOffline_V2.1.0\app"
)
AAR = ROOT / "libs" / "dkcloudid-usb-android-module-v2.1.0.aar"
SRC = ROOT / "dk-patch" / "UsbHidManager$1.java"
ANDROID_JAR = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platforms" / "android-33" / "android.jar"


def main() -> int:
    if not AAR.exists():
        print("missing aar", AAR)
        return 1
    if not SRC.exists():
        print("missing src", SRC)
        return 1
    if not ANDROID_JAR.exists():
        print("missing android.jar", ANDROID_JAR)
        return 1
    work = Path(tempfile.mkdtemp(prefix="dkusb-"))
    try:
        subprocess.check_call(["jar", "xf", str(AAR)], cwd=str(work))
        classes = work / "classes.jar"
        pkg = work / "src" / "com" / "dk" / "usbNfc" / "UsbHidManager"
        pkg.mkdir(parents=True, exist_ok=True)
        shutil.copy2(SRC, pkg / "UsbHidManager$1.java")
        outc = work / "out"
        outc.mkdir()
        cp = str(classes) + os.pathsep + str(ANDROID_JAR)
        subprocess.check_call(
            [
                "javac",
                "-encoding",
                "UTF-8",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                cp,
                "-d",
                str(outc),
                str(pkg / "UsbHidManager$1.java"),
            ]
        )
        patched = list(outc.rglob("UsbHidManager$1.class"))
        if not patched:
            print("javac produced no class")
            return 1
        rel = "com/dk/usbNfc/UsbHidManager/UsbHidManager$1.class"
        subprocess.check_call(["jar", "uf", str(classes), "-C", str(outc), rel.replace("\\", "/")])
        aar_tmp = work / "patched.aar"
        # rebuild aar from extracted files
        names = [p.name for p in work.iterdir() if p.name not in ("src", "out", "patched.aar")]
        subprocess.check_call(["jar", "cf", str(aar_tmp)] + names, cwd=str(work))
        shutil.copy2(aar_tmp, AAR)
        print("patched", AAR)
        return 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
