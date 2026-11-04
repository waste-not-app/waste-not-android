import asyncio
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from io import BytesIO
import json
from pathlib import Path
import threading
from uuid import UUID, uuid4

from fastapi.testclient import TestClient
import httpx
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from openapi_spec_validator import validate
from PIL import Image
import pytest

from wastenot_ai.catalog import load_catalog
from wastenot_ai.http_api import ERRORS
from wastenot_ai.mock_api import CLASS_NAMES, MockDetector, create_mock_app
from wastenot_ai.openapi_export import contract_artifacts, example_request, export_contracts
from wastenot_ai.providers import FixtureProvider
from wastenot_ai.recommendation_worker import create_recommendation_app
from wastenot_ai.wire import DecisionInput
from wastenot_ai.worker import create_app

ROOT = Path(__file__).resolve().parents[1]
TOKEN = "test-service-token-at-least-24-characters"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
USER_A = {"Authorization": "Bearer mock-user-a"}
USER_B = {"Authorization": "Bearer mock-user-b"}


def png(color="white"):
    buffer = BytesIO()
    Image.new("RGB", (128, 128), color).save(buffer, format="PNG")
    return buffer.getvalue()


def detect(client, *, owner=USER_A, identifier=None, data=None):
    return client.post("/v1/ai/detections", headers=owner,
                       data={"client_request_id": str(identifier or uuid4())},
                       files={"image": ("sample.png", data or png(), "image/png")})


def validator(doc, schema):
    return Draft202012Validator({**schema, "components": doc["components"]}, format_checker=FormatChecker())


@pytest.fixture(scope="module")
def artifacts():
    return contract_artifacts()


def test_openapi_valid_and_export_has_no_drift(artifacts):
    for name, value in artifacts.items():
        if name.endswith(".openapi.json"):
            validate(value)
    export_contracts(ROOT / "contracts/v1", check=True)


def test_all_generated_examples_match_shared_schemas_and_expected_outcomes(artifacts):
    public = artifacts["public.openapi.json"]
    operations = public["paths"]
    req = operations["/v1/ai/recommendations"]["post"]["requestBody"]["content"]["application/json"]["schema"]
    rec = operations["/v1/ai/recommendations"]["post"]["responses"]["201"]["content"]["application/json"]["schema"]
    det = operations["/v1/ai/detections"]["post"]["responses"]["201"]["content"]["application/json"]["schema"]
    error = {"$ref": "#/components/schemas/ErrorEnvelope"}
    expected = {"recycle": "RECYCLE", "reuse": "REUSE", "reduce": "REDUCE", "trash": "TRASH", "hdpe-recycle": "RECYCLE"}
    for name, value in artifacts.items():
        if not name.startswith("examples/"):
            continue
        if name.endswith("-request.json") and "internal-" not in name:
            validator(public, req).validate(value)
        elif name.endswith("-response.json") and "internal-" not in name:
            if "error" in value:
                validator(public, error).validate(value)
            elif "recommendation_id" in value["data"]:
                validator(public, rec).validate(value)
            elif "detection_id" in value["data"]:
                validator(public, det).validate(value)
    for name, method in expected.items():
        result = artifacts[f"examples/{name}-response.json"]["data"]
        assert result["status"] == "READY" and result["recommended_method"] == method
        assert result["provider"] == "fixture" and result["prototype_only"] is True
    assert artifacts["examples/needs-context-response.json"]["data"]["status"] == "NEEDS_CONTEXT"
    assert artifacts["examples/hdpe-needs-context-response.json"]["data"]["missing_context"] == ["condition.former_contents"]
    assert artifacts["examples/receiver-needs-context-response.json"]["data"]["missing_context"] == ["recycling_access"]
    conditional = artifacts["examples/conditional-cup-response.json"]["data"]
    assert conditional["recommendation_basis"] == "CONDITIONAL" and conditional["recommended_method"] == "REUSE"
    assert conditional["conditions_to_verify"] and conditional["missing_context"] == []
    assert artifacts["examples/provider-unavailable-response.json"]["data"]["reason_code"] == "LLM_UNAVAILABLE"
    private = artifacts["private-recommendation.openapi.json"]
    op = private["paths"]["/internal/v1/recommendations"]["post"]
    validator(private, op["requestBody"]["content"]["application/json"]["schema"]).validate(artifacts["examples/internal-recommendation-request.json"])
    validator(private, op["responses"]["200"]["content"]["application/json"]["schema"]).validate(artifacts["examples/internal-recommendation-response.json"])


