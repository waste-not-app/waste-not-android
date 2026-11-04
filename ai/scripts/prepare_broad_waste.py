"""Prepare a reproducible, source-attributed object taxonomy from real bounding boxes."""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import random
import re
import shutil
import threading
import urllib.request
import zipfile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "ai/data/local/downloads/broad-waste-v1"
OUTPUT = ROOT / "ai/data/local/broad-waste-v1"
LABELS = ["bottle", "can", "cup", "plastic_bag", "paper", "cardboard",
          "drink_carton", "plastic_wrapper", "food_container", "bottle_cap"]
TACO_MAP = {4: 0, 5: 0, 6: 0, 10: 1, 12: 1, 20: 2, 21: 2, 22: 2, 23: 2, 24: 2,
            38: 3, 40: 3, 41: 3, 30: 4, 32: 4, 33: 4,
            13: 5, 14: 5, 15: 5, 17: 5, 18: 5, 19: 5, 16: 6,
            36: 7, 39: 7, 42: 7, 43: 8, 44: 8, 45: 8, 46: 8, 7: 9, 8: 9}
COCO_MAP = {44: 0, 47: 2}
LICENSES = {4: "https://creativecommons.org/licenses/by/2.0/",
            5: "https://creativecommons.org/licenses/by-sa/2.0/",
            9: "https://creativecommons.org/publicdomain/zero/1.0/",
            10: "https://creativecommons.org/publicdomain/mark/1.0/"}
TACO_URL = "https://raw.githubusercontent.com/pedropro/TACO/master/data/annotations.json"
COCO_URL = "https://s3.amazonaws.com/images.cocodataset.org/annotations/annotations_trainval2017.zip"
FLICKR_LIMITED = threading.Event()


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for part in iter(lambda: f.read(4 * 1024 * 1024), b""):
            h.update(part)
    return h.hexdigest()


def get(url, timeout=30):
    request = urllib.request.Request(url, headers={"User-Agent": "WasteNotDatasetAudit/1.0"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read(), response.url


def download(url, path):
    if path.exists():
        return
    temp = path.with_suffix(path.suffix + ".part")
    with urllib.request.urlopen(url, timeout=60) as src, temp.open("wb") as dst:
        shutil.copyfileobj(src, dst, 1024 * 1024)
    temp.replace(path)


def photo_license(url):
    photo_id = re.search(r"/([0-9]+)_", url)
    if not photo_id:
        return {"accepted": False, "reason": "no_flickr_photo_identity"}
    photo_id = photo_id.group(1)
    path = CACHE / "licenses" / f"{photo_id}.json"
    if path.exists():
        return json.loads(path.read_text("utf-8"))
    if FLICKR_LIMITED.is_set():
        return {"accepted": False, "reason": "Flickr rate limit reached; no further live request this run"}
    page_url = f"https://www.flickr.com/photo.gne?id={photo_id}"
    result = {"photo_id": photo_id, "source_page": page_url,
              "checked_at": datetime.now(timezone.utc).isoformat(), "accepted": False}
    try:
        raw, final = get(page_url, timeout=15)
        text = raw.decode("utf-8")
        if not re.search(r"/photos/[^/]+/" + photo_id + r"(?:/|$)", final):
            raise ValueError("Photo page redirected to another identity")
        match = re.search(r'"photoModel"\s*:\s*\{.*?"license"\s*:\s*(\d+)', text, re.S)
        if not match:
            raise ValueError("No current license in photo model")
        license_id = int(match.group(1))
        result.update(source_page=final, license_id=license_id,
                      license_url=LICENSES.get(license_id), accepted=license_id in LICENSES,
                      creator_page=final.rsplit("/" + photo_id, 1)[0] + "/")
        if not result["accepted"]:
            result["reason"] = "license_not_in_permissive_allowlist"
    except Exception as exc:
        if getattr(exc, "code", None) == 429:
            FLICKR_LIMITED.set()
        result["reason"] = type(exc).__name__ + ": " + str(exc)
    path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return result


def sources():
    (CACHE / "licenses").mkdir(parents=True, exist_ok=True)
    download(TACO_URL, CACHE / "taco.json")
    data = json.loads((CACHE / "taco.json").read_text("utf-8"))
    target_ids = {a["image_id"] for a in data["annotations"] if a["category_id"] in TACO_MAP}
    rows = [r for r in data["images"] if r["id"] in target_ids]
    results = []
    with ThreadPoolExecutor(max_workers=8) as pool:
        # Fetch annotations from the official COCO S3 bucket while photo rights are checked.
        archive = pool.submit(download, COCO_URL, CACHE / "coco-annotations2017.zip")
        futures = {pool.submit(photo_license, row["flickr_url"]): row for row in rows}
        for i, future in enumerate(as_completed(futures), 1):
            result = {"taco_image_id": futures[future]["id"], **future.result()}
            results.append(result)
            if i % 100 == 0:
                print(f"TACO rights audit {i}/{len(rows)}; accepted {sum(x['accepted'] for x in results)}", flush=True)
        archive.result()
    (CACHE / "taco-rights.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"rights": dict(Counter(str(r.get("license_id", r.get("reason"))) for r in results)),
                      "accepted": sum(r["accepted"] for r in results),
                      "coco_archive_bytes": (CACHE / "coco-annotations2017.zip").stat().st_size}), flush=True)


