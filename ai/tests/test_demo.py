import asyncio
import json
import re
import threading
from uuid import uuid4

from fastapi.testclient import TestClient
import httpx
import pytest

from wastenot_ai.catalog import load_catalog, review_detections
from wastenot_ai.contracts import RawDetection
from wastenot_ai.demo import create_demo_app
from wastenot_ai.providers import FixtureProvider
from wastenot_ai.recommendation import load_guides


class DemoDetector:
    labels = {0:"person",39:"bottle",41:"cup",43:"knife",73:"book"}
    artifact = {"model_version":"test-fixture"}
    def __init__(self): self.calls = 0
    def detect(self,image):
        self.calls += 1
        class_id = 43 if image == b"knife" else 39
        return {**review_detections([RawDetection(class_id=class_id,raw_class=self.labels[class_id],
                                                  confidence=.9,bbox=(.1,.1,.9,.9))],self.labels),
                "image":{"width":100,"height":100},"timing_ms":{"total":1}}


class FakeGemini(FixtureProvider):
    name = "gemini"
    api_key = "fixture-key-not-a-real-secret"
    model = "fixture-model"
    def __init__(self): self.calls = 0
    def generate(self,payload):
        self.calls += 1
        return super().generate(payload)


def make_client(demo_guides=True):
    detector,provider = DemoDetector(),FakeGemini()
    app = create_demo_app(detector,demo_guides=demo_guides,
                          providers={"gemini":provider,"fixture":FixtureProvider()})
    return app,detector,provider


def bootstrap(client):
    page = client.get("/")
    assert page.status_code == 200
    nonce = re.search(r'name="wastenot-demo" content="([^"]+)"',page.text).group(1)
    return {"X-WasteNot-Demo":nonce}


def detect(client,headers,request_id=None,image=b"photo"):
    return client.post("/demo/api/detect",content=image,headers={**headers,"Content-Type":"image/jpeg",
                       "X-Client-Request-Id":request_id or str(uuid4())})


def rec_payload(detection_id=None,provider="gemini"):
    return {"client_request_id":str(uuid4()),"manual":detection_id is None,"detection_id":detection_id,
            "object_id":"object-1" if detection_id else None,"confirmed_object":True,"provider":provider,
            "context":{"object_key":"pet_bottle","category":"PLASTIC",
                       "condition":{"cleanliness":"CLEAN","integrity":"INTACT","reusable":"UNKNOWN"},
                       "reuse_intention":False,"risk_flag":False}}


def test_demo_requires_local_host_origin_cookie_and_csrf_nonce():
    app,detector,provider = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        assert client.get("/demo/api/config").status_code == 403
        headers = bootstrap(client)
        config = client.get("/demo/api/config",headers=headers)
        assert config.status_code == 200 and config.json()["gemini_configured"]
        assert "fixture-key" not in config.text
        assert client.get("/",headers={"Host":"evil.example"}).status_code == 400
        assert client.get("/demo/api/config",headers={**headers,"Origin":"https://evil.example"}).status_code == 403
        assert client.get("/demo-assets/demo.js").status_code == 200
        assert client.get("/demo-assets/jakarta.ttf").status_code == 200
        assert client.get("/demo-assets/.env").status_code == 404
        assert config.headers["cache-control"] == "no-store"
        assert "camera=(self)" in config.headers["permissions-policy"]


def test_detection_replay_conflict_and_no_gemini_before_confirmation():
    app,detector,provider = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        rid = str(uuid4())
        first = detect(client,headers,rid)
        second = detect(client,headers,rid)
        assert first.status_code == 200 and first.json() == second.json() and detector.calls == 1
        assert detect(client,headers,rid,b"different").status_code == 409
        value = rec_payload(first.json()["data"]["detection_id"])
        value["confirmed_object"] = False
        assert client.post("/demo/api/recommend",json=value,headers=headers).status_code == 422
        assert provider.calls == 0