def test_schema_rejects_invalid_method_status_scope_and_manual_ids(artifacts):
    doc = artifacts["public.openapi.json"]
    op = doc["paths"]["/v1/ai/recommendations"]["post"]
    response_validator = validator(doc, op["responses"]["201"]["content"]["application/json"]["schema"])
    for update in ({"recommended_method": "COMPOST"}, {"recommended_method": None},
                   {"recommendation_scope": "PREVENTION"}, {"status": "UNAVAILABLE"},
                   {"status": "NEEDS_CONTEXT", "recommended_method": None, "recommendation_scope": None}):
        value = deepcopy(artifacts["examples/recycle-response.json"])
        value["data"].update(update)
        with pytest.raises(ValidationError):
            response_validator.validate(value)
    conditional = deepcopy(artifacts["examples/conditional-cup-response.json"])
    conditional["data"]["conditions_to_verify"] = []
    with pytest.raises(ValidationError):
        response_validator.validate(conditional)
    value = example_request(detection_id=str(uuid4()), object_id="object-1")
    with pytest.raises(ValidationError):
        validator(doc, op["requestBody"]["content"]["application/json"]["schema"]).validate(value)


def test_public_optional_context_can_be_entirely_omitted_and_strict_mode_is_forwarded():
    value = {"client_request_id": str(uuid4()), "input_source": "MANUAL", "confirmed_object": True,
             "confirmed_object_key": "ceramic_cup", "confirmed_category": "OTHER"}
    with TestClient(create_mock_app()) as client:
        response = client.post("/v1/ai/recommendations", headers=USER_A, json=value)
        assert response.status_code == 201
        result = response.json()["data"]
        assert result["recommended_method"] == "REUSE" and result["recommendation_basis"] == "CONDITIONAL"
        assert result["conditions_to_verify"] and result["missing_context"] == []
        value.update(client_request_id=str(uuid4()), allow_conditional=False)
        response = client.post("/v1/ai/recommendations", headers=USER_A, json=value)
        assert response.status_code == 200
        result = response.json()["data"]
        assert result["status"] == "NEEDS_CONTEXT" and result["recommendation_basis"] is None


def test_mock_session_selection_replay_conflict_ownership_delete_and_expiry():
    stamp = [datetime(2026, 10, 20, tzinfo=timezone.utc)]
    app = create_mock_app(clock=lambda: stamp[0], ttl_seconds=60)
    with TestClient(app) as client:
        identifier = uuid4()
        first = detect(client, identifier=identifier)
        assert first.status_code == 201 and first.headers["x-wastenot-mock"] == "true"
        assert first.headers["cache-control"] == "no-store"
        session = first.json()["data"]
        replay = detect(client, identifier=identifier)
        assert replay.status_code == 200 and replay.json()["data"] == session
        assert detect(client, identifier=identifier, data=png("red")).status_code == 409
        other = detect(client, owner=USER_B, identifier=identifier)
        assert other.status_code == 201 and other.json()["data"]["detection_id"] != session["detection_id"]
        request = example_request(input_source="DETECTION", detection_id=session["detection_id"], object_id="object-1")
        assert client.post("/v1/ai/recommendations", json=request, headers=USER_B).status_code == 404
        result = client.post("/v1/ai/recommendations", json=request, headers=USER_A)
        assert result.status_code == 201
        assert result.json()["data"]["expires_at"] == session["expires_at"]
        replay = client.post("/v1/ai/recommendations", json=request, headers=USER_A)
        assert replay.status_code == 200 and replay.json()["data"] == result.json()["data"]
        altered = {**request, "context_revision": 2}
        assert client.post("/v1/ai/recommendations", json=altered, headers=USER_A).status_code == 409
        path = "/v1/ai/detections/" + session["detection_id"]
        assert client.delete(path, headers=USER_B).status_code == 404
        assert client.delete(path, headers=USER_A).status_code == 204
        assert client.delete(path, headers=USER_A).status_code == 204
        assert client.post("/v1/ai/recommendations", json=request, headers=USER_A).status_code == 410
        assert detect(client, identifier=identifier).status_code == 410
        fresh = detect(client).json()["data"]
        stamp[0] += timedelta(seconds=61)
        expired = {**request, "client_request_id": str(uuid4()), "detection_id": fresh["detection_id"]}
        assert client.post("/v1/ai/recommendations", json=expired, headers=USER_A).status_code == 410


