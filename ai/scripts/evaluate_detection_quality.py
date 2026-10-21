"""Measure a reviewed camera holdout, or explicitly labelled synthetic sanity inputs."""
import argparse
from datetime import date
import hashlib
from io import BytesIO
import json
from pathlib import Path

from PIL import Image, ImageDraw

from wastenot_ai.contracts import RawDetection
from wastenot_ai.detection_quality import box_iou
from wastenot_ai.detector import YoloDetector

ROOT = Path(__file__).resolve().parents[1]


def synthetic_inputs():
    for name, color in (("black", "black"), ("white", "white"), ("gray", "gray"), ("green", "green")):
        image = Image.new("RGB", (640, 480), color)
        content = BytesIO()
        image.save(content, format="PNG")
        image.close()
        yield {"id": name, "kind": "SYNTHETIC", "truth": []}, content.getvalue()
    for size in (8, 64):
        image = Image.new("RGB", (640, 480), "white")
        draw = ImageDraw.Draw(image)
        for x in range(0, 640, size):
            for y in range(0, 480, size):
                if (x//size+y//size) % 2:
                    draw.rectangle((x, y, x+size-1, y+size-1), fill="black")
        content = BytesIO()
        image.save(content, format="PNG")
        image.close()
        yield {"id": f"checker-{size}", "kind": "SYNTHETIC", "truth": []}, content.getvalue()


def reviewed_inputs(path):
    manifest = json.loads(path.read_text("utf-8"))
    if manifest.get("usage") != "EVALUATION_ONLY" or not manifest.get("records"):
        raise ValueError("A nonempty EVALUATION_ONLY manifest is required; no placeholder results")
    seen_ids, seen_hashes = set(), set()
    for row in manifest["records"]:
        if (row["id"] in seen_ids or row["sha256"] in seen_hashes or not row.get("reviewer")
                or not row.get("capture_session_id") or row["kind"] not in {"POSITIVE", "NEGATIVE", "OOD"}):
            raise ValueError("Duplicate image or missing review/session provenance")
        if date.fromisoformat(row["reviewed_at"]) > date.today():
            raise ValueError("Review date cannot be in the future")
        image = (path.parent / row["image"]).resolve()
        if not image.is_relative_to(path.parent.resolve()):
            raise ValueError("Image must remain inside the manifest directory")
        content = image.read_bytes()
        if hashlib.sha256(content).hexdigest() != row["sha256"]:
            raise ValueError("Reviewed image checksum mismatch")
        if bool(row["truth"]) != (row["kind"] == "POSITIVE"):
            raise ValueError("Negative/OOD image must have verified empty target annotations")
        if row["kind"] == "POSITIVE" and not row.get("physical_object_ids"):
            raise ValueError("Positive image needs physical object provenance")
        for truth in row["truth"]:
            RawDetection.model_validate_json(json.dumps({**truth, "raw_class": str(truth["class_id"]), "confidence": 1.0}))
        seen_ids.add(row["id"])
        seen_hashes.add(row["sha256"])
        yield row, content


def match_counts(objects, truth):
    unmatched = set(range(len(truth)))
    tp = fp = 0
    for obj in sorted(objects, key=lambda value: -value["confidence"]):
        options = [index for index in unmatched if truth[index]["class_id"] == obj["class_id"]]
        best = max(options, key=lambda index: box_iou(obj["bbox"], truth[index]["bbox"]), default=None)
        if best is not None and box_iou(obj["bbox"], truth[best]["bbox"]) >= .5:
            tp += 1
            unmatched.remove(best)
        else:
            fp += 1
    return {"tp": tp, "fp": fp, "fn": len(unmatched)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument("--manifest", type=Path)
    inputs.add_argument("--synthetic", action="store_true")
    parser.add_argument("--model", type=Path, default=ROOT / "artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt")
    parser.add_argument("--device", default="cpu")
    parser.add_argument("--input-shape", choices=["adaptive", "square"], default="adaptive")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/detection-quality.json")
    args = parser.parse_args()
    detector = YoloDetector(args.model, args.device, args.input_shape)
    rows = []
    for row, content in synthetic_inputs() if args.synthetic else reviewed_inputs(args.manifest.resolve()):
        if any(item["class_id"] not in detector.labels for item in row["truth"]):
            raise ValueError("Annotation class not in checkpoint taxonomy")
        result = detector.detect(content)
        review = [obj for obj in result["objects"] if obj["supported_for_review"]]
        offered = [obj for obj in review if obj["confidence_band"] == "OFFERED"]
        rows.append({"id": row["id"], "kind": row["kind"], "image_sha256": hashlib.sha256(content).hexdigest(),
                     "review": match_counts(review, row["truth"]), "offered": match_counts(offered, row["truth"]),
                     "false_review_on_empty": not row["truth"] and bool(review),
                     "false_offered_on_empty": not row["truth"] and bool(offered), "objects": result["objects"]})
    if not rows:
        raise ValueError("No evaluated images")
    report = {"kind": "synthetic_sanity_NOT_real_negative_OOD_accuracy" if args.synthetic else "reviewed_camera_holdout",
              "model_sha256": detector.artifact["sha256"], "model_version": detector.artifact["model_version"],
              "input_shape": args.input_shape, "review_profile": result["review_profile"],
              "manifest_sha256": hashlib.sha256(args.manifest.read_bytes()).hexdigest() if args.manifest else None,
              "images": len(rows), "by_kind": {}, "records": rows,
              "limitations": ["Synthetic patterns do not validate real-world OOD rejection"] if args.synthetic else
              ["Provenance, missing annotations and independence require reviewer audit; no thresholds fitted here"]}
    for kind in sorted({row["kind"] for row in rows}):
        selected = [row for row in rows if row["kind"] == kind]
        report["by_kind"][kind] = {"images": len(selected),
            "images_with_false_review": sum(row["false_review_on_empty"] for row in selected),
            "images_with_false_offered": sum(row["false_offered_on_empty"] for row in selected),
            **{band: {key: sum(row[band][key] for row in selected) for key in ("tp", "fp", "fn")}
               for band in ("review", "offered")}}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("kind", "images", "by_kind")}))


if __name__ == "__main__":
    main()
