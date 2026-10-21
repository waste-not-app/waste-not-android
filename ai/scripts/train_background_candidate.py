"""Fine-tune a separate candidate with train-only negatives; never replace the original checkpoint."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".cache-ai/ultralytics"))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache-ai/matplotlib"))
os.environ["YOLO_AUTOINSTALL"] = "false"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--epochs", type=int, default=24)
    parser.add_argument("--device", default="0")
    parser.add_argument("--name", default="drinking-waste-background-v1")
    parser.add_argument("--data", type=Path, default=ROOT / "ai/data/local/drinking-waste-background-v1/data.yaml")
    parser.add_argument("--initial-model", type=Path, default=ROOT / "ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt")
    parser.add_argument("--lr0", type=float, default=.0002)
    parser.add_argument("--mosaic", type=float, default=1.0)
    parser.add_argument("--degrees", type=float, default=0.0)
    parser.add_argument("--flipud", type=float, default=0.0)
    parser.add_argument("--scale", type=float, default=.5)
    args = parser.parse_args()
    from ultralytics import YOLO, settings
    settings.update({"sync": False, "weights_dir": str(ROOT / "ai/artifacts/models")})
    data, initial = args.data.resolve(), args.initial_model.resolve()
    run = ROOT / "ai/artifacts/training" / args.name
    if run.exists():
        raise ValueError("Run exists; choose a new --name")
    model = YOLO(str(initial))
    model.train(data=str(data), epochs=args.epochs, imgsz=640, batch=16, workers=4, device=args.device,
                project=str(run.parent), name=run.name, seed=42, deterministic=True,
                optimizer="AdamW", lr0=args.lr0, lrf=.01, patience=8, close_mosaic=8,
                mosaic=args.mosaic, degrees=args.degrees, flipud=args.flipud, scale=args.scale,
                amp=True, save=True, save_period=5, plots=True, cache=False)
    best = Path(model.trainer.save_dir) / "weights/best.pt"
    report = {"kind": "experimental_candidate_not_auto_promoted", "initial_sha256": hashlib.sha256(initial.read_bytes()).hexdigest(),
              "best_sha256": hashlib.sha256(best.read_bytes()).hexdigest(),
              "data_manifest_sha256": hashlib.sha256(data.with_name("manifest.json").read_bytes()).hexdigest(),
              "selection_split": "val", "test_used": False,
              "note": "Compare at fixed confidence on unchanged original val before promotion. Negative crops are annotation-excluded train backgrounds, not independent negatives."}
    (run / "provenance.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    shutil.copyfile(ROOT / "ai/docs/DATASET_NOTICES.md", run / "DATASET_NOTICES.md")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