@pytest.mark.parametrize("field,value", [("user_id", "spoof"), ("allowed_methods", ["TRASH"]),
                                        ("provider", "gemini"), ("guide_key", "fake"), ("confidence", .99)])
def test_public_contract_rejects_injected_authoritative_fields(field, value):
    with TestClient(create_mock_app()) as client:
        response = client.post("/v1/ai/recommendations", headers=USER_A, json={**example_request(), field: value})
        assert response.status_code == 422
        assert response.json()["error"]["code"] == "VALIDATION_ERROR"


@pytest.mark.parametrize("scenario,status,code", [("rate-limited", 429, "RATE_LIMITED"), ("timeout", 504, "AI_TIMEOUT")])
def test_mock_transport_failure_envelope(scenario, status, code):
    with TestClient(create_mock_app(scenario=scenario)) as client:
        response = client.post("/v1/ai/recommendations", json=example_request(), headers=USER_A)
        assert response.status_code == status and response.json()["error"]["code"] == code
        assert response.json()["error"]["retryable"] is True
        if status == 429:
            assert response.headers["retry-after"] == "30"


def test_public_media_auth_limits_and_mock_never_retains_photos():
    app = create_mock_app()
    with TestClient(app) as client:
        assert client.get("/v1/ai/objects").status_code == 401
        assert detect(client, data=b"not a photo").status_code == 422
        assert detect(client, data=b"x" * (5 * 1024 * 1024 + 1)).status_code == 413
        response = client.post("/v1/ai/detections", headers={**USER_A, "content-type": "multipart/form-data"}, content=b"x" * (6 * 1024 * 1024))
        assert response.status_code == 413 and response.headers["x-wastenot-mock"] == "true"
        assert detect(client).status_code == 201
        assert all(set(row) == {"owner", "result", "expires", "deleted"} for row in app.state.sessions.values())


class CountingProvider:
    name = "fixture"

    def __init__(self):
        self.calls = []

    def generate(self, payload):
        self.calls.append(payload)
        return FixtureProvider().generate(payload)


def private_input():
    return {"context": {"object_key": "glass_bottle", "category": "GLASS",
                         "condition": {"cleanliness": "CLEAN", "integrity": "INTACT"},
                         "reuse_intention": False, "recycling_access": "YES"}}


def test_private_policy_gate_without_weights_and_one_owner_for_outputs(artifacts):
    provider = CountingProvider()
    with TestClient(create_recommendation_app(token=TOKEN, provider=provider)) as client:
        assert client.post("/internal/v1/recommendations", json=private_input()).status_code == 401
        result = client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH)
        assert result.status_code == 200 and result.json()["data"]["reason_code"] == "NO_REVIEWED_GUIDE"
        assert provider.calls == []
        assert client.get("/docs").status_code == 404
    with TestClient(create_recommendation_app(token=TOKEN, provider=provider, demo_guides=True)) as client:
        result = client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH)
        assert result.status_code == 200
        direct = result.json()["data"]
        assert direct["recommended_method"] == "RECYCLE"
        for key in ("reason", "evidence_ids", "source_urls", "recommendation_version"):
            assert direct[key] == artifacts["examples/recycle-response.json"]["data"][key]
        assert set(provider.calls[-1]) <= {"object_key", "category", "condition", "reuse_intention", "recommendation_goal",
            "recycling_access", "special_handling", "risk_type", "allowed_methods", "guide"}
        assert client.post("/internal/v1/recommendations", content=b"x" * 16385, headers=AUTH).status_code == 413


