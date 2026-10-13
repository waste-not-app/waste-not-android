"""Exercise Gradle configuration with valid and hostile URL inputs."""

import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
checks = [
    ("debug localhost", "wastenot.debugApiBaseUrl=http://localhost:8080/v1", True, ""),
    ("staging HTTPS", "wastenot.stagingApiBaseUrl=https://api.example.invalid/v1/", True, ""),
    ("release HTTPS", "wastenot.releaseApiBaseUrl=https://api.example.invalid/v1/", True, ""),
    ("release rejects emulator HTTP", "wastenot.releaseApiBaseUrl=http://10.0.2.2:8080/v1/", False, "must use HTTPS"),
    ("debug rejects public HTTP", "wastenot.debugApiBaseUrl=http://api.example.invalid/v1/", False, "must use HTTPS"),
    ("staging rejects HTTP", "wastenot.stagingApiBaseUrl=http://localhost:8080/v1/", False, "must use HTTPS"),
    ("reject URL credentials", "wastenot.releaseApiBaseUrl=https://fixture:fixture@api.example.invalid/v1/", False, "must not contain credentials"),
    ("reject query credentials", "wastenot.releaseApiBaseUrl=https://api.example.invalid/v1/?token=fixture", False, "must not contain credentials"),
    ("reject malformed port", "wastenot.debugApiBaseUrl=http://localhost:99999/v1/", False, "valid host and port"),
]

environment = os.environ.copy()
for name in ["ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"]:
    environment.pop(name, None)

for label, value, succeeds, diagnostic in checks:
    result = subprocess.run(
        [str(ROOT / "gradlew"), "help", "--console=plain", f"-P{value}"],
        cwd=ROOT, env=environment, text=True, capture_output=True, timeout=180,
    )
    output = result.stdout + result.stderr
    assert (result.returncode == 0) == succeeds, f"{label}: unexpected Gradle result"
    assert not diagnostic or diagnostic in output, f"{label}: expected validation diagnostic missing"
    print(f"PASS: {label}", flush=True)

partial_environment = environment | {"ANDROID_KEY_ALIAS": "fixture"}
partial = subprocess.run(
    [str(ROOT / "gradlew"), "help", "--console=plain"], cwd=ROOT,
    env=partial_environment, text=True, capture_output=True, timeout=180,
)
assert partial.returncode != 0 and "Release signing requires all four" in partial.stdout + partial.stderr
print("PASS: partial signing configuration is rejected", flush=True)

sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk and (ROOT / "local.properties").exists():
    sdk = next((line.removeprefix("sdk.dir=").strip() for line in
                (ROOT / "local.properties").read_text().splitlines() if line.startswith("sdk.dir=")), None)
assert sdk, "Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir before verifying APKs"
aapt2 = max((Path(sdk) / "build-tools").glob("*/aapt2"),
            key=lambda path: tuple(map(int, re.findall(r"\d+", path.parent.name))))

for variant in ["debug", "staging", "release"]:
    apk = next((ROOT / f"app/build/outputs/apk/{variant}").glob("*.apk"), None)
    assert apk, f"Build the {variant} APK before verifying environments"
    resources = subprocess.check_output([str(aapt2), "dump", "resources", str(apk)], text=True)
    resource = re.search(r"xml/network_security_config\n\s+\(\) \(file\) (\S+) type=XML", resources)
    assert resource, f"{variant}: network security resource missing"
    policy = subprocess.check_output(
        [str(aapt2), "dump", "xmltree", "--file", resource[1], str(apk)], text=True)
    manifest = subprocess.check_output(
        [str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], text=True)
    assert "cleartextTrafficPermitted=false" in policy, f"{variant}: default cleartext policy missing"
    assert ("cleartextTrafficPermitted=true" in policy) == (variant == "debug"), variant
    if variant == "debug":
        assert all(host in policy for host in ["10.0.2.2", "localhost", "127.0.0.1"])
    else:
        with zipfile.ZipFile(apk) as archive:
            for name in archive.namelist():
                if name.endswith(".dex"):
                    assert b"http://10.0.2.2:8080/v1/" not in archive.read(name), variant
    assert re.search(r"android:allowBackup[^\n]*=false", manifest), variant
    assert "android:dataExtractionRules" in manifest, variant
    if variant == "staging":
        assert "com.example.wastenot.staging" in manifest
    print(f"PASS: {variant} packaged network/backup policy and API isolation", flush=True)
