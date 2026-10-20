"""Real model + private HTTP adapter smoke; no Gemini call and no waste-log write."""
import json
from pathlib import Path

from fastapi.testclient import TestClient

from wastenot_ai.cli import run_pipeline
from wastenot_ai.contracts import Context
from wastenot_ai.detector import YoloDetector
from wastenot_ai.providers import FixtureProvider
from wastenot_ai.recommendation import load_guides
from wastenot_ai.worker import create_app

root = Path(__file__).resolve().parents[1]
detector = YoloDetector(root / "artifacts/models/yolo11n.pt")
image = (root / "data/local/smoke/bottle.jpg").read_bytes()
token = "runtime-smoke-fixture-token-not-a-production-secret"
with TestClient(create_app(detector, token)) as client:
    assert client.get("/health/ready").status_code == 401
    response = client.post("/internal/detect",content=image,
                           headers={"Authorization":f"Bearer {token}","Content-Type":"image/jpeg"})
    assert response.status_code == 200
    detection = response.json()["data"]
selected = next(x for x in detection["objects"] if x["supported_for_review"] and x["raw_class"] == "bottle")
context = Context.model_validate_json((root / "examples/context-glass.json").read_text("utf-8"))
result = run_pipeline(detector,image,context,selected["object_id"],FixtureProvider(),load_guides(),demo_guides=True)
assert result["recommendation"]["status"] == "READY" and result["saved_log"] is False
report = {"real_yolo":True,"private_http_adapter":True,"model_sha256":detector.artifact["sha256"],
          "selected_object":selected,"recommendation":result["recommendation"],
          "live_gemini":False,"saved_log":False}
(root / "artifacts/runtime-smoke.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
print(json.dumps({"real_yolo":True,"private_http_adapter":True,"fixture_recommendation":"READY",
                  "live_gemini":False,"saved_log":False}))
