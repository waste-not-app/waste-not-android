import json
from importlib.resources import files

from .contracts import Context, RawDetection


def load_catalog() -> dict:
    return json.loads(files("wastenot_ai").joinpath("data/catalog.json").read_text("utf-8"))


def confirm_context(context: Context) -> dict:
    item = next((x for x in load_catalog()["items"] if x["object_key"] == context.object_key), None)
    if item is None or item["category"] != context.category:
        raise ValueError("Objek/kategori tidak cocok dengan katalog pilot.")
    return item


def preserve_detected_risk(context: Context, selected: dict) -> Context:
    risk = any(item["special_handling"] for item in load_catalog()["items"]
               if item["baseline_class_id"] == selected["class_id"]
               and item["baseline_raw_class"] == selected["raw_class"])
    return context.model_copy(update={"risk_flag":True}) if risk else context


def review_detections(objects: list[RawDetection], labels: dict[int, str]) -> dict:
    catalog = load_catalog()
    thresholds = catalog["thresholds"]
    result = []
    for index, obj in enumerate(objects):
        if labels.get(obj.class_id) != obj.raw_class:
            raise ValueError("Class ID dan label tidak cocok dengan artifact model.")
        matches = [item for item in catalog["items"] if item["baseline_class_id"] == obj.class_id
                   and item["baseline_raw_class"] == obj.raw_class]
        supported = bool(matches)
        reviewable = supported and obj.confidence >= thresholds["lower"]
        band = "MANUAL_REQUIRED"
        if reviewable:
            band = "OFFERED" if obj.confidence >= thresholds["offered"] else "UNCERTAIN"
        result.append({
            **obj.model_dump(mode="json"), "object_id": f"object-{index + 1}",
            "confidence_band": band, "object_key": None, "category": None,
            "requires_material_confirmation": reviewable,
            "supported_for_review": reviewable,
            "suggested_object_keys": [item["object_key"] for item in matches] if reviewable else [],
        })
    return {
        "status": "REVIEW_REQUIRED" if any(x["supported_for_review"] for x in result) else "MANUAL_REQUIRED",
        "mapping_version": catalog["mapping_version"], "thresholds": thresholds, "objects": result,
    }
