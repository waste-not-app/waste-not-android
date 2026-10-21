"""Train a local candidate, evaluate held-out data, and retain reproducible evidence."""
import argparse
import csv
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import shutil
from time import perf_counter

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".cache-ai/ultralytics"))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache-ai/matplotlib"))
os.environ["YOLO_AUTOINSTALL"] = "false"


def iou(a, b):
    intersection = max(0, min(a[2], b[2])-max(a[0], b[0])) * max(0, min(a[3], b[3])-max(a[1], b[1]))
    union = (a[2]-a[0])*(a[3]-a[1]) + (b[2]-b[0])*(b[3]-b[1]) - intersection
    return intersection / union if union > 0 else 0.0


def bottle_comparison(model, dataset, classes, device):
    tp = fp = fn = 0
    latencies = []
    images = sorted((dataset / "images/test").glob("*.jpg"))
    model.predict(str(images[0]), device=device, imgsz=640, verbose=False)
    for image in images:
        truth = []
        for line in (dataset / "labels/test" / image.with_suffix(".txt").name).read_text().splitlines():
            cls, x, y, w, h = map(float, line.split())
            if int(cls) in {1, 2, 3}:
                truth.append((x-w/2, y-h/2, x+w/2, y+h/2))
        start = perf_counter()
        prediction = model.predict(str(image), device=device, imgsz=640, conf=.25, verbose=False)[0]
        latencies.append((perf_counter()-start)*1000)
        unmatched = set(range(len(truth)))
        for box in prediction.boxes:
            if int(box.cls.item()) not in classes:
                continue
            coords = box.xyxyn[0].tolist()
            best = max(unmatched, key=lambda j: iou(coords, truth[j]), default=None)
            if best is not None and iou(coords, truth[best]) >= .5:
                tp += 1
                unmatched.remove(best)
            else:
                fp += 1
        fn += len(unmatched)
    import numpy as np
    return {"scope": "bottle localization only; ignores PET/glass/HDPE distinction; cans not a baseline class",
            "images": len(images), "confidence": .25, "iou": .5,
            "tp": tp, "fp": fp, "fn": fn, "precision": tp/(tp+fp) if tp+fp else 0,
            "recall": tp/(tp+fn) if tp+fn else 0,
            "latency_p50_ms": float(np.median(latencies)), "latency_p95_ms": float(np.percentile(latencies, 95))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=ROOT / "ai/data/local/drinking-waste-v2/data.yaml")
    parser.add_argument("--model", type=Path, default=ROOT / "ai/artifacts/models/yolo11n.pt")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--no-amp", action="store_true")
    parser.add_argument("--device", default="0")
    parser.add_argument("--name", default="drinking-waste-v2-yolo11n")
    parser.add_argument("--evaluate-only", type=Path)
    parser.add_argument("--resume", type=Path)
    args = parser.parse_args()
    import torch
    from ultralytics import YOLO, settings
    settings.update({key: False for key in ("sync", "wandb", "mlflow", "clearml", "comet", "hub",
                                           "neptune", "tensorboard", "dvc", "raytune") if key in settings})
    settings.update({"weights_dir": str(ROOT / "ai/artifacts/models")})
    if args.device != "cpu" and not torch.cuda.is_available():
        raise ValueError("CUDA unavailable; use dedicated training environment or explicitly --device cpu")
    if not args.data.is_file() or not args.model.is_file():
        raise ValueError("Dataset/model missing; prepare dataset and fetch baseline first")
    dataset = args.data.resolve().parent
    manifest = json.loads((dataset / "manifest.json").read_text())
    if manifest["labels"] != ["aluminium_can", "glass_bottle", "hdpe_bottle", "plastic_bottle"]:
        raise ValueError("Evaluation mappings require the audited four-class taxonomy")
    run_dir = ROOT / "ai/artifacts/training" / args.name
    if args.evaluate_only:
        best = args.evaluate_only.resolve()
    else:
        if run_dir.exists() and not args.resume:
            raise ValueError("Run exists: choose another --name or explicitly --resume last.pt")
        model = YOLO(str(args.resume or args.model))
        if args.resume:
            model.train(resume=True, device=args.device)
        else:
            model.train(data=str(args.data.resolve()), epochs=args.epochs, imgsz=640,
                        batch=args.batch, device=args.device, workers=args.workers, cache=False,
                        project=str(run_dir.parent), name=args.name, seed=42, deterministic=True,
                        optimizer="AdamW", lr0=.001, lrf=.01, patience=12, close_mosaic=8,
                        plots=True, amp=not args.no_amp, save=True, save_period=10)
        best = Path(model.trainer.save_dir) / "weights/best.pt"
    candidate = YOLO(str(best))
    if candidate.names != dict(enumerate(manifest["labels"])):
        raise ValueError("Checkpoint labels differ from the dataset and evaluation mappings")
    evaluation = candidate.val(data=str(args.data.resolve()), split="test", device=args.device,
                               workers=0, imgsz=640, batch=args.batch, plots=True,
                               project=str(run_dir), name="test", exist_ok=True)
    per_class = []
    for index, cls in enumerate(evaluation.box.ap_class_index):
        p, r, ap50, ap = evaluation.box.class_result(index)
        per_class.append({"class_id": int(cls), "name": candidate.names[int(cls)],
                          "precision": float(p), "recall": float(r), "map50": float(ap50), "map50_95": float(ap)})
    report = {"kind": "exploratory_public_dataset_not_pilot_release", "weights": str(best.relative_to(ROOT) if best.is_relative_to(ROOT) else best),
              "sha256": hashlib.sha256(best.read_bytes()).hexdigest(), "labels": candidate.names,
              "dataset_archive_sha256": manifest["archive_sha256"],
              "split_limitations": manifest["split_limitations"],
              "metrics": {k: float(v) for k, v in evaluation.results_dict.items()}, "per_class": per_class,
              "candidate_bottles": bottle_comparison(candidate, dataset, {1, 2, 3}, args.device),
              "baseline_bottles": bottle_comparison(YOLO(str(args.model)), dataset, {39}, args.device),
              "baseline_sha256": hashlib.sha256(args.model.read_bytes()).hexdigest(),
              "environment": {"python": platform.python_version(), "torch": torch.__version__,
                              "ultralytics": importlib.metadata.version("ultralytics"),
                              "cuda": torch.version.cuda, "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cpu"},
              "thresholds": "Demo thresholds .50/.70 are unchanged and uncalibrated for this candidate.",
              "not_evaluated": ["Indonesian camera holdout", "verified physical-object-disjoint holdout",
                                "negative/OOD rejection", "battery/cardboard/cup classes", "recommendation quality"]}
    run_dir.mkdir(parents=True, exist_ok=True)
    checkpoint_run = best.parent.parent
    if (checkpoint_run / "results.csv").is_file() and (checkpoint_run / "args.yaml").is_file():
        import yaml
        with (checkpoint_run / "results.csv").open() as stream:
            rows = list(csv.DictReader(stream))
        if rows:
            selected = max(rows, key=lambda row: float(row["metrics/mAP50-95(B)"]))
            report["training_summary"] = {
                "completed_epochs": len(rows), "best_validation_epoch": int(selected["epoch"]),
                "elapsed_training_seconds": float(rows[-1]["time"]),
                "configuration": yaml.safe_load((checkpoint_run / "args.yaml").read_text())}
    shutil.copyfile(ROOT / "ai/docs/DATASET_NOTICES.md", run_dir / "DATASET_NOTICES.md")
    (run_dir / "dataset-summary.json").write_text(json.dumps(
        {key: value for key, value in manifest.items() if key not in {"records", "rejected"}}, indent=2), encoding="utf-8")
    (run_dir / "evaluation.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
