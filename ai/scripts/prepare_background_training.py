"""Mine real background crops from TRAIN only; original val/test stay unchanged."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("YOLO_CONFIG_DIR", str(ROOT / ".cache-ai/ultralytics"))
os.environ.setdefault("MPLCONFIGDIR", str(ROOT / ".cache-ai/matplotlib"))
os.environ["YOLO_AUTOINSTALL"] = "false"


def safe_crop_regions(size, boxes, margin=.04):
    width, height = size
    # Retain only regions completely outside every annotated object plus a safety margin.
    expanded = [(max(0, x1-margin), max(0, y1-margin), min(1, x2+margin), min(1, y2+margin))
                for x1, y1, x2, y2 in boxes]
    for left, top, right, bottom in ((0, 0, .4, .4), (.6, 0, 1, .4), (0, .6, .4, 1), (.6, .6, 1, 1),
                                     (0, 0, 1, .3), (0, .7, 1, 1), (0, 0, .3, 1), (.7, 0, 1, 1)):
        if (right-left)*width < 180 or (bottom-top)*height < 180:
            continue
        if all(min(right, box[2]) <= max(left, box[0]) or min(bottom, box[3]) <= max(top, box[1]) for box in expanded):
            yield tuple(round(value*scale) for value, scale in zip((left, top, right, bottom), (width, height, width, height)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, default=ROOT / "ai/data/local/drinking-waste-v2")
    parser.add_argument("--output", type=Path, default=ROOT / "ai/data/local/drinking-waste-background-v1")
    parser.add_argument("--model", type=Path, default=ROOT / "ai/artifacts/training/drinking-waste-v2-yolo11n/weights/best.pt")
    parser.add_argument("--limit", type=int, default=600)
    parser.add_argument("--device", default="0")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Output already exists; inspect existing manifest or choose another directory")
    from ultralytics import YOLO, settings
    settings.update({"sync": False})
    model = YOLO(str(args.model))
    output = args.output.resolve()
    (output / "images/train").mkdir(parents=True)
    (output / "labels/train").mkdir(parents=True)
    candidates = []
    images = sorted((args.data / "images/train").glob("*.jpg"))
    for image in images:
        boxes = []
        for line in (args.data / "labels/train" / image.with_suffix(".txt").name).read_text().splitlines():
            _, x, y, w, h = map(float, line.split())
            boxes.append((x-w/2, y-h/2, x+w/2, y+h/2))
        with Image.open(image) as source:
            for coords in safe_crop_regions(source.size, boxes):
                identifier = hashlib.sha256((image.name+str(coords)).encode()).hexdigest()[:24]
                candidates.append({"id": identifier, "source_image": image.name, "crop_xyxy": coords})
    # Deterministic bounded candidate pool; mine only train, never validation failures.
    candidates.sort(key=lambda row: row["id"])
    candidates = candidates[:2400]
    ranked = []
    for index, row in enumerate(candidates):
        with Image.open(args.data / "images/train" / row["source_image"]) as source:
            crop = source.crop(row["crop_xyxy"]).convert("RGB")
        prediction = model.predict(crop, device=args.device, imgsz=640, conf=.25, verbose=False, save=False, max_det=50)[0]
        score = float(prediction.boxes.conf.max().item()) if len(prediction.boxes) else 0.0
        crop.close()
        ranked.append({**row, "mining_confidence": score})
        if (index+1) % 300 == 0:
            print(f"Background mining: {index+1}/{len(candidates)}", flush=True)
    ranked.sort(key=lambda row: (-row["mining_confidence"], row["id"]))
    # Limit repeated backgrounds from one source image, then retain a moderate negative ratio.
    selected, source_counts, hashes = [], {}, set()
    for row in ranked:
        if len(selected) >= args.limit:
            break
        if source_counts.get(row["source_image"], 0) >= 2:
            continue
        with Image.open(args.data / "images/train" / row["source_image"]) as source:
            crop = source.crop(row["crop_xyxy"]).convert("RGB")
            digest = hashlib.sha256(crop.tobytes()+str(crop.size).encode()).hexdigest()
            if digest in hashes:
                continue
            crop.save(output / "images/train" / f"{row['id']}.jpg", quality=92)
        (output / "labels/train" / f"{row['id']}.txt").write_text("")
        hashes.add(digest)
        source_counts[row["source_image"]] = source_counts.get(row["source_image"], 0)+1
        selected.append({**row, "pixel_sha256": digest})
    manifest = {"kind": "train_only_annotation_exclusion_background_crops", "source_manifest_sha256": hashlib.sha256((args.data / "manifest.json").read_bytes()).hexdigest(),
                "mining_model_sha256": hashlib.sha256(args.model.read_bytes()).hexdigest(), "source_split": "train",
                "margin_normalized": .04, "positive_images": len(images), "negative_images": len(selected),
                "candidate_crops": len(candidates), "records": selected,
                "limitations": ["Annotation-based exclusion, not independent complete image review", "Same source domains as training positives",
                                "Does not establish rejection of other real object classes"]}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
    (output / "train.txt").write_text("\n".join(str(p.resolve()).replace("\\", "/") for p in images)+"\n"+
        "\n".join(str(p).replace("\\", "/") for p in sorted((output / "images/train").glob("*.jpg")))+"\n", encoding="utf-8")
    import yaml
    config = {"path": str(output), "train": "train.txt", "val": str((args.data / "images/val").resolve()),
              "test": str((args.data / "images/test").resolve()), "names": model.names}
    (output / "data.yaml").write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf-8")
    shutil.copyfile(ROOT / "ai/docs/DATASET_NOTICES.md", output / "DATASET_NOTICES.md")
    # Contact sheets expose the hardest crops for visual review before any training.
    for page, start in enumerate(range(0, min(120, len(selected)), 40)):
        sheet = Image.new("RGB", (1000, 800), "white")
        draw = ImageDraw.Draw(sheet)
        for index, row in enumerate(selected[start:start+40]):
            with Image.open(output / "images/train" / f"{row['id']}.jpg") as source:
                source.thumbnail((190, 75))
                x, y = index % 5 * 200, index // 5 * 100
                sheet.paste(source, (x, y))
                draw.text((x, y+77), f"{start+index} {row['id'][:8]} {row['mining_confidence']:.2f}", fill="black")
        sheet.save(output / f"hardest-background-{page}.jpg")
    print(json.dumps({key: manifest[key] for key in ("positive_images", "negative_images", "candidate_crops")}))


if __name__ == "__main__":
    main()
