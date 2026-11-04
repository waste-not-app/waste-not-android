"""Integration smoke for a running specialist demo; not an accuracy evaluation."""
import argparse
import json
import os
from pathlib import Path
import re

from playwright.sync_api import expect, sync_playwright


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:8095")
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--object-key", choices=["aluminium_can", "glass_bottle", "pet_bottle", "hdpe_bottle"], required=True)
    parser.add_argument("--live-gemini", action="store_true")
    args = parser.parse_args()
    photo = args.image.resolve()
    artifacts = Path(__file__).resolve().parents[1] / "artifacts"
    artifacts.mkdir(exist_ok=True)
    os.chdir(artifacts)
    errors, checks = [], []
    names = {"aluminium_can": "Kaleng aluminium", "glass_bottle": "Botol kaca", "pet_bottle": "Botol plastik", "hdpe_bottle": "Botol HDPE"}
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 1100})
        page.on("pageerror", lambda error: errors.append(str(error)))
        page.goto(args.url + "/?camera=off", wait_until="networkidle")
        config = page.evaluate("config")
        assert config["mapping_version"] == "drinking-waste-reviewed-02+dw-val-01"
        assert config["review_profile"] == "dw-val-01"
        assert config["review_only_classes"] == ["glass_bottle"]
        assert config["supported_visual_classes"] == 4
        assert set(config["supported_object_labels"]) == {"aluminium_can", "glass_bottle", "pet_bottle", "hdpe_bottle"}
        expect(page.locator("#catalog-summary")).to_contain_text("4 dari 69")
        checks.append("catalog_reflects_specialist_scope_and_includes_scoped_hdpe")
        page.locator("#file").set_input_files(photo)
        page.locator("#detect").click()
        boxes = page.locator(".bbox:not(.unsupported)").filter(has_text=re.compile(re.escape(names[args.object_key])))
        boxes.first.wait_for(state="visible", timeout=30000)
        # Overlapping class hypotheses can cover each other; the object list stays selectable.
        page.locator(".object-choice").filter(has_text=re.compile(re.escape(names[args.object_key]))).first.click()
        page.locator("#object-key").select_option(args.object_key)
        page.locator("#cleanliness").select_option("CLEAN")
        page.locator("#integrity").select_option("INTACT")
        page.locator("#reuse-intention").select_option("false")
        page.locator("#recycling-access").select_option("YES")
        page.locator("#provider").select_option("gemini" if args.live_gemini else "fixture")
        page.locator("#confirmed").check()
        if args.object_key == "hdpe_bottle":
            expect(page.locator("#former-contents-field")).to_be_visible()
            page.locator("#recommend").click()
            expect(page.locator("#method")).to_have_text("Lengkapi kondisi")
            expect(page.locator("#reason-code")).to_contain_text("isi botol sebelumnya")
            page.locator("#former-contents").select_option("FOOD_DRINK")
            checks.append("hdpe_unknown_contents_requests_context_before_provider")
        page.locator("#recommend").click()
        expect(page.locator("#method")).to_have_text("Recycle", timeout=30000)
        expect(page.locator("#recommendation-source")).to_have_text("Gemini langsung" if args.live_gemini else "Simulasi")
        assert page.locator("#reason").inner_text()
        assert page.locator("#source-list a").count() > 0
        checks.append("real_specialist_bbox_and_list_selection_confirmed_context_recommendation_sources")
        page.screenshot(path=str(artifacts / "finetuned-demo.png"), full_page=True)
        if args.object_key == "hdpe_bottle":
            page.locator("#former-contents").select_option("CHEMICAL")
            expect(page.locator("#risk")).to_be_checked()
            expect(page.locator("#recommendation-result")).to_be_hidden()
            page.locator("#recommend").click()
            expect(page.locator("#method")).to_have_text("Saran belum tersedia")
            checks.append("hdpe_chemical_history_blocks_ordinary_recycle_and_clears_stale_answer")
        page.locator("#clear").click()
        assert page.locator(".bbox").count() == 0
        checks.append("photo_and_detections_cleared")
        assert not errors, errors
        report = {"checks": checks, "model_version": config["model_version"], "browser_errors": errors,
                  "live_gemini": args.live_gemini, "camera": "not accessed; file upload",
                  "scope": "Integration smoke with explicitly supplied conditions, not accuracy/material verification"}
        (artifacts / "finetuned-demo-browser.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(json.dumps(report, indent=2))
        browser.close()


if __name__ == "__main__":
    main()
