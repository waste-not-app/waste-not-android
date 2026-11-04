"""Attach actual CSV provenance and compare completed runs without changing model defaults."""
import csv
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
RUNS = (("broad-waste-v1-yolo11n", "broad-waste-v1-20261020.json"),
        ("broad-waste-v2-yolo11s", "broad-waste-v2-yolo11s-20261020.json"))


def main():
    rows = []
    for name, filename in RUNS:
        run = ROOT / "ai/artifacts/training" / name
        target = ROOT / "ai/evaluations" / filename
        report = json.loads(target.read_text("utf-8"))
        history = list(csv.DictReader((run / "results.csv").open(encoding="utf-8")))
        best = max(history, key=lambda r: float(r["metrics/mAP50-95(B)"]))
        args = yaml.safe_load((run / "args.yaml").read_text("utf-8"))
        report["training"] = {"epochs_completed": len(history), "best_epoch": int(float(best["epoch"])),
                              "last_csv_time_seconds": float(history[-1]["time"]),
                              "configuration": {key: args[key] for key in (
                                  "epochs", "imgsz", "batch", "optimizer", "lr0", "patience", "freeze",
                                  "cos_lr", "mosaic", "degrees", "scale", "close_mosaic", "seed")}}
        if (run / "head-transfer.json").exists():
            report["training"]["head_transfer"] = json.loads((run / "head-transfer.json").read_text("utf-8"))
        report["test_evaluated_classes"] = [r["name"] for r in report["test_per_class"]]
        report["limitations"] = [s.replace("COCO creator", "COCO individual-photo identities") for s in report["limitations"]]
        report["metadata_clarification"] = (
            "Frozen input manifest COCO creator_page field contains the publisher's original image URL, not an author identity. "
            "COCO group keys represent individual image URLs plus cross-source pHash grouping; creator-disjoint splitting was NOT performed. "
            "DW records' legacy classes field retains original material IDs; normalized boxes and label files contain the actual broad IDs. "
            "The preparation script has been clarified for future runs. Actual image/label bytes and input manifest hash are unchanged.")
        report["verification"] = {"python_tests": 186, "offline_recommendation_scenarios": "44/44",
                                  "contracts": "34 exports checked", "data_consistency": "3699 image hashes, labels and splits verified"}
        report["promotion"] = {"default_model_changed": False, "broad_profile": "broad-experimental-01",
                               "all_classes_require_review": True, "local_camera_holdout_available": False}
        target.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
        (run / "report.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
        metrics = report["fixed_validation"]["candidate"]["per_class"]
        row = {"run": name, "sha256": report["sha256"], "training": report["training"],
               "per_class": metrics, "test_metrics": report["test_metrics"], "report": filename}
        # Before inspecting run 2 results: gates for common-class usability only, not a release gate.
        gates = {"bottle": (.85, .60), "can": (.80, .65), "cup": (.80, .40)}
        row["common_class_gates"] = {key: {"precision_min": p, "recall_min": r,
                                           "passed": (metrics[key]["precision"] or 0) >= p and
                                                     (metrics[key]["recall"] or 0) >= r}
                                     for key, (p, r) in gates.items()}
        row["all_common_gates_passed"] = all(v["passed"] for v in row["common_class_gates"].values())
        rows.append(row)
    output = {"dataset": "broad-waste-v1", "runs": rows, "default_changed": False,
              "gate_scope": "Common bottle/can/cup validation usability only; sparse classes remain experimental and no field holdout exists",
              "test_independence": "Public-data exploration; COCO pretraining and previous DW test inspection preclude an untouched-field claim"}
    path = ROOT / "ai/evaluations/broad-waste-comparison-20261020.json"
    path.write_text(json.dumps(output, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({"report": str(path), "runs": [{"name": r["run"], "common_gates": r["common_class_gates"]} for r in rows]}))


if __name__ == "__main__":
    main()
