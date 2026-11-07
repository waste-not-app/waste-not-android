"""Exercise the release-build invocation and packaging steps in temporary fixtures."""

import os
import json
from pathlib import Path
import subprocess
import tempfile
import textwrap

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/android-release.yml").read_text()
build_section = workflow.split("      - name: Build Release APK\n", 1)[1].split("\n      - name:", 1)[0]
build_script = textwrap.dedent(build_section.split("        run: |\n", 1)[1])
for label, url, expected in [
    ("configured backend uses remote adapters", "https://api.example.invalid/v1/", True),
    ("missing backend prevents publishing", "", False),
]:
    with tempfile.TemporaryDirectory(prefix="wastenot-release-build-") as directory:
        fixture = Path(directory)
        invocation = fixture / "gradle-args.json"
        wrapper = fixture / "gradlew"
        wrapper.write_text("#!/usr/bin/env python3\nimport json, os, sys\nfrom pathlib import Path\n"
                           "Path(os.environ['GRADLE_ARGS_FILE']).write_text(json.dumps(sys.argv[1:]))\n")
        wrapper.chmod(0o755)
        result = subprocess.run(["bash", "-c", build_script], cwd=fixture,
                                env={"PATH": os.environ["PATH"], "WASTENOT_RELEASE_API_BASE_URL": url,
                                     "GRADLE_ARGS_FILE": str(invocation)}, text=True, capture_output=True, timeout=30)
        assert (result.returncode == 0) == expected, label
        assert invocation.exists() == expected, label
        if expected:
            args = json.loads(invocation.read_text())
            assert "assembleRelease" in args and "-Pwastenot.useDummyData=false" in args, label
            assert "-Pwastenot.hotReload=false" in args, label
            assert f"-Pwastenot.releaseApiBaseUrl={url}" in args, label
        print(f"PASS: release build {label}", flush=True)

section = workflow.split("      - name: Prepare APK Files\n", 1)[1].split("\n      - name:", 1)[0]
script = textwrap.dedent(section.split("        run: |\n", 1)[1])
cases = [
    ("explicit version", "1.2.3-beta.1", "branch", "main", "1.2.3-beta.1"),
    ("tag version", "", "tag", "v2.3.4", "2.3.4"),
    ("branch build number", "", "branch", "main", "1.0.0-b42"),
    ("reject path traversal", "../../outside", "branch", "main", None),
    ("reject shell input", "1.2.3; touch INJECTED", "branch", "main", None),
]

for label, override, ref_type, ref_name, expected in cases:
    with tempfile.TemporaryDirectory(prefix="wastenot-release-test-") as directory:
        fixture = Path(directory)
        apk_dir = fixture / "app/build/outputs/apk/release"
        apk_dir.mkdir(parents=True)
        (apk_dir / "fixture.apk").write_bytes(b"fixture-apk")
        (fixture / "app/build.gradle.kts").write_text('versionName = "1.0.0"\n')
        runner = fixture / "runner"
        runner.mkdir()
        output = fixture / "output"
        env = {"PATH": os.environ["PATH"], "RUNNER_TEMP": str(runner), "GITHUB_OUTPUT": str(output),
               "VERSION_OVERRIDE": override, "GITHUB_REF_TYPE": ref_type, "GITHUB_REF_NAME": ref_name,
               "GITHUB_RUN_NUMBER": "42"}
        result = subprocess.run(["bash", "-c", script], cwd=fixture, env=env,
                                text=True, capture_output=True, timeout=30)
        assert not (fixture / "INJECTED").exists(), label
        if expected is None:
            assert result.returncode != 0 and not (runner / "apk-release").exists(), label
        else:
            assert result.returncode == 0, label
            assert (runner / f"apk-release/wastenot-v{expected}.apk").read_bytes() == b"fixture-apk", label
            assert (runner / "apk-release/latest.apk").read_bytes() == b"fixture-apk", label
            assert f"version={expected}\n" in output.read_text(), label
        print(f"PASS: release packaging {label}", flush=True)