def test_private_rechecks_saved_detection_and_keeps_risk_on_object_correction():
    provider = CountingProvider()
    value = private_input()
    value["selection"] = {"model_version": "mock-model", "mapping_version": "wrong",
                          "class_names": CLASS_NAMES, "object": {"class_id": 1, "raw_class": "glass_bottle", "confidence": .9, "bbox": [.1, .1, .8, .8]}}
    with TestClient(create_recommendation_app(token=TOKEN, provider=provider, demo_guides=True)) as client:
        assert client.post("/internal/v1/recommendations", json=value, headers=AUTH).json()["error"]["code"] == "INVALID_AI_REFERENCE"
        value["selection"]["mapping_version"] = "drinking-waste-reviewed-02"
        value["selection"]["object"]["confidence"] = .49
        assert client.post("/internal/v1/recommendations", json=value, headers=AUTH).json()["error"]["code"] == "OBJECT_REQUIRES_MANUAL"
        value["context"]["object_key"], value["context"]["category"] = "pet_bottle", "PLASTIC"
        names = [f"unsupported-{i}" for i in range(44)]
        names[43] = "knife"
        value["selection"].update(class_names=names, mapping_version=load_catalog()["mapping_version"],
            object={"class_id": 43, "raw_class": "knife", "confidence": .9, "bbox": [.1, .1, .8, .8]})
        response = client.post("/internal/v1/recommendations", json=value, headers=AUTH)
        assert response.status_code == 200 and response.json()["data"]["special_handling"] is True
        assert response.json()["data"]["recommended_method"] is None
        assert provider.calls == []


def test_versioned_detector_response_schema_and_legacy_adapter():
    with TestClient(create_app(MockDetector(), TOKEN)) as client:
        response = client.post("/internal/v1/detect", content=png(), headers={**AUTH, "content-type": "image/png"})
        assert response.status_code == 200
        body = response.json()
        assert body["api_version"] == "1.0.0" and body["data"]["model"]["class_names"] == CLASS_NAMES
        spec = client.app.openapi()
        schema = spec["paths"]["/internal/v1/detect"]["post"]["responses"]["200"]["content"]["application/json"]["schema"]
        validator(spec, schema).validate(body)
        assert client.get("/internal/v1/capabilities", headers=AUTH).json()["data"]["supported_visual_classes"] == 4
        assert client.post("/internal/detect", content=png(), headers={**AUTH, "content-type": "image/png"}).status_code == 200


def test_recommendation_timeout_keeps_slot_until_native_provider_finishes():
    async def scenario():
        release, finished = threading.Event(), threading.Event()

        class BlockingProvider:
            name = "fixture"

            def generate(self, payload):
                try:
                    release.wait(2)
                    return FixtureProvider().generate(payload)
                finally:
                    finished.set()

        app = create_recommendation_app(token=TOKEN, provider=BlockingProvider(), demo_guides=True, timeout=.04)
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            try:
                response = await client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH)
                assert response.status_code == 504
                response = await client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH)
                assert response.status_code == 503 and response.headers["retry-after"] == "1"
            finally:
                release.set()
                await asyncio.to_thread(finished.wait, 1)
    asyncio.run(scenario())


def test_concurrent_mock_replay_does_not_duplicate_provider_work():
    async def scenario():
        provider = CountingProvider()
        app = create_mock_app()
        app.state.decisions.provider = provider
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            responses = await asyncio.gather(*[client.post("/v1/ai/recommendations", json=example_request(), headers=USER_A) for _ in range(2)])
        assert sorted(r.status_code for r in responses) == [200, 201]
        assert responses[0].json()["data"] == responses[1].json()["data"]
        assert len(provider.calls) == 1
    asyncio.run(scenario())


def test_unexpected_provider_failure_is_redacted_and_slot_is_released():
    class Broken:
        name = "fixture"

        def generate(self, payload):
            raise RuntimeError("SECRET_DO_NOT_FORWARD")

    app = create_recommendation_app(token=TOKEN, provider=Broken(), demo_guides=True)
    with TestClient(app) as client:
        response = client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH)
        assert response.status_code == 503 and "SECRET_DO_NOT_FORWARD" not in response.text
        assert response.json()["error"]["code"] == "AI_UNAVAILABLE"
        app.state.decisions.provider = FixtureProvider()
        assert client.post("/internal/v1/recommendations", json=private_input(), headers=AUTH).status_code == 200


def test_malformed_private_detection_is_not_forwarded():
    class BrokenDetector(MockDetector):
        def detect(self, data):
            result = super().detect(data)
            result["objects"][0]["class_id"] = 0
            return result

    with TestClient(create_app(BrokenDetector(), TOKEN)) as client:
        response = client.post("/internal/v1/detect", content=png(), headers={**AUTH, "content-type": "image/png"})
        assert response.status_code == 503
        assert response.json()["error"]["code"] == "INVALID_AI_RESPONSE"
