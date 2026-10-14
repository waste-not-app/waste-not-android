"""Check native APK minimum SDK and bundled font/license distribution."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("--expected", type=int, default=24)
args = parser.parse_args()
sdk = Path(os.environ["ANDROID_HOME"])
aapt = max((sdk / "build-tools").glob("*/aapt2"), key=lambda p: tuple(map(int, re.findall(r"\d+", p.parent.name))))
badging = subprocess.check_output([str(aapt), "dump", "badging", str(args.apk)], text=True)
minimum = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'$", badging, re.MULTILINE)
assert minimum and int(minimum[1]) == args.expected, f"APK must support API {args.expected}: {minimum}"
with zipfile.ZipFile(args.apk) as apk:
    assert not any(name.startswith("assets/hotswan-v2/") for name in apk.namelist()), "Native APK must not contain hot-reload interpreter assets"
    for weight in ["regular", "medium", "semibold", "bold", "extrabold"]:
        assert len(apk.read(f"res/font/plus_jakarta_sans_{weight}.ttf")) > 0, weight
    license_text = apk.read("assets/licenses/OFL-PlusJakartaSans.txt").decode()
    assert "SIL OPEN FONT LICENSE Version 1.1" in license_text
print(f"PASS: {args.apk.name} supports API {args.expected}, five font weights and OFL license packaged")
