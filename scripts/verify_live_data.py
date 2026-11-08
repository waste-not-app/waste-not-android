"""Reject local sample adapters in application sources and packaged APKs."""

from pathlib import Path
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
main = ROOT / "app/src/main"
for folder in ["data/demo", "data/dummy"]:
    assert not (main / "java/com/example/wastenot" / folder).exists(), folder
for path in (main / "java").rglob("*.kt"):
    source = path.read_text()
    assert not re.search(r"\b(USE_DUMMY_DATA|isDemo|demo|dummy)\b", source, re.I), path
for path in main.glob("res/values*/*.xml"):
    for item in ET.parse(path).getroot():
        if item.tag not in {"string", "plurals", "string-array"}:
            continue
        assert not re.search(r"\b(demo|dummy)\b", "".join(item.itertext()), re.I), (path, item.attrib)
assert '"USE_DUMMY_DATA"' not in (ROOT / "app/build.gradle.kts").read_text()
print("PASS: application has no sample-data mode or copy")

for arg in sys.argv[1:]:
    apk = Path(arg)
    with zipfile.ZipFile(apk) as archive:
        dex = b"".join(archive.read(name) for name in archive.namelist() if name.endswith(".dex"))
    for package in [b"Lcom/example/wastenot/data/demo/", b"Lcom/example/wastenot/data/dummy/"]:
        assert package not in dex, (apk, package)
    assert b"USE_DUMMY_DATA" not in dex, apk
    for adapter in [b"RemoteAccountSession;", b"RemoteActivityStore;", b"RemoteScanRepository;"]:
        assert adapter in dex, (apk, adapter)
    print(f"PASS: {apk.name} contains remote adapters without local sample adapters")
