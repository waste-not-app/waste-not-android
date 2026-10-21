"""Compare fixed-confidence errors on unchanged val, not on test or mined training backgrounds."""
import argparse
import hashlib
import json
import os
from pathlib import Path

from calibrate_yolo import LABELS, score

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".cache-ai/ultralytics"))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache-ai/matplotlib"))
os.environ["YOLO_AUTOINSTALL"] = "false"


def confusion(records, confidence, output):
    import torch
    from ultralytics.utils.metrics import ConfusionMatrix
    matrix = ConfusionMatrix(names=dict(enumerate(LABELS)))
    for row in records:
        matrix.process_batch({"cls": torch.tensor([p["class_id"] for p in row["predictions"]]),
                              "conf": torch.tensor([p["confidence"] for p in row["predictions"]]),
                              "bboxes": torch.tensor([p["bbox"] for p in row["predictions"]]).reshape(-1, 4)},
                             {"cls": torch.tensor([p["class_id"] for p in row["truth"]]),
                              "bboxes": torch.tensor([p["bbox"] for p in row["truth"]]).reshape(-1, 4)},
                             conf=confidence, iou_thres=.45)
    output.mkdir(parents=True, exist_ok=True)
    matrix.plot(normalize=False, save_dir=output)
    return {"rows": [*LABELS, "background"], "columns": [*LABELS, "background"],
            "orientation": "rows=predicted, columns=true; background column=unmatched predictions, row=missed objects",
            "confidence": confidence, "match_iou": .45, "matrix": matrix.matrix.astype(int).tolist()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-cache", type=Path, required=True)
    parser.add_argument("--baseline-cache", type=Path, default=ROOT / "ai/artifacts/calibration/validation-predictions.json")
    parser.add_argument("--output", type=Path, default=ROOT / "ai/artifacts/background-comparison")
    args = parser.parse_args()
    old = json.loads(args.baseline_cache.read_text("utf-8"))
    new = json.loads(args.candidate_cache.read_text("utf-8"))
    for key in ("manifest_sha256", "split", "prediction"):
        if old["identity"][key] != new["identity"][key]:
            raise ValueError("Comparison protocol mismatch: " + key)
    if new["identity"]["split"] != "val" or [r["image"] for r in old["records"]] != [r["image"] for r in new["records"]]:
        raise ValueError("Only identical validation images may be compared")
    if any(a["truth"] != b["truth"] for a, b in zip(old["records"], new["records"])):
        raise ValueError("Ground truth changed")
    reports = {}
    for name, value in (("baseline", old), ("candidate", new)):
        reports[name] = {"identity": value["identity"],
                         "fixed_confidence_050": score(value["records"], [.5]*4),
                         "fixed_confidence_070": score(value["records"], [.7]*4),
                         "confusion_025": confusion(value["records"], .25, args.output / name / "conf025"),
                         "confusion_050": confusion(value["records"], .5, args.output / name / "conf050")}
    before = reports["baseline"]["fixed_confidence_050"]["overall"]
    after = reports["candidate"]["fixed_confidence_050"]["overall"]
    # Declared before measuring candidate: >=25% fewer FP, at most 3pp recall loss.
    gate = {"fp_reduction_at_least_25_percent": after["fp"] <= before["fp"]*.75,
            "recall_loss_at_most_3_percentage_points": after["recall"] >= before["recall"]-.03}
    report = {"kind": "validation_comparison_not_independent_field_accuracy", "images": len(new["records"]),
              "models": reports, "promotion_gate": gate, "fixed_threshold_gate_passed": all(gate.values()),
              "test_used": False, "limitations": ["Both model and review thresholds were selected using this validation split",
                 "Negative crops used for training cannot serve as evidence of held-out negative/OOD performance",
                 "Training schedule also changed; this comparison is not a controlled negative-data-only ablation"]}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "comparison.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"before": before, "after": after, "gate": gate}), flush=True)


if __name__ == "__main__":
    main()
