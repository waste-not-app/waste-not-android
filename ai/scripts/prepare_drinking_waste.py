"""Download and audit the versioned public Drinking Waste detection dataset."""
import argparse
from collections import Counter, defaultdict
import hashlib
import io
import json
import math
from pathlib import Path
import random
import re
import urllib.request
import zipfile

from PIL import Image

SOURCE = "https://www.kaggle.com/datasets/arkadiyhacks/drinking-waste-classification"
DOWNLOAD = "https://www.kaggle.com/api/v1/datasets/download/arkadiyhacks/drinking-waste-classification?datasetVersionNumber=2"
ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARCHIVE = ROOT / "ai/data/local/downloads/drinking-waste.zip"
DEFAULT_OUTPUT = ROOT / "ai/data/local/drinking-waste-v2"
ARCHIVE_SHA256 = "2231be78c398a8606b29399ccc8797be0586d5c4894d610d4ff5e84494dd1449"


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_boxes(text, class_count):
    boxes = []
    for line in text.splitlines():
        if not line.strip():
            continue
        values = line.split()
        if len(values) != 5:
            raise ValueError("Expected class xc yc width height")
        cls = int(values[0])
        xc, yc, width, height = map(float, values[1:])
        if not 0 <= cls < class_count or not all(map(math.isfinite, (xc, yc, width, height))):
            raise ValueError("Invalid class or nonfinite box")
        if not (0 < width <= 1 and 0 < height <= 1 and 0 <= xc <= 1 and 0 <= yc <= 1):
            raise ValueError("Box outside normalized range")
        if xc-width/2 < -0.002 or yc-height/2 < -0.002 or xc+width/2 > 1.002 or yc+height/2 > 1.002:
            raise ValueError("Box edges outside image")
        boxes.append((cls, xc, yc, width, height))
    if not boxes:
        raise ValueError("Empty annotations are not verified negative examples")
    return boxes


def perceptual_hash(image):
    import cv2
    import numpy as np
    gray = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2GRAY)
    small = cv2.resize(gray, (32, 32), interpolation=cv2.INTER_AREA).astype(np.float32)
    dct = cv2.dct(small)[:8, :8].flatten()[1:]
    return sum(int(value > np.median(dct)) << i for i, value in enumerate(dct))


