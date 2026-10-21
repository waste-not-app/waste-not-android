import importlib.util
import json
from pathlib import Path

from PIL import Image
import pytest


def script(name):
    path = Path(__file__).resolve().parents[1] / "scripts" / f"{name}.py"
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_validation_matching_is_class_aware_one_to_one_and_counts_duplicates_as_false_positives():
    scorer = script("calibrate_yolo")
    bbox = [.1, .1, .8, .9]
    records = [{"truth": [{"class_id": 0, "bbox": bbox}, {"class_id": 2, "bbox": [.8, .8, .9, .9]}],
                "predictions": [{"class_id": cls, "confidence": score, "bbox": bbox}
                                for cls, score in [(0, .9), (0, .8), (1, .95)]]}]
    report = scorer.score(records, [.5]*4)
    assert {key: report["overall"][key] for key in ("tp", "fp", "fn")} == {"tp": 1, "fp": 2, "fn": 1}
    assert report["per_class"]["glass_bottle"]["fp"] == 1
    assert report["per_class"]["hdpe_bottle"]["fn"] == 1
    assert report["ambiguous_images"] == 1


def test_camera_holdout_rejects_empty_template_unreviewed_negatives_and_changed_image(tmp_path):
    evaluator = script("evaluate_detection_quality")
    manifest = tmp_path / "manifest.json"
    manifest.write_text(json.dumps({"usage": "EVALUATION_ONLY", "records": []}))
    with pytest.raises(ValueError, match="nonempty"):
        list(evaluator.reviewed_inputs(manifest))
    image = tmp_path / "empty.png"
    Image.new("RGB", (32, 32), "white").save(image)
    row = {"id": "empty-1", "image": image.name, "sha256": "0"*64,
           "capture_session_id": "new-session", "physical_object_ids": [], "kind": "NEGATIVE", "truth": []}
    manifest.write_text(json.dumps({"usage": "EVALUATION_ONLY", "records": [row]}))
    with pytest.raises(ValueError, match="review/session"):
        list(evaluator.reviewed_inputs(manifest))
    row.update(reviewer="Unit-test fixture, not a real dataset", reviewed_at="2026-10-20")
    manifest.write_text(json.dumps({"usage": "EVALUATION_ONLY", "records": [row]}))
    with pytest.raises(ValueError, match="checksum"):
        list(evaluator.reviewed_inputs(manifest))


def test_negative_crops_cannot_intersect_annotation_or_its_safety_margin():
    prepare = script("prepare_background_training")
    assert list(prepare.safe_crop_regions((640, 480), [(0, 0, 1, 1)])) == []
    boxes = [(.35, .25, .65, .75)]
    regions = list(prepare.safe_crop_regions((960, 960), boxes))
    assert regions
    for x1, y1, x2, y2 in regions:
        assert x2 <= (.35-.04)*960 or x1 >= (.65+.04)*960 or y2 <= (.25-.04)*960 or y1 >= (.75+.04)*960
