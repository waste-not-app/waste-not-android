"""Apply reviewed exclusions through split lists, preserving original downloaded files."""
from collections import Counter
import json
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "ai/data/local/broad-waste-v1"


def main():
    audit = json.loads((ROOT / "ai/evaluations/broad-waste-v1-data-audit.json").read_text("utf-8"))
    manifest = json.loads((DATA / "manifest.json").read_text("utf-8"))
    excluded = {str(i) for i in audit["excluded_coco_ids"]}
    records = []
    for r in manifest["records"]:
        if r["source"] == "COCO2017" and r["source_id"] in excluded:
            manifest["rejected"].append({"source": r["source"], "source_id": r["source_id"],
                                         "rejected": "visual_screen_unlabelled_additional_target"})
            continue
        records.append(r)
    manifest["records"] = records
    manifest["visual_audit"] = audit
    manifest["source_images"] = dict(Counter(r["source"] for r in records))
    manifest["images_per_split"] = dict(Counter(r["split"] for r in records))
    manifest["instances_per_split"] = {s: dict(Counter(int(b[0]) for r in records if r["split"] == s for b in r["boxes"]))
                                       for s in ("train", "val", "test")}
    for split in ("train", "val", "test"):
        (DATA / (split + ".txt")).write_text("".join((DATA / r["image"]).as_posix()+"\n"
                                                    for r in records if r["split"] == split), encoding="utf-8")
    config = yaml.safe_load((DATA / "data.yaml").read_text("utf-8"))
    config.update(train="train.txt", val="val.txt", test="test.txt")
    (DATA / "data.yaml").write_text(yaml.safe_dump(config, sort_keys=False), encoding="utf-8")
    (DATA / "manifest.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({k: manifest[k] for k in ("images_per_split", "source_images", "instances_per_split")}))


if __name__ == "__main__":
    main()
