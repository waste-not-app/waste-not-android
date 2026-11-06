"""Check native APK minimum SDK and bundled font/license distribution."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--expected", type=int, default=30)
args = parser.parse_args()
sdk = Path(os.environ["ANDROID_HOME"])
aapt = max((sdk / "build-tools").glob("*/aapt2"), key=lambda p: tuple(map(int, re.findall(r"\d+", p.parent.name))))
badging = subprocess.check_output([str(aapt), "dump", "badging", str(args.apk)], text=True)
minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'$", badging, re.MULTILINE)
assert minimum and int(minimum[1]) == args.expected, f"APK must support API {args.expected}: {minimum}"
manifest = subprocess.check_output([str(aapt), "dump", "xmltree", "--file", "AndroidManifest.xml", str(args.apk)], text=True)
activities = re.findall(r"(?m)^([ ]*)E: activity\b[^\n]*\n((?:\1  A:[^\n]*\n)*)", manifest)
main = next((attributes for _, attributes in activities if '="com.example.wastenot.MainActivity"' in attributes), "")
assert re.search(r"android:windowSoftInputMode[^\n]*=0x0*10\b", main), "MainActivity must declare adjustResize; an application attribute is ignored"
with zipfile.ZipFile(args.apk) as apk:
    assert not any(name.startswith("assets/hotswan-v2/") for name in apk.namelist()), "Native APK must not contain hot-reload interpreter assets"
    for weight in ["regular", "medium", "semibold", "bold", "extrabold"]:
        assert len(apk.read(f"res/font/plus_jakarta_sans_{weight}.ttf")) > 0, weight
    license_text = apk.read("assets/licenses/OFL-PlusJakartaSans.txt").decode()
    assert "SIL OPEN FONT LICENSE Version 1.1" in license_text
print(f"PASS: {args.apk.name} supports API {args.expected}, MainActivity adjustResize, five font weights and OFL license packaged")
