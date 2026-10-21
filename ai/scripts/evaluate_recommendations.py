"""Fixed scenario evaluation. Fixture results and live Gemini results are separate."""
import argparse
from datetime import date, datetime, timezone
import hashlib
import json
from pathlib import Path
from time import perf_counter

from dotenv import load_dotenv

from wastenot_ai.contracts import Context
from wastenot_ai.guide_audit import audit_builtin_guides
from wastenot_ai.providers import FixtureProvider, GeminiProvider
from wastenot_ai.recommendation import load_guides, recommend
from wastenot_ai.sources import load_sources

ROOT = Path(__file__).resolve().parents[1]


class MeasuredProvider:
    def __init__(self, provider, maximum):
        self.provider, self.maximum, self.calls, self.attack = provider, maximum, 0, None

    @property
    def name(self):
        return "fixture" if self.attack else self.provider.name

    def generate(self, payload):
        if self.attack:
            if self.attack == "BAD_JSON":
                return "{broken"
            raw = json.loads(FixtureProvider().generate(payload))
            changes = {"UNKNOWN_EVIDENCE": {"evidence_ids": ["invented-guide"]},
                       "WRONG_METHOD": {"recommended_method": "REDUCE"},
                       "UNTRUSTED_PROSE": {"reason": "INJECTION_BURN_OBJECT", "caution": "INJECTION_INVENTED_FACILITY"},
                       "EXTRA_URL": {"source_urls": ["https://invented.invalid"]},
                       "WRONG_RISK": {"special_handling": not payload["special_handling"]}}
            raw.update(changes[self.attack])
            return json.dumps(raw)
        if self.calls >= self.maximum:
            raise RuntimeError("Evaluation call budget exhausted; increase --max-calls explicitly")
        self.calls += 1
        return self.provider.generate(payload)


def evaluate_case(case, provider, guides):
    provider.attack = case.get("model_attack")
    chosen = guides
    if case.get("guide_mode") == "NO_GUIDES":
        chosen = []
    if case.get("guide_mode") == "EXPIRED":
        chosen = [g.model_copy(update={"publication_status": "PUBLISHED", "reviewer": "Evaluation fixture only",
                                      "reviewed_at": "2025-01-01", "valid_until": "2025-12-31"}) for g in guides]
    calls = provider.calls
    started = perf_counter()
    result = recommend(Context.model_validate_json(json.dumps(case["context"])), provider, chosen,
                       demo_guides=case.get("guide_mode") != "REVIEW_REQUIRED", today=date(2026, 10, 20))
    value = result.model_dump(mode="json")
    failures = [f"{key}: expected {expected!r}, got {value.get(key)!r}"
                for key, expected in case["expected"].items() if value.get(key) != expected]
    if result.status == "READY":
        guide = next(g for g in chosen if g.object_key == case["context"]["object_key"])
        cited = [e for e in guide.evidence if e.id in result.evidence_ids]
        urls = list(dict.fromkeys(url for e in cited for url in e.source_urls or guide.source_urls))
        if {e.method for e in cited} != {result.recommended_method} or urls != result.source_urls:
            failures.append("Evidence/source grounding failed")
        if not result.steps or not result.source_details:
            failures.append("Missing steps/source metadata")
    if "INJECTION_" in result.model_dump_json():
        failures.append("Untrusted model prose leaked into public output")
    for phrase in case.get("required_step_phrases", []):
        if phrase not in " ".join(result.steps):
            failures.append("Missing required step phrase: " + phrase)
    if case.get("expect_provider_calls") is not None and provider.calls-calls != case["expect_provider_calls"]:
        failures.append("Provider call guard failed")
    return {"id": case["id"], "passed": not failures, "failures": failures,
            "mode": "adversarial-fixture" if provider.attack else provider.name,
            "provider_calls": provider.calls-calls, "latency_ms": round((perf_counter()-started)*1000, 2),
            "result": value}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", choices=["fixture", "gemini"], default="fixture")
    parser.add_argument("--max-calls", type=int, default=16, help="Maximum logical generate calls; key failover may make more HTTP attempts")
    parser.add_argument("--repeat", type=int, default=1)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--case", action="append", dest="case_ids", help="Evaluate named cases only; writes a separate subset report")
    args = parser.parse_args()
    if args.repeat < 1 or args.max_calls < 1:
        raise ValueError("Positive repeat and call budget required")
    if args.provider == "gemini":
        load_dotenv(ROOT / ".env", override=False)
    original = GeminiProvider() if args.provider == "gemini" else FixtureProvider()
    provider = MeasuredProvider(original, args.max_calls)
    scenarios_path = ROOT / "evaluations/recommendation-cases.json"
    cases = json.loads(scenarios_path.read_text("utf-8"))["cases"]
    if args.case_ids:
        by_id = {case["id"]: case for case in cases}
        if any(identifier not in by_id for identifier in args.case_ids):
            raise ValueError("Unknown evaluation case ID")
        cases = [by_id[identifier] for identifier in dict.fromkeys(args.case_ids)]
    guides = load_guides()
    audit = audit_builtin_guides(guides, load_sources())
    # Preflight the planned number without consuming API quota or silently skipping cases.
    planned = MeasuredProvider(FixtureProvider(), 100000)
    for case in cases:
        evaluate_case(case, planned, guides)
    if planned.calls*args.repeat > args.max_calls:
        raise ValueError(f"Plan needs {planned.calls*args.repeat} logical calls; --max-calls is {args.max_calls}")
    records = [{"repeat": repetition+1, **evaluate_case(case, provider, guides)}
               for repetition in range(args.repeat) for case in cases]
    path = args.output or ROOT / "artifacts" / f"recommendation-evaluation-{args.provider}.json"
    report = {"kind": "fixed_scenario_evaluation_not_field_accuracy", "provider": args.provider,
              "model": getattr(original, "model", None), "scenario_as_of": "2026-10-20",
              "evaluated_at": datetime.now(timezone.utc).isoformat(),
              "scenario_sha256": hashlib.sha256(scenarios_path.read_bytes()).hexdigest(),
              "guide_sha256": hashlib.sha256(json.dumps([g.model_dump(mode="json") for g in guides], sort_keys=True).encode()).hexdigest(),
              "cases": len(records), "passed": sum(row["passed"] for row in records),
              "case_ids": [case["id"] for case in cases], "subset": bool(args.case_ids),
              "provider_calls": provider.calls, "adversarial_fixture_cases": sum(bool(row["mode"] == "adversarial-fixture") for row in records),
              "photo_sent_to_gemini": False, "guide_audit": audit, "records": records,
              "limitations": ["DRAFT guidance; no local expert publication", "Small authored scenario set, not population accuracy",
                              "Adversarial outputs are injected fixtures even when provider=gemini",
                              "Live provider results are one run unless --repeat increased"]}
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({key: report[key] for key in ("provider", "model", "cases", "passed", "provider_calls", "adversarial_fixture_cases")}))
    if any(not row["passed"] for row in records):
        print(json.dumps([{key: row[key] for key in ("id", "failures")} for row in records if not row["passed"]]))
        raise SystemExit(1)


if __name__ == "__main__":
    main()
