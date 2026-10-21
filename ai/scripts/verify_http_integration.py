"""Real YOLO -> versioned private HTTP -> Python decisions; no Go or log writes."""
import argparse
import json
from pathlib import Path

from dotenv import load_dotenv
from fastapi.testclient import TestClient
from jsonschema import Draft202012Validator

from wastenot_ai.cli import read_image
from wastenot_ai.detector import YoloDetector
from wastenot_ai.providers import FixtureProvider, GeminiProvider
from wastenot_ai.recommendation_worker import create_recommendation_app
from wastenot_ai.worker import create_app


def validate_response(name, path, value):
    spec = json.loads((ROOT / "contracts/v1" / name).read_text("utf-8"))
    schema = spec["paths"][path]["post"]["responses"]["200"]["content"]["application/json"]["schema"]
    Draft202012Validator({**schema, "components": spec["components"]}).validate(value)


ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=ROOT / "artifacts/models/yolo11n.pt")
    parser.add_argument("--image", type=Path, default=ROOT / "data/local/smoke/bottle.jpg")
    parser.add_argument("--context", type=Path, default=ROOT / "examples/context-glass.json")
    parser.add_argument("--live-gemini", action="store_true")
    parser.add_argument("--input-shape", choices=["adaptive", "square"], default="adaptive")
    parser.add_argument("--output", type=Path, help="Write a separate evidence file for another model/object")
    args = parser.parse_args()
    if args.live_gemini:
        load_dotenv(ROOT / ".env", override=False)
    token = "http-integration-fixture-token-not-a-production-secret"
    headers = {"Authorization": "Bearer " + token}
    detector = YoloDetector(args.model, input_shape=args.input_shape)
    with TestClient(create_app(detector, token)) as client:
        response = client.post("/internal/v1/detect", headers={**headers, "content-type": "image/jpeg"},
                               content=read_image(args.image))
        assert response.status_code == 200, response.status_code
        body = response.json()
        validate_response("private-detection.openapi.json", "/internal/v1/detect", body)
    detection = body["data"]
    context = json.loads(args.context.read_text("utf-8"))
    selected = next((row for row in detection["objects"] if row["supported_for_review"]
                     and context["object_key"] in row["suggested_object_keys"]), None)
    assert selected is not None, "No reviewable detection supports the requested object context"
    request = {"context": context, "selection": {
        "model_version": detection["model_version"], "mapping_version": detection["mapping_version"],
        "class_names": detection["model"]["class_names"],
        "model_sha256": detection["model"]["sha256"],
        "object": {key: selected[key] for key in ("class_id", "raw_class", "confidence", "bbox")}}}
    provider = GeminiProvider() if args.live_gemini else FixtureProvider()
    with TestClient(create_recommendation_app(token=token, provider=provider, demo_guides=True)) as client:
        response = client.post("/internal/v1/recommendations", json=request, headers=headers)
        assert response.status_code == 200, response.status_code
        value = response.json()
        validate_response("private-recommendation.openapi.json", "/internal/v1/recommendations", value)
        result = value["data"]
        assert result["status"] == "READY", result["reason_code"]
        assert result["provider"] == ("gemini" if args.live_gemini else "fixture")
    report = {"real_yolo": True, "transport": "private HTTP adapters via ASGI TestClient",
              "model_sha256": detection["model"]["sha256"], "mapping_version": detection["mapping_version"],
              "decision_request": request,
              "recommendation": result, "live_gemini": args.live_gemini,
              "photo_sent_to_gemini": False, "saved_log": False, "go_backend_implemented": False}
    (ROOT / "artifacts").mkdir(exist_ok=True)
    filename = "http-integration-gemini.json" if args.live_gemini else "http-integration-fixture.json"
    target = args.output or ROOT / "artifacts" / filename
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"real_yolo": True, "versioned_http_schema": "passed", "decision_status": result["status"],
                      "method": result["recommended_method"], "live_gemini": args.live_gemini, "saved_log": False}))


if __name__ == "__main__":
    main()
