from datetime import date
from importlib.resources import files
import json
from pathlib import Path
from typing import Protocol

from pydantic import ValidationError

from .catalog import confirm_context, load_catalog
from .contracts import Context, Guide, ModelSuggestion, Recommendation


class ProviderError(Exception):
    def __init__(self, code: str):
        self.code = code
        super().__init__(code)


class Provider(Protocol):
    name: str

    def generate(self, payload: dict) -> str: ...


def load_guides(path: Path | None = None) -> list[Guide]:
    text = path.read_text("utf-8") if path else files("wastenot_ai").joinpath("data/guides.json").read_text("utf-8")
    guides = [Guide.model_validate_json(json.dumps(x)) for x in json.loads(text)]
    if path is None:
        templates = json.loads(files("wastenot_ai").joinpath("data/guide_templates.json").read_text("utf-8"))
        for item in load_catalog()["items"]:
            if item["object_key"] in {g.object_key for g in guides} or not item.get("guide_template"):
                continue
            template = templates[item["guide_template"]]
            evidence = [{**e, "id":f"{item['object_key']}-{e['id']}"} for e in template["evidence"]]
            value = {**template,"evidence":evidence,"id":f"demo-{item['object_key']}","version":3,
                     "object_key":item["object_key"],"category":item["category"],"publication_status":"DRAFT",
                     "reviewed_at":None,"reviewer":None,"valid_until":None,
                     "scope_note":"Draf demo berbasis prinsip umum; perlu review penerapan lokal Indonesia.",
                     "special_handling":item["special_handling"]}
            guides.append(Guide.model_validate_json(json.dumps(value)))
    if len({x.object_key for x in guides}) != len(guides):
        raise ValueError("Panduan duplikat untuk object_key.")
    for guide in guides:
        ids = [x.id for x in guide.evidence]
        if len(set(ids)) != len(ids):
            raise ValueError("Evidence ID duplikat.")
    return guides


def is_reviewed(guide: Guide, today: date) -> bool:
    if guide.publication_status != "PUBLISHED" or not guide.reviewer or not guide.reviewed_at or not guide.valid_until:
        return False
    try:
        return date.fromisoformat(guide.reviewed_at) <= today <= date.fromisoformat(guide.valid_until)
    except ValueError:
        return False


def eligible_evidence(context: Context, guide: Guide, *, risk: bool = False):
    values = {**context.condition.model_dump(), "reuse_intention": context.reuse_intention,
              "recycling_access": context.recycling_access, "category": context.category.value,
              "object_key": context.object_key}
    reusable = (context.condition.cleanliness == "CLEAN" and context.condition.integrity == "INTACT"
                and context.condition.reusable == "YES" and context.reuse_intention is True)
    return [e for e in guide.evidence if (e.method != "REUSE" or reusable)
            and (not risk or e.risk_compatible)
            and (context.recommendation_goal != "PREVENTION" or e.method == "REDUCE")
            and (context.recommendation_goal != "HANDLING" or e.method != "REDUCE")
            and (e.method != "RECYCLE" or context.recycling_access != "NO")
            and all(values.get(k) == v for k, v in e.requires.items())]


def recommend(context: Context, provider: Provider, guides: list[Guide], *,
              demo_guides: bool = False, today: date | None = None) -> Recommendation:
    item = confirm_context(context)
    today = today or date.today()
    guide = next((g for g in guides if g.object_key == context.object_key and g.category == context.category), None)
    prototype = demo_guides or provider.name == "fixture"
    base = {"provider": provider.name, "prototype_only": prototype}

    risk = (item["special_handling"] or context.risk_flag or
            ((context.category == "GLASS" or context.object_key.startswith("ceramic_"))
             and context.condition.integrity == "DAMAGED") or
            (guide is not None and guide.special_handling))
    permitted_guide = guide is not None and (is_reviewed(guide, today) or
                                            (demo_guides and guide.publication_status == "DRAFT"))
    caution = guide.caution if permitted_guide else ""
    if risk:
        caution = " ".join(filter(None, ["Ada risiko khusus. Ikuti petunjuk pengemasan dan pengelola yang sesuai; jangan membuang benda berbahaya sembarangan.", caution]))

    def unavailable(code: str):
        return Recommendation(status="UNAVAILABLE", reason="Rekomendasi otomatis belum tersedia; periksa panduan dan coba lagi setelah konteks atau layanan siap.",
                              reason_code=code, special_handling=bool(risk), caution=caution,
                              guide_refs=[{"guide_id":guide.id,"version":guide.version}] if permitted_guide else [],
                              source_urls=guide.source_urls if permitted_guide else [], **base)

    if not permitted_guide:
        return unavailable("NO_REVIEWED_GUIDE")
    evidence = eligible_evidence(context, guide, risk=bool(risk))
    allowed = sorted({e.method for e in evidence})
    if not allowed:
        return unavailable("NO_ALLOWED_METHOD")
    payload = {
        "object_key": context.object_key, "category": context.category.value,
        "condition": context.condition.model_dump(), "reuse_intention": context.reuse_intention,
        "recommendation_goal": context.recommendation_goal, "recycling_access": context.recycling_access,
        "special_handling": bool(risk),
        "allowed_methods": allowed,
        "guide": {"id": guide.id, "version": guide.version, "scope_note": guide.scope_note,
                  "evidence": [e.model_dump() for e in evidence], "caution": guide.caution},
    }
    try:
        raw = ModelSuggestion.model_validate_json(provider.generate(payload))
    except ProviderError as exc:
        return unavailable(exc.code)
    except (ValidationError, ValueError, TypeError):
        return unavailable("INVALID_MODEL_OUTPUT")
    if raw.special_handling != bool(risk) or raw.recommended_method not in allowed:
        return unavailable("INVALID_MODEL_OUTPUT")
    by_id = {e.id: e for e in evidence}
    if len(set(raw.evidence_ids)) != len(raw.evidence_ids) or any(x not in by_id for x in raw.evidence_ids):
        return unavailable("INVALID_EVIDENCE")
    cited = [by_id[x] for x in raw.evidence_ids]
    if {e.method for e in cited} != {raw.recommended_method}:
        return unavailable("INVALID_EVIDENCE")
    # Render public text from validated evidence; free model prose is never a safety authority.
    reason = " ".join(e.text for e in cited)
    if len(reason) > 500:
        return unavailable("INVALID_EVIDENCE")
    return Recommendation(status="READY", recommended_method=raw.recommended_method,
                          recommendation_scope="PREVENTION" if raw.recommended_method == "REDUCE" else "HANDLING",
                          reason=reason,
                          special_handling=bool(risk), caution=caution, evidence_ids=raw.evidence_ids,
                          source_urls=list(dict.fromkeys(url for e in cited for url in (e.source_urls or guide.source_urls))),
                          steps=list(dict.fromkeys(step for e in cited for step in (e.steps or guide.steps))),
                          guide_refs=[{"guide_id": guide.id, "version": guide.version}], **base)