def normalized_box(box, width, height, cls):
    x, y, w, h = box
    if not all(math.isfinite(v) for v in box) or min(w, h) <= 0:
        raise ValueError("Invalid box")
    if x < -1 or y < -1 or x + w > width + 1 or y + h > height + 1:
        raise ValueError("Box outside stored pixels")
    x1, y1, x2, y2 = max(0, x), max(0, y), min(width, x + w), min(height, y + h)
    return [cls, (x1 + x2) / (2 * width), (y1 + y2) / (2 * height),
            (x2 - x1) / width, (y2 - y1) / height]


def source_records(coco_limit):
    taco = json.loads((CACHE / "taco.json").read_text("utf-8"))
    rights = {r["taco_image_id"]: r for r in json.loads((CACHE / "taco-rights.json").read_text("utf-8"))}
    taco_boxes = defaultdict(list)
    for ann in taco["annotations"]:
        if ann["category_id"] in TACO_MAP and not ann.get("iscrowd"):
            taco_boxes[ann["image_id"]].append(ann)
    records = []
    for image in taco["images"]:
        ident = image["id"]
        if not rights.get(ident, {}).get("accepted") or not taco_boxes[ident]:
            continue
        records.append({"source": "TACO", "source_id": str(ident),
                        "url": image.get("flickr_640_url") or image["flickr_url"],
                        "rights": rights[ident], "width": image["width"], "height": image["height"],
                        "boxes": [normalized_box(a["bbox"], image["width"], image["height"], TACO_MAP[a["category_id"]])
                                  for a in taco_boxes[ident]],
                        "group": "TACO-" + image["file_name"].split("/")[0] + "-" +
                                 str(int(re.search(r"\d+", Path(image["file_name"]).stem).group()) // 20)})
    with zipfile.ZipFile(CACHE / "coco-annotations2017.zip") as archive:
        coco = json.loads(archive.read("annotations/instances_train2017.json"))
    boxes = defaultdict(list)
    invalid = set()
    for ann in coco["annotations"]:
        if ann["category_id"] in COCO_MAP:
            if ann.get("iscrowd"):
                invalid.add(ann["image_id"])
            else:
                boxes[ann["image_id"]].append(ann)
    images = [im for im in coco["images"] if im["id"] in boxes and im["id"] not in invalid
              and im["license"] in {4, 5, 7, 8}]
    random.Random(42).shuffle(images)
    # Prefer cup scenes; bottles occurring in them are retained rather than erased.
    images.sort(key=lambda im: not any(a["category_id"] == 47 for a in boxes[im["id"]]))
    accepted = 0
    coco_licenses = {r["id"]: r for r in coco["licenses"]}
    # COCO provides an explicit, image-specific license ID and original URL.
    # This is publisher metadata evidence, distinct from TACO's live Flickr check.
    for im in images[:coco_limit]:
        declared = coco_licenses[im["license"]]
        right = {"accepted": True, "verification": "COCO2017 image-specific publisher license metadata",
                 "license_url": declared["url"], "license_name": declared["name"],
                 "source_page": im.get("flickr_url"), "creator_page": None,
                 "checked_at": datetime.now(timezone.utc).isoformat()}
        records.append({"source": "COCO2017", "source_id": str(im["id"]),
                        "url": im["coco_url"].replace("http://images.cocodataset.org/", "https://s3.amazonaws.com/images.cocodataset.org/"),
                        "rights": right, "width": im["width"], "height": im["height"],
                        "boxes": [normalized_box(a["bbox"], im["width"], im["height"], COCO_MAP[a["category_id"]])
                                  for a in boxes[im["id"]]],
                        "group": "COCO-photo-" + str(im["id"])})
        accepted += 1
    print(f"Selected {len(records)-accepted} TACO and {accepted} COCO images", flush=True)
    return records


def fetch_record(row):
    path = CACHE / "images" / f"{row['source']}-{row['source_id']}.jpg"
    try:
        download(row["url"], path)
        raw_hash = digest(path)
        with Image.open(path) as opened:
            im = opened.convert("RGB")
            # All boxes refer to stored pixels, never independently EXIF-transpose.
            if abs(im.width / im.height - row["width"] / row["height"]) > .01:
                raise ValueError("Downloaded aspect ratio differs from annotation")
            im.thumbnail((960, 960))
            from prepare_drinking_waste import perceptual_hash
            phash = perceptual_hash(im)
        return {**row, "raw_sha256": raw_hash, "phash": phash, "cached_path": str(path)}
    except Exception as exc:
        return {"source": row["source"], "source_id": row["source_id"], "rejected": str(exc)}


def prepare(coco_limit):
    if OUTPUT.exists():
        raise ValueError("Output exists; preserve it and choose a new version in the script")
    (CACHE / "images").mkdir(exist_ok=True)
    rows = source_records(coco_limit)
    with ThreadPoolExecutor(max_workers=8) as pool:
        fetched = list(pool.map(fetch_record, rows))
    rejected = [r for r in fetched if "rejected" in r]
    rows = [r for r in fetched if "rejected" not in r]
    old = ROOT / "ai/data/local/drinking-waste-v2"
    manifest = json.loads((old / "manifest.json").read_text("utf-8"))
    old_rows = manifest["records"]
    train_budget = Counter()
    random.Random(42).shuffle(old_rows)
    for r in old_rows:
        old_class = r["boxes"][0][0]
        if r["split"] == "train":
            if train_budget[old_class] >= 400:
                continue
            train_budget[old_class] += 1
        r = dict(r)
        r["original_classes"] = r.pop("classes", [])
        r.update(source="DrinkingWasteV2", source_id=r["image_id"],
                 boxes=[[1 if b[0] == 0 else 0, *b[1:]] for b in r["boxes"]],
                 group="DW-" + r["group_id"],
                 cached_path=str(old / "images" / r["split"] / (r["image_id"] + ".jpg")),
                 raw_sha256=r["sha256"])
        rows.append(r)
    # Union capture/photo groups plus near-duplicate images across every source.
    parent = list(range(len(rows)))
    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    def union(i, j):
        i, j = find(i), find(j)
        if i != j:
            parent[max(i, j)] = min(i, j)
    owners, exact, unique = {}, {}, []
    for i, r in enumerate(rows):
        if r["raw_sha256"] in exact:
            rejected.append({"source": r["source"], "source_id": r["source_id"], "rejected": "exact_duplicate"})
            continue
        exact[r["raw_sha256"]] = i
        unique.append(i)
        if r["group"] in owners:
            union(i, owners[r["group"]])
        owners[r["group"]] = i
        for j in unique[:-1]:
            if (r["phash"] ^ rows[j]["phash"]).bit_count() <= 5:
                union(i, j)
    groups = defaultdict(list)
    for i in unique:
        groups[find(i)].append(rows[i])
    totals, splits = Counter(), {s: [] for s in ("train", "val", "test")}
    class_totals = Counter(b[0] for i in unique for b in rows[i]["boxes"])
    group_items = list(groups.values())
    random.Random(42).shuffle(group_items)
    group_items.sort(key=lambda group: len(group), reverse=True)
    assigned_counts = {s: Counter() for s in splits}
    for group in group_items:
        locked = {r["split"] for r in group if r["source"] == "DrinkingWasteV2"}
        # A new near-duplicate linking multiple old splits is excluded entirely.
        if len(locked) > 1:
            rejected.extend({"source": r["source"], "source_id": r["source_id"], "rejected": "cross_split_component"} for r in group)
            continue
        counts = Counter(b[0] for r in group for b in r["boxes"])
        if locked:
            split = next(iter(locked))
        else:
            rarest = min(counts, key=lambda c: class_totals[c])
            split = min(splits, key=lambda s: (assigned_counts[s][rarest] + counts[rarest]) /
                        ({"train": .70, "val": .15, "test": .15}[s] * class_totals[rarest]))
        splits[split].extend(group)
        assigned_counts[split].update(counts)
        totals[split] += len(group)
    OUTPUT.mkdir(parents=True)
    records = []
    for split, group in splits.items():
        for kind in ("images", "labels"):
            (OUTPUT / kind / split).mkdir(parents=True, exist_ok=True)
        for r in group:
            stem = r["source"] + "-" + r["source_id"]
            dest = OUTPUT / "images" / split / (stem + ".jpg")
            with Image.open(r["cached_path"]) as opened:
                im = opened.convert("RGB")
                im.thumbnail((960, 960))
                im.save(dest, quality=95)
            (OUTPUT / "labels" / split / (stem + ".txt")).write_text(
                "".join(f"{int(b[0])} " + " ".join(f"{v:.8f}" for v in b[1:]) + "\n" for b in r["boxes"]), encoding="utf-8")
            records.append({**r, "split": split, "image": dest.relative_to(OUTPUT).as_posix(),
                            "prepared_sha256": digest(dest)})
    import yaml
    (OUTPUT / "data.yaml").write_text(yaml.safe_dump({"path": OUTPUT.as_posix(), "train": "images/train",
        "val": "images/val", "test": "images/test", "names": dict(enumerate(LABELS))}, sort_keys=False), encoding="utf-8")
    report = {"version": "broad-waste-v1", "seed": 42, "labels": LABELS,
              "sources": {"TACO": {"url": TACO_URL, "metadata_sha256": digest(CACHE / "taco.json")},
                          "COCO2017": {"url": COCO_URL, "archive_sha256": digest(CACHE / "coco-annotations2017.zip")},
                          "DrinkingWasteV2": {"manifest_sha256": digest(old / "manifest.json")}},
              "images_per_split": dict(totals), "instances_per_split": {s: dict(c) for s, c in assigned_counts.items()},
              "source_images": dict(Counter(r["source"] for r in records)),
              "limitations": ["Public-data exploratory split; no independent local-camera field holdout.",
                  "COCO annotates bottle/cup but not all additional waste classes; audit sampled scenes for missing target boxes.",
                  "TACO batch+filename-block20, COCO photo identities, old DW groups and pHash are proxy groups, not verified physical-object identities.",
                  "Old train is deterministically capped at 400 images per original material class; old val/test remain reserved.",
                  "Old test already inspected historically; do not call combined test fully untouched."],
              "records": records, "rejected": rejected}
    (OUTPUT / "manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (OUTPUT / "DATASET_NOTICES.md").write_text(
        "# Broad Waste v1\n\nTACO: Pedro F. Proença and Pedro Simões; annotations/toolkit source https://github.com/pedropro/TACO .\n"
        "COCO: Lin et al., Microsoft COCO: Common Objects in Context; https://cocodataset.org/ .\n"
        "Photo source URLs and publisher-declared (COCO) or live-checked (TACO) licenses are recorded per image in manifest.json.\n"
        "Original Drinking Waste / TrashNet attribution and MIT terms follow the original notice below.\n\n" +
        (ROOT / "ai/docs/DATASET_NOTICES.md").read_text("utf-8"), encoding="utf-8")
    # Contact sheets are local audit aids, never published as dataset redistribution.
    external = [r for r in records if r["source"] != "DrinkingWasteV2"]
    for page in range((len(external) + 59) // 60):
        sheet = Image.new("RGB", (1200, 900), "white")
        draw = ImageDraw.Draw(sheet)
        for k, r in enumerate(external[page * 60:(page + 1) * 60]):
            x, y = (k % 10) * 120, (k // 10) * 150
            with Image.open(OUTPUT / r["image"]) as im:
                im = im.convert("RGB")
                im.thumbnail((118, 120))
                for cls, xc, yc, w, h in r["boxes"]:
                    ImageDraw.Draw(im).rectangle(((xc-w/2)*im.width, (yc-h/2)*im.height,
                                                  (xc+w/2)*im.width, (yc+h/2)*im.height), outline="lime", width=2)
                sheet.paste(im, (x, y))
            draw.text((x, y + 121), r["source_id"] + " " + r["source"][:4], fill="black")
        sheet.save(OUTPUT / f"audit-{page:02}.jpg")
    print(json.dumps({k: report[k] for k in ("images_per_split", "instances_per_split", "source_images")}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", choices=("sources", "prepare"), required=True)
    parser.add_argument("--coco-limit", type=int, default=900)
    args = parser.parse_args()
    if args.stage == "sources":
        sources()
    else:
        prepare(args.coco_limit)