def test_recommendation_replay_calls_provider_once_and_invalidates_on_reset():
    app,detector,provider = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        detection_id = detect(client,headers).json()["data"]["detection_id"]
        value = rec_payload(detection_id)
        first = client.post("/demo/api/recommend",json=value,headers=headers)
        second = client.post("/demo/api/recommend",json=value,headers=headers)
        assert first.status_code == 200 and first.json() == second.json() and provider.calls == 1
        result = first.json()["data"]
        assert result["status"] == "READY" and result["saved_log"] is False
        assert result["prototype_only"] is True and result["guide_status"] == "DRAFT"
        assert result["source_urls"] and result["steps"]
        value["context"]["condition"]["cleanliness"] = "DIRTY"
        assert client.post("/demo/api/recommend",json=value,headers=headers).status_code == 409
        assert client.delete("/demo/api/session",headers=headers).status_code == 204
        value["client_request_id"] = str(uuid4())
        assert client.post("/demo/api/recommend",json=value,headers=headers).status_code == 404


def test_other_browser_cannot_read_or_recommend_another_sessions_detection():
    app,_,_ = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        detection_id = detect(client,headers).json()["data"]["detection_id"]
        client.cookies.clear()
        other = bootstrap(client)
        assert client.post("/demo/api/recommend",json=rec_payload(detection_id),headers=other).status_code == 404


def test_manual_path_and_draft_content_gate():
    app,_,provider = make_client(demo_guides=False)
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        result = client.post("/demo/api/recommend",json=rec_payload(),headers=headers).json()["data"]
        assert result["reason_code"] == "NO_REVIEWED_GUIDE"
        assert result["steps"] == [] and result["source_urls"] == [] and provider.calls == 0
        assert result["input_source"] == "manual_confirmation"


def test_detected_sharp_object_cannot_bypass_policy_with_category_correction():
    app,_,provider = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        detection_id = detect(client,headers,image=b"knife").json()["data"]["detection_id"]
        result = client.post("/demo/api/recommend",json=rec_payload(detection_id),headers=headers).json()["data"]
        assert result["status"] == "READY" and result["recommended_method"] == "REDUCE"
        assert result["recommendation_scope"] == "PREVENTION" and result["special_handling"] is True
        assert provider.calls == 1


def test_expanded_catalog_and_guides_cover_every_item_without_claiming_manual_detection():
    catalog = load_catalog()
    guides = load_guides()
    assert len(catalog["items"]) == 68 and len(guides) == 68
    assert len({x["baseline_class_id"] for x in catalog["items"] if x["baseline_class_id"] is not None}) == 58
    assert all(g.publication_status == "DRAFT" for g in guides)
    for key in ("battery","aluminium_can","cardboard"):
        assert next(x for x in catalog["items"] if x["object_key"] == key)["baseline_class_id"] is None
    for class_id,label in [(41,"cup"),(73,"book"),(67,"cell phone"),(43,"knife")]:
        result = review_detections([RawDetection(class_id=class_id,raw_class=label,confidence=.9,bbox=(.1,.1,.9,.9))],{class_id:label})
        assert result["status"] == "REVIEW_REQUIRED"
        assert result["objects"][0]["suggested_object_keys"]


def test_demo_limit_caps_provider_calls_and_invalid_media_is_rejected_early():
    app,_,provider = make_client()
    with TestClient(app,base_url="http://127.0.0.1") as client:
        headers = bootstrap(client)
        assert detect(client,headers,request_id="invalid").status_code == 422
        assert client.post("/demo/api/detect",headers={**headers,"X-Client-Request-Id":str(uuid4())},content=b"fake").status_code == 415
        for _ in range(30):
            assert client.post("/demo/api/recommend",json=rec_payload(),headers=headers).status_code == 200
        assert client.post("/demo/api/recommend",json=rec_payload(),headers=headers).status_code == 429
        assert provider.calls == 30
