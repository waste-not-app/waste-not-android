"""Check wheel resources without downloading models, images or API credentials."""
from pathlib import Path
import sys
from zipfile import ZipFile

directory = Path(sys.argv[1])
wheels = list(directory.glob("wastenot_ai-*.whl"))
if len(wheels) != 1:
    raise SystemExit("Expected one WasteNot AI wheel.")
expected = {
    "wastenot_ai/wire.py", "wastenot_ai/decisions.py", "wastenot_ai/http_api.py",
    "wastenot_ai/mock_api.py", "wastenot_ai/recommendation_worker.py", "wastenot_ai/openapi_export.py",
    "wastenot_ai/detection_quality.py", "wastenot_ai/guide_audit.py", "wastenot_ai/data/detection_profile.json",
    "wastenot_ai/taxonomy.py",
    "wastenot_ai/data/catalog.json", "wastenot_ai/data/guides.json",
    "wastenot_ai/data/guide_templates.json", "wastenot_ai/data/source_registry.json", "wastenot_ai/web/index.html",
    "wastenot_ai/web/demo.js", "wastenot_ai/web/demo.css",
    "wastenot_ai/web/jakarta.ttf", "wastenot_ai/web/OFL-PlusJakartaSans.txt",
}
with ZipFile(wheels[0]) as wheel:
    names = set(wheel.namelist())
    missing = expected - names
    forbidden = [name for name in names if any(part.startswith(".env") for part in Path(name).parts)
                 or name.endswith((".pt", ".jpg", ".png", ".pyc"))]
    if missing or forbidden:
        raise SystemExit(f"Invalid package: missing={sorted(missing)}, forbidden={forbidden}")
print("Wheel resources and exclusions verified.")
