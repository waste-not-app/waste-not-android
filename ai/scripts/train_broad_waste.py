"""Train/evaluate a separate broad detector; never silently replace the active checkpoint."""
import argparse
from collections import Counter
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


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def overlap(a, b):
    inter = max(0, min(a[2], b[2])-max(a[0], b[0])) * max(0, min(a[3], b[3])-max(a[1], b[1]))
    union = (a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-inter
    return inter/union if union > 0 else 0


def evaluate_fixed(model, dataset, labels, device, mapping, split="val"):
    counts = {i: Counter(tp=0, fp=0, fn=0) for i in range(len(labels))}
    by_source = {}
    timings, records = [], []
    paths = [Path(line) for line in (dataset / (split + ".txt")).read_text("utf-8").splitlines() if line]
    for path in sorted(paths):
        truth = []
        for line in (dataset / "labels" / split / path.with_suffix(".txt").name).read_text().splitlines():
            c, x, y, w, h = map(float, line.split())
            if int(c) in mapping.values():
                truth.append({"class_id": int(c), "bbox": [x-w/2, y-h/2, x+w/2, y+h/2]})
        start = perf_counter()
        result = model.predict(str(path), device=device, imgsz=640, rect=False, conf=.5,
                               iou=.7, max_det=50, agnostic_nms=False, verbose=False)[0]
        timings.append((perf_counter()-start)*1000)
        predictions = [{"class_id": mapping[int(b.cls.item())], "confidence": float(b.conf.item()),
                        "bbox": b.xyxyn[0].tolist()} for b in result.boxes if int(b.cls.item()) in mapping]
        unmatched = set(range(len(truth)))
        per_image = {i: Counter(tp=0, fp=0, fn=0) for i in mapping.values()}
        for p in sorted(predictions, key=lambda x: -x["confidence"]):
            options = [i for i in unmatched if truth[i]["class_id"] == p["class_id"]]
            best = max(options, key=lambda i: overlap(p["bbox"], truth[i]["bbox"]), default=None)
            key = "fp"
            if best is not None and overlap(p["bbox"], truth[best]["bbox"]) >= .5:
                key = "tp"
                unmatched.remove(best)
            per_image[p["class_id"]][key] += 1
        for i in unmatched:
            per_image[truth[i]["class_id"]]["fn"] += 1
        source = path.name.split("-")[0]
        by_source.setdefault(source, {i: Counter(tp=0, fp=0, fn=0) for i in mapping.values()})
        for cls, count in per_image.items():
            counts[cls].update(count)
            by_source[source][cls].update(count)
        records.append({"image": path.name, "truth": truth, "predictions": predictions})
    def summarize(count):
        tp, fp, fn = count["tp"], count["fp"], count["fn"]
        return {**count, "precision": tp/(tp+fp) if tp+fp else None,
                "recall": tp/(tp+fn) if tp+fn else None}
    import numpy as np
    return {"protocol": {"split": split, "confidence": .5, "matching_iou": .5, "nms_iou": .7,
                          "imgsz": 640, "rect": False, "max_det": 50},
            "mapped_classes": [labels[i] for i in sorted(set(mapping.values()))],
            "per_class": {labels[i]: summarize(c) for i, c in counts.items() if i in mapping.values()},
            "per_source": {s: {labels[i]: summarize(c) for i, c in rows.items()} for s, rows in by_source.items()},
            "latency_p50_ms": float(np.median(timings[1:])),
            "latency_p95_ms": float(np.percentile(timings[1:], 95)), "records": records}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--epochs", type=int, default=35)
    parser.add_argument("--device", default="0")
    parser.add_argument("--name", default="broad-waste-v1-yolo11n")
    parser.add_argument("--evaluate-only", type=Path)
    parser.add_argument("--initial-model", type=Path, default=ROOT / "ai/artifacts/models/yolo11n.pt")
    parser.add_argument("--seed-known-head", action="store_true", help="Preserve pretrained bottle/cup classifier rows when hidden channels match")
    parser.add_argument("--lr0", type=float, default=.001)
    parser.add_argument("--freeze", type=int, default=0)
    args = parser.parse_args()
    import torch
    from ultralytics import YOLO, settings
    settings.update({"sync": False, "weights_dir": str(ROOT / "ai/artifacts/models")})
    if args.device != "cpu" and not torch.cuda.is_available():
        raise ValueError("CUDA unavailable")
    data = ROOT / "ai/data/local/broad-waste-v1"
    manifest = json.loads((data / "manifest.json").read_text("utf-8"))
    labels = manifest["labels"]
    run = ROOT / "ai/artifacts/training" / args.name
    baseline = ROOT / "ai/artifacts/models/yolo11n.pt"
    initial = args.initial_model.resolve()
    if args.evaluate_only:
        best = args.evaluate_only.resolve()
    else:
        if run.exists():
            raise ValueError("Run already exists; choose a new --name")
        model = YOLO(str(initial))
        if args.seed_known_head:
            original_head = model.model.model[-1]
            source_rows = [(head[-1].weight.detach().cpu().clone(), head[-1].bias.detach().cpu().clone())
                           for head in original_head.cv3]
            def seed_head(trainer):
                # YOLO11s retains the pretrained hidden width when nc changes 80 -> 10.
                target = trainer.model.model[-1]
                transferred = []
                with torch.no_grad():
                    for branch, (weight, bias) in zip(target.cv3, source_rows):
                        layer = branch[-1]
                        if layer.weight.shape[1:] != weight.shape[1:]:
                            raise ValueError("Head hidden channels differ; refuse partial guessed transfer")
                        for old_cls, new_cls in ((39, 0), (41, 2)):
                            layer.weight[new_cls].copy_(weight[old_cls].to(layer.weight.device))
                            layer.bias[new_cls].copy_(bias[old_cls].to(layer.bias.device))
                        transferred.append(list(layer.weight.shape))
                    if trainer.ema is not None:
                        trainer.ema.ema.load_state_dict(trainer.model.state_dict())
                (Path(trainer.save_dir) / "head-transfer.json").write_text(json.dumps({
                    "source_sha256": sha(initial), "class_rows": {"39:bottle": "0:bottle", "41:cup": "2:cup"},
                    "target_branch_shapes": transferred, "other_rows": "standard fresh initialization"}, indent=2)+"\n", encoding="utf-8")
            model.add_callback("on_pretrain_routine_end", seed_head)
        start = perf_counter()
        def progress(trainer):
            (Path(trainer.save_dir) / "progress.json").write_text(json.dumps({
                "epoch_completed": trainer.epoch+1, "max_epochs": args.epochs,
                "elapsed_seconds": perf_counter()-start,
                "metrics": {k: float(v) for k, v in trainer.metrics.items()}}, indent=2)+"\n", encoding="utf-8")
        model.add_callback("on_fit_epoch_end", progress)
        model.train(data=str(data / "data.yaml"), device=args.device, imgsz=640, batch=16,
                    workers=4, epochs=args.epochs, project=str(run.parent), name=run.name,
                    optimizer="AdamW", lr0=args.lr0, lrf=.01, cos_lr=True, freeze=args.freeze,
                    patience=10, close_mosaic=6, mosaic=.5, degrees=15, flipud=0, scale=.35,
                    seed=42, deterministic=True, amp=True, cache=False, plots=True,
                    save=True, save_period=5, verbose=False)
        best = Path(model.trainer.save_dir) / "weights/best.pt"
    model = YOLO(str(best))
    if model.names != dict(enumerate(labels)):
        raise ValueError("Checkpoint taxonomy mismatch")
    run.mkdir(parents=True, exist_ok=True)
    evaluation = model.val(data=str(data / "data.yaml"), split="test", device=args.device,
                           workers=0, batch=16, imgsz=640, rect=False, conf=.001,
                           max_det=50, plots=False, project=str(run), name="test-ap")
    per_class = []
    for index, cls in enumerate(evaluation.box.ap_class_index):
        p, r, ap50, ap = evaluation.box.class_result(index)
        per_class.append({"name": labels[int(cls)], "precision": float(p), "recall": float(r),
                          "map50": float(ap50), "map50_95": float(ap)})
    fixed = evaluate_fixed(model, data, labels, args.device, dict(enumerate(range(len(labels)))))
    # Baseline only has two comparable visual classes; no fabricated zero score for the others.
    coco = evaluate_fixed(YOLO(str(baseline)), data, labels, args.device, {39: 0, 41: 2})
    old = ROOT / "ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt"
    specialist = evaluate_fixed(YOLO(str(old)), data, labels, args.device, {0: 1, 1: 0, 2: 0, 3: 0})
    for name, value in (("candidate-fixed-val", fixed), ("coco-fixed-val", coco), ("specialist-fixed-val", specialist)):
        (run / (name + ".json")).write_text(json.dumps(value, indent=2)+"\n", encoding="utf-8")
    from ultralytics.utils.metrics import ConfusionMatrix
    matrix = ConfusionMatrix(names=dict(enumerate(labels)))
    for record in fixed["records"]:
        gt = record["truth"]
        pred = record["predictions"]
        matrix.process_batch({"cls": torch.tensor([p["class_id"] for p in pred]),
                              "conf": torch.tensor([p["confidence"] for p in pred]),
                              "bboxes": torch.tensor([p["bbox"] for p in pred]).reshape(-1, 4)},
                             {"cls": torch.tensor([g["class_id"] for g in gt]),
                              "bboxes": torch.tensor([g["bbox"] for g in gt]).reshape(-1, 4)},
                             conf=.5, iou_thres=.5)
    matrix.plot(normalize=False, save_dir=run)
    matrix.plot(normalize=True, save_dir=run)
    report = {"kind": "exploratory_broader_taxonomy_not_auto_promoted", "weights": best.relative_to(ROOT).as_posix(),
              "sha256": sha(best), "labels": labels, "initial_sha256": sha(initial),
              "manifest_sha256": sha(data / "manifest.json"),
              "images_per_split": manifest["images_per_split"], "source_images": manifest["source_images"],
              "instances_per_split": manifest["instances_per_split"],
              "test_metrics": {k: float(v) for k, v in evaluation.results_dict.items()}, "test_per_class": per_class,
              "fixed_validation": {"candidate": {k: v for k, v in fixed.items() if k != "records"},
                                   "coco": {k: v for k, v in coco.items() if k != "records"},
                                   "old_specialist": {k: v for k, v in specialist.items() if k != "records"}},
              "confusion_matrix": {"confidence": .5, "matching_iou": .5, "rows": "predicted", "columns": "truth",
                                   "labels": labels + ["background"], "counts": matrix.matrix.tolist()},
              "environment": {"python": platform.python_version(), "torch": torch.__version__,
                              "ultralytics": importlib.metadata.version("ultralytics"), "cuda": torch.version.cuda,
                              "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cpu"},
              "limitations": manifest["limitations"] + [
                  "COCO subset is from train2017 seen during baseline pretraining; its evaluation is not independent of pretraining.",
                  "Rare new classes have very limited supervision; per-class validation support must be inspected.",
                  "No default detector or existing checkpoint replaced."], "promoted": False}
    (run / "report.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    shutil.copyfile(data / "DATASET_NOTICES.md", run / "DATASET_NOTICES.md")
    public = ROOT / "ai/evaluations" / (args.name + "-20261020.json")
    public.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    for name in ("confusion_matrix.png", "confusion_matrix_normalized.png"):
        shutil.copyfile(run / name, public.with_name(args.name + "-val-conf050-" + name))
    print(json.dumps({"report": str(public), "sha256": report["sha256"], "test_metrics": report["test_metrics"]}), flush=True)


if __name__ == "__main__":
    main()
