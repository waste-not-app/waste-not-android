import importlib.util
from pathlib import Path

import pytest

from wastenot_ai.catalog import detection_capabilities, review_detections
from wastenot_ai.contracts import RawDetection

LABELS = {0: "aluminium_can", 1: "glass_bottle", 2: "hdpe_bottle", 3: "plastic_bottle"}


def test_catalog_capabilities_follow_active_checkpoint():
    capabilities = detection_capabilities(LABELS)
    assert capabilities["supported_visual_classes"] == 4
    assert capabilities["supported_object_labels"] == {
        "aluminium_can": "aluminium_can", "glass_bottle": "glass_bottle", "hdpe_bottle": "hdpe_bottle", "pet_bottle": "plastic_bottle"}
    assert detection_capabilities({39: "bottle"})["supported_object_labels"] == {
        "pet_bottle": "bottle", "glass_bottle": "bottle"}


@pytest.mark.parametrize("cls,key", [(0, "aluminium_can"), (1, "glass_bottle"), (2, "hdpe_bottle"), (3, "pet_bottle")])
def test_custom_taxonomy_routes_to_existing_guides_after_confirmation(cls, key):
    result = review_detections([RawDetection(class_id=cls, raw_class=LABELS[cls], confidence=.8,
                                            bbox=(.1, .2, .8, .9))], LABELS)
    obj = result["objects"][0]
    assert result["mapping_version"] == "drinking-waste-reviewed-02"
    assert obj["suggested_object_keys"] == [key]
    assert obj["requires_material_confirmation"] is True
    assert obj["object_key"] is None


def test_hdpe_does_not_use_pet_guide():
    result = review_detections([RawDetection(class_id=2, raw_class="hdpe_bottle", confidence=.99,
                                            bbox=(.1, .2, .8, .9))], LABELS)
    assert result["status"] == "REVIEW_REQUIRED"
    assert result["objects"][0]["suggested_object_keys"] == ["hdpe_bottle"]


def test_partial_or_reordered_taxonomy_cannot_activate_specialist_mapping():
    result = review_detections([RawDetection(class_id=0, raw_class="aluminium_can", confidence=.99,
                                            bbox=(.1, .2, .8, .9))], {0: "aluminium_can"})
    assert result["status"] == "MANUAL_REQUIRED"


@pytest.fixture
def prepare_script():
    path = Path(__file__).resolve().parents[1] / "scripts/prepare_drinking_waste.py"
    spec = importlib.util.spec_from_file_location("prepare_drinking_waste", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize("text", ["4 .5 .5 .2 .2", "0 nan .5 .2 .2", "0 .5 .5 0 .2",
                                 "0 .1 .5 .8 .2", "0 .5 .5 .2", ""])
def test_bad_source_annotations_are_excluded_instead_of_fabricated(prepare_script, text):
    with pytest.raises(ValueError):
        prepare_script.parse_boxes(text, 4)


def test_valid_multiclass_source_annotation(prepare_script):
    boxes = prepare_script.parse_boxes("0 .5 .5 .2 .3\n3 .7 .7 .1 .1", 4)
    assert [row[0] for row in boxes] == [0, 3]
