import json

from wastenot_ai.catalog import detection_capabilities, load_catalog, review_detections
from wastenot_ai.contracts import RawDetection
from wastenot_ai.decisions import Decisions
from wastenot_ai.providers import FixtureProvider
from wastenot_ai.recommendation import load_guides
from wastenot_ai.taxonomy import BROAD_LABELS, BROAD_OBJECT_KEYS
from wastenot_ai.wire import DecisionInput


LABELS = dict(enumerate(BROAD_LABELS))


def test_whole_broad_taxonomy_maps_objects_without_claiming_material_or_calibration():
    capabilities = detection_capabilities(LABELS)
    assert capabilities["supported_visual_classes"] == 10
    assert set(capabilities["review_only_classes"]) == set(BROAD_LABELS)
    assert capabilities["review_profile"] == "broad-experimental-01"
    objects = [RawDetection(class_id=i, raw_class=name, confidence=.99,
                            bbox=(.01+i*.09, .1, .08+i*.09, .8)) for i, name in LABELS.items()]
    result = review_detections(objects, LABELS)
    assert all(row["confidence_band"] == "UNCERTAIN" and row["object_key"] is None for row in result["objects"])
    for row in result["objects"]:
        assert set(row["suggested_object_keys"]) == set(BROAD_OBJECT_KEYS[row["raw_class"]])
    assert detection_capabilities({0: "bottle", 1: "can"})["supported_visual_classes"] == 0


def test_every_broad_selection_has_a_unique_catalog_entry_and_draft_guide():
    items = {r["object_key"] for r in load_catalog()["items"]}
    guides = {g.object_key: g for g in load_guides()}
    for keys in BROAD_OBJECT_KEYS.values():
        for key in keys:
            assert key in items and guides[key].publication_status == "DRAFT"


def test_broad_cup_selection_replays_through_decision_contract_with_unknown_conditions():
    selection = {"model_version": "candidate-" + "a"*12 + "-square640", "model_sha256": "a"*64,
                 "class_names": list(BROAD_LABELS), "mapping_version": "broad-waste-objects-01+broad-experimental-01",
                 "object": {"class_id": 2, "raw_class": "cup", "confidence": .9, "bbox": [.1,.1,.8,.9]}}
    request = DecisionInput.model_validate_json(json.dumps({"context": {"object_key": "ceramic_cup", "category": "OTHER"},
                                                           "selection": selection}))
    result = Decisions(FixtureProvider(), load_guides(), demo_guides=True).evaluate(request)
    assert result.recommended_method == "REUSE" and result.recommendation_basis == "CONDITIONAL"
