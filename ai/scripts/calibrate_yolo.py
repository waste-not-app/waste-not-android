"""Select review thresholds on validation data only; never fit against test/OOD data."""
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".cache-ai/ultralytics"))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache-ai/matplotlib"))
os.environ["YOLO_AUTOINSTALL"] = "false"
LABELS = ["aluminium_can", "glass_bottle", "hdpe_bottle", "plastic_bottle"]


def iou(a, b):
    intersection = max(0, min(a[2], b[2])-max(a[0], b[0])) * max(0, min(a[3], b[3])-max(a[1], b[1]))
    union = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - intersection
    return intersection / union if union > 0 else 0.0


def score(records, thresholds, *, unambiguous=False):
    counts = [{"tp": 0, "fp": 0, "fn": 0} for _ in LABELS]
    ambiguous_images = 0
    for record in records:
        predictions = [p for p in record["predictions"] if p["confidence"] >= thresholds[p["class_id"]]]
        ambiguous = {i for i, a in enumerate(predictions) for j, b in enumerate(predictions)
                     if i != j and a["class_id"] != b["class_id"] and iou(a["bbox"], b["bbox"]) >= .65}
        ambiguous_images += bool(ambiguous)
        if unambiguous:
            predictions = [p for i, p in enumerate(predictions) if i not in ambiguous]
        unmatched = set(range(len(record["truth"])))
        for prediction in sorted(predictions, key=lambda row: -row["confidence"]):
            cls = prediction["class_id"]
            options = [j for j in unmatched if record["truth"][j]["class_id"] == cls]
            best = max(options, key=lambda j: iou(prediction["bbox"], record["truth"][j]["bbox"]), default=None)
            if best is not None and iou(prediction["bbox"], record["truth"][best]["bbox"]) >= .5:
                counts[cls]["tp"] += 1
                unmatched.remove(best)
            else:
                counts[cls]["fp"] += 1
        for j in unmatched:
            counts[record["truth"][j]["class_id"]]["fn"] += 1
    def metrics(count):
        tp, fp, fn = count["tp"], count["fp"], count["fn"]
        return {**count, "precision": tp/(tp+fp) if tp+fp else None,
                "recall": tp/(tp+fn) if tp+fn else None}
    return {"overall": metrics({key: sum(row[key] for row in counts) for key in ("tp", "fp", "fn")}),
            "per_class": {name: metrics(row) for name, row in zip(LABELS, counts)},
            "ambiguous_images": ambiguous_images}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", type=Path, default=ROOT / "ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt")
    parser.add_argument("--data", type=Path, default=ROOT / "ai/data/local/drinking-waste-v2")
    parser.add_argument("--output", type=Path, default=ROOT / "ai/artifacts/calibration")
    parser.add_argument("--device", default="0")
    parser.add_argument("--square", action="store_true", help="Force 640x640 padding instead of adaptive rectangular inference")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    checksum = hashlib.sha256(args.model.read_bytes()).hexdigest()
    manifest_hash = hashlib.sha256((args.data / "manifest.json").read_bytes()).hexdigest()
    cache = args.output / "validation-predictions.json"
    identity = {"model_sha256": checksum, "manifest_sha256": manifest_hash, "split": "val",
                "prediction": {"imgsz": 640, "conf": .25, "iou": .7, "max_det": 50, "agnostic_nms": False}}
    if args.square:
        identity["prediction"]["rect"] = False
    if cache.exists():
        stored = json.loads(cache.read_text("utf-8"))
        if stored["identity"] != identity:
            raise ValueError("Prediction cache identity changed; use another --output")
        records = stored["records"]
    else:
        from ultralytics import YOLO, settings
        settings.update({"sync": False})
        model = YOLO(str(args.model))
        if model.names != dict(enumerate(LABELS)):
            raise ValueError("Unregistered checkpoint taxonomy")
        records = []
        images = sorted((args.data / "images/val").glob("*.jpg"))
        if not images:
            raise ValueError("Validation images missing")
        for index, image in enumerate(images):
            truth = []
            for line in (args.data / "labels/val" / image.with_suffix(".txt").name).read_text().splitlines():
                cls, x, y, w, h = map(float, line.split())
                truth.append({"class_id": int(cls), "bbox": [x-w/2, y-h/2, x+w/2, y+h/2]})
            result = model.predict(str(image), device=args.device, verbose=False, save=False, **identity["prediction"])[0]
            predictions = [{"class_id": int(box.cls.item()), "confidence": float(box.conf.item()),
                            "bbox": box.xyxyn[0].tolist()} for box in result.boxes]
            records.append({"image": image.name, "truth": truth, "predictions": predictions})
            if (index+1) % 100 == 0:
                print(f"Validation inference: {index+1}/{len(images)}", flush=True)
        cache.write_text(json.dumps({"identity": identity, "records": records}), encoding="utf-8")
    # A fixed precision target and minimum support are declared before searching.
    # Thresholds are confidence cutoffs, not estimates of correctness probability.
    grid = [round(.5 + .025*i, 3) for i in range(19)] + [1.0]
    lower, offered, selection = [], [], {}
    for cls, name in enumerate(LABELS):
        trials = [(threshold, score(records, [threshold]*4)["per_class"][name]) for threshold in grid]
        valid = [(t, m) for t, m in trials if m["tp"] >= 20 and m["precision"] >= .85]
        chosen = min(valid, key=lambda pair: pair[0])[0] if valid else .5
        lower.append(chosen)
        selection[name] = {"lower": chosen, "lower_target_met": bool(valid)}
    for cls, name in enumerate(LABELS):
        # Competing hypotheses remain visible but never enter OFFERED, even at high confidence.
        trials = []
        for threshold in [t for t in grid if t >= .7]:
            limits = lower.copy()
            limits[cls] = max(threshold, lower[cls])
            # Ambiguity must be based on all review candidates, not just offered candidates.
            retained = []
            for row in records:
                review = [p for p in row["predictions"] if p["confidence"] >= lower[p["class_id"]]]
                ambiguous = {i for i, a in enumerate(review) for j, b in enumerate(review)
                             if i != j and a["class_id"] != b["class_id"] and iou(a["bbox"], b["bbox"]) >= .65}
                retained.append({**row, "predictions": [p for i, p in enumerate(review) if i not in ambiguous]})
            trials.append((limits[cls], score(retained, limits)["per_class"][name]))
        valid = [(t, m) for t, m in trials if m["tp"] >= 20 and m["precision"] >= .95]
        chosen = min(valid, key=lambda pair: pair[0])[0] if valid else 1.0
        offered.append(chosen)
        selection[name].update(offered=chosen, offered_enabled=bool(valid), offered_target_met=bool(valid))
    # Evaluate exactly the deployed offered decision: first review cutoff, then ambiguity, then offered cutoff.
    retained = []
    for row in records:
        review = [p for p in row["predictions"] if p["confidence"] >= lower[p["class_id"]]]
        ambiguous = {i for i, a in enumerate(review) for j, b in enumerate(review)
                     if i != j and a["class_id"] != b["class_id"] and iou(a["bbox"], b["bbox"]) >= .65}
        retained.append({**row, "predictions": [p for i, p in enumerate(review) if i not in ambiguous
                                                 and selection[LABELS[p["class_id"]]]["offered_enabled"]]})
    report = {**identity, "images": len(records), "match_iou": .5,
              "selection_rule": "Smallest cutoff >= .50 review / .70 offered meeting empirical precision .85 / .95, minimum 20 TP; fallback .50 review / disable OFFERED. Selection and scoring both use val, NOT independent validation.",
              "selection": selection, "previous_review": score(records, [.5]*4),
              "previous_offered": score(records, [.7]*4), "selected_review": score(records, lower),
              "selected_offered": score(retained, offered),
              "limitations": ["No true negative/OOD images", "No Indonesian camera holdout",
                              "No verified object-disjoint split", "No confidence probability calibration",
                              "Test split not used for threshold selection"]}
    (args.output / "validation-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