def prepare(archive, output, seed):
    archive_digest = sha256(archive)
    if archive_digest != ARCHIVE_SHA256:
        raise ValueError("Archive differs from the audited v2 checksum; inspect provenance before use")
    if output.exists():
        raise ValueError(f"Output already exists; choose a new --output: {output}")
    output.mkdir(parents=True)
    records, rejected, seen = [], [], {}
    with zipfile.ZipFile(archive) as data:
        label_paths = [n for n in data.namelist() if Path(n).name.lower() == "labels.txt"]
        print("Label files:", label_paths, flush=True)
        # v2 omits labels.txt despite the description. Verify IDs against all named class files.
        labels = ["aluminium_can", "glass_bottle", "hdpe_bottle", "plastic_bottle"]
        expected = {"AluCan": 0, "Glass": 1, "HDPEM": 2, "PET": 3}
        for name in data.namelist():
            if "/YOLO_imgs/" not in name or not name.endswith(".txt"):
                continue
            prefix = re.sub(r"[\d,]+$", "", Path(name).stem)
            if prefix in expected:
                ids = {int(line.split()[0]) for line in data.read(name).decode("utf-8-sig").splitlines() if line.strip()}
                if ids != {expected[prefix]}:
                    raise ValueError(f"Source prefix and label IDs disagree: {name}")
        print("Source classes:", labels, flush=True)
        if len(labels) != 4:
            raise ValueError("Unexpected taxonomy; inspect source before training")
        members = set(data.namelist())
        images = sorted(n for n in members if "/YOLO_imgs/" in n and Path(n).suffix.lower() in {".jpg", ".jpeg", ".png"})
        for index, name in enumerate(images):
            try:
                label_name = str(Path(name).with_suffix(".txt")).replace("\\", "/")
                boxes = parse_boxes(data.read(label_name).decode("utf-8-sig"), len(labels))
                raw = data.read(name)
                digest = hashlib.sha256(raw).hexdigest()
                if digest in seen:
                    original = seen[digest]
                    if sorted(b[0] for b in boxes) != sorted(b[0] for b in original["boxes"]):
                        raise RuntimeError(f"Conflicting classes on duplicate image: {name}")
                    rejected.append({"source": name, "reason": "exact_duplicate", "of": original["source"],
                                     "annotation_differs": boxes != original["boxes"],
                                     "decision": "retain first valid source annotation; no averaged/generated boxes"})
                    continue
                with Image.open(io.BytesIO(raw)) as opened:
                    # Labels refer to stored pixels; do not EXIF-transpose those pixels independently.
                    image = opened.convert("RGB")
                image.thumbnail((960, 960))
                phash = perceptual_hash(image)
                stem = Path(name).stem
                match = re.fullmatch(r"([A-Za-z_]+)([\d,]+)", stem)
                if not match:
                    raise ValueError("Unknown filename sequence; cannot group conservatively")
                prefix, sequence = match.groups()
                sequence = int(sequence.replace(",", ""))
                record = {"source": name, "sha256": digest, "phash": phash,
                          "sequence_group": f"{prefix}-{(sequence-1)//200}",
                          "boxes": boxes, "classes": sorted({b[0] for b in boxes}),
                          "image_id": digest[:20], "width": image.width, "height": image.height}
                image_dir = output / "staging"
                image_dir.mkdir(exist_ok=True)
                image.save(image_dir / f"{record['image_id']}.jpg", quality=95)
                image.close()
                records.append(record)
                seen[digest] = record
            except (ValueError, KeyError, OSError) as exc:
                rejected.append({"source": name, "reason": str(exc)})
            if index % 500 == 0:
                print(f"Audited {index}/{len(images)}", flush=True)
    if not records:
        raise ValueError("No usable annotated images")

    # Conservative contiguous capture blocks plus connected near-duplicate groups.
    parent = list(range(len(records)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        a, b = find(a), find(b)
        if a != b:
            parent[max(a, b)] = min(a, b)

    block_owner = {}
    for i, row in enumerate(records):
        block = row["sequence_group"]
        if block in block_owner:
            union(i, block_owner[block])
        else:
            block_owner[block] = i
    near_pairs = 0
    for i, row in enumerate(records):
        for j in range(i):
            if (row["phash"] ^ records[j]["phash"]).bit_count() <= 5:
                union(i, j)
                near_pairs += 1
    groups = defaultdict(list)
    for i, row in enumerate(records):
        groups[find(i)].append(row)
    # Group-level stratification; never split a near-duplicate component.
    rng = random.Random(seed)
    by_class = defaultdict(list)
    for key, rows in groups.items():
        dominant = Counter(c for row in rows for c in row["classes"]).most_common(1)[0][0]
        by_class[dominant].append((key, rows))
    assigned = {}
    for cls, class_groups in by_class.items():
        if len(class_groups) < 3:
            raise ValueError(f"Class {cls} has only {len(class_groups)} independent proxy groups; cannot split")
        rng.shuffle(class_groups)
        class_groups.sort(key=lambda item: len(item[1]), reverse=True)
        totals = Counter()
        target = sum(len(rows) for _, rows in class_groups)
        ratios = {"train": .70, "val": .15, "test": .15}
        for key, rows in class_groups:
            split = min(ratios, key=lambda s: (totals[s] + len(rows)) / (target * ratios[s]))
            assigned[key] = split
            totals[split] += len(rows)
    counts, group_counts = defaultdict(Counter), Counter()
    for key, rows in groups.items():
        split = assigned[key]
        group_counts[split] += 1
        for row in rows:
            row.update(split=split, group_id=f"proxy-{key}")
            for kind in ("images", "labels"):
                (output / kind / split).mkdir(parents=True, exist_ok=True)
            name = row["image_id"]
            (output / "staging" / f"{name}.jpg").replace(output / "images" / split / f"{name}.jpg")
            (output / "labels" / split / f"{name}.txt").write_text(
                "".join(f"{cls} {xc:.8f} {yc:.8f} {w:.8f} {h:.8f}\n" for cls, xc, yc, w, h in row["boxes"]), encoding="utf-8")
            counts[split].update(str(c) for c, *_ in row["boxes"])
    (output / "staging").rmdir()
    config = {"path": output.resolve().as_posix(), "train": "images/train", "val": "images/val",
              "test": "images/test", "names": dict(enumerate(labels))}
    import yaml
    (output / "data.yaml").write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf-8")
    report = {"source": SOURCE, "download": DOWNLOAD, "version": 2, "archive_sha256": archive_digest,
              "licenses": ["CC0 (publisher)", "MIT (acknowledged TrashNet images; retain notice)"],
              "seed": seed, "labels": labels, "accepted_images": len(records),
              "images_per_split": dict(Counter(r["split"] for r in records)),
              "instances_per_split": dict(counts), "groups_per_split": dict(group_counts),
              "near_duplicate_pairs": near_pairs, "rejected": rejected,
              "split_limitations": "Sequence blocks of 200 and pHash distance <=5 are capture proxies. Source has no verified physical_object_id/capture_session_id. Physical-object disjointness is NOT established; exploratory evaluation only, not pilot release evidence. No verified negative/OOD set.",
              "records": records}
    (output / "manifest.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    import shutil
    shutil.copyfile(ROOT / "ai/docs/DATASET_NOTICES.md", output / "DATASET_NOTICES.md")
    print(json.dumps({k: v for k, v in report.items() if k not in {"records", "rejected"}}, indent=2), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    if not args.archive.exists():
        if not args.download:
            parser.error("Archive missing; use --download or supply --archive")
        args.archive.parent.mkdir(parents=True, exist_ok=True)
        partial = args.archive.with_suffix(".partial")
        urllib.request.urlretrieve(DOWNLOAD, partial)
        partial.replace(args.archive)
    prepare(args.archive, args.output, args.seed)


if __name__ == "__main__":
    main()
