"""Browser smoke against a running local demo; camera uses Chromium's fake device."""
import argparse
import json
import os
from pathlib import Path
import re

from playwright.sync_api import expect, sync_playwright

parser = argparse.ArgumentParser()
parser.add_argument("--url",default="http://127.0.0.1:8092")
parser.add_argument("--live-gemini",action="store_true")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
(root / "artifacts").mkdir(exist_ok=True)
# Chromium subprocesses may write debug.log relative to cwd even with --log-file.
os.chdir(root / "artifacts")
errors = []
checks = []
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True,args=["--use-fake-ui-for-media-stream","--use-fake-device-for-media-stream",
                                                           "--log-file="+str(root / "artifacts/chromium-debug.log")])
    context = browser.new_context(viewport={"width":1440,"height":1100},permissions=["camera"])
    page = context.new_page()
    page.on("pageerror",lambda error:errors.append(str(error)))
    page.goto(args.url,wait_until="networkidle")
    expect(page.locator("#capture")).to_be_enabled()
    page.locator("#capture").click()
    page.locator("#detect").wait_for(state="visible")
    assert page.locator("#camera").is_hidden()
    assert page.evaluate("document.querySelector('#camera').srcObject === null")
    checks.append("fake_camera_capture_stops_tracks_before_upload")
    previous_photo = page.locator("#photo").get_attribute("src")
    page.locator("#file").set_input_files(root / "data/local/smoke/bottle.jpg")
    expect(page.locator("#photo")).not_to_have_attribute("src",previous_photo)
    expect(page.locator("#camera-status")).to_have_text("Tinjau foto")
    page.locator("#detect").click()
    page.locator(".bbox[aria-label^='Pilih Botol,']").wait_for(state="visible",timeout=25000)
    page.locator(".bbox[aria-label^='Pilih Botol,']").click()
    page.locator("#object-key").select_option("glass_bottle")
    page.locator("#cleanliness").select_option("CLEAN")
    page.locator("#integrity").select_option("INTACT")
    page.locator("#reuse-intention").select_option("false")
    page.locator("#provider").select_option("fixture")
    page.locator("#confirmed").check()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Recycle")
    assert page.locator("#recommendation-source").inner_text() == "Simulasi"
    checks.append("real_yolo_selectable_bbox_fixture_recommendation")
    page.screenshot(path=str(root / "artifacts/camera-demo-desktop.png"),full_page=True)
    # Controlled delayed response: changing context must discard an old answer,
    # even if a transport ignores AbortSignal. This makes no provider request.
    page.evaluate("""() => {
      window.realDemoFetch = window.fetch;
      window.fetch = (url, options) => String(url).endsWith('/recommend')
        ? new Promise(resolve => setTimeout(() => resolve(new Response(JSON.stringify({data:{
            status:'READY',provider:'fixture',recommended_method:'RECYCLE',recommendation_scope:'HANDLING',
            reason:'STALE TEST RESPONSE',caution:'',steps:[],source_urls:[],guide_status:'DRAFT',evidence_ids:[]
          }}),{status:200,headers:{'Content-Type':'application/json'}})),500))
        : window.realDemoFetch(url,options);
    }""")
    page.locator("#recommend").click()
    page.locator("#cleanliness").select_option("DIRTY")
    page.wait_for_timeout(650)
    assert page.locator("#recommendation-result").is_hidden()
    assert "STALE TEST RESPONSE" not in page.locator("#reason").inner_text()
    page.evaluate("window.fetch = window.realDemoFetch")
    page.locator("#cleanliness").select_option("CLEAN")
    checks.append("late_recommendation_discarded_after_context_change")
    page.locator("#integrity").select_option("DAMAGED")
    assert page.locator("#recommendation-result").is_hidden()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Trash")
    expect(page.locator("#caution")).to_contain_text("Perlu penanganan khusus")
    expect(page.locator("#source-list a")).to_have_attribute("href","https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/broken-glass/")
    checks.append("broken_glass_keeps_trash_label_with_special_warning")
    if args.live_gemini:
        page.locator("#integrity").select_option("INTACT")
        page.locator("#provider").select_option("gemini")
        page.locator("#recommend").click()
        expect(page.locator("#recommendation-result")).to_be_visible(timeout=30000)
        assert page.locator("#method").inner_text() == "Recycle",page.locator("#global-message").inner_text()
        assert page.locator("#recommendation-source").inner_text() == "Gemini langsung"
        checks.append("live_gemini_browser_recommendation")
        page.screenshot(path=str(root / "artifacts/camera-demo-gemini.png"),full_page=True)
    page.locator("#integrity").select_option("INTACT")
    page.locator("#recommendation-goal").select_option("PREVENTION")
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Reduce",timeout=30000)
    expect(page.locator("#recommendation-scope")).to_contain_text("Pencegahan")
    expect(page.locator("#source-list a")).to_have_attribute("href","https://www.epa.gov/climate-change/what-you-can-do-about-climate-change-waste")
    assert page.locator("#sources").get_attribute("open") is not None
    assert page.locator("#alternative").count() == 0
    checks.append("reduce_is_single_prevention_result_with_matching_source")
    page.locator("#catalog-search").fill("Buku kertas")
    page.locator(".catalog-item").click()
    page.locator("#cleanliness").select_option("CLEAN")
    page.locator("#integrity").select_option("INTACT")
    page.locator("#reusable").select_option("YES")
    page.locator("#reuse-intention").select_option("true")
    page.locator("#confirmed").check()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Reuse",timeout=30000)
    assert page.locator("#reason").inner_text() and page.locator("#source-list a").count()
    checks.append("reuse_book_with_confirmed_condition_reason_and_sources")
    page.locator("#catalog-search").fill("Sikat gigi plastik manual")
    page.locator(".catalog-item").click()
    page.locator("#reusable").select_option("NO")
    page.locator("#recycling-access").select_option("NO")
    page.locator("#confirmed").check()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Trash",timeout=30000)
    expect(page.locator("#source-list a")).to_have_attribute("href","https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/toothbrushes/")
    checks.append("trash_manual_plastic_toothbrush_with_confirmed_no_recycling")
    page.locator("#catalog-search").fill("baterai")
    page.locator(".catalog-item").click()
    assert page.locator("#selected-label").inner_text() == "Pilihan manual"
    page.locator("#confirmed").check()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Recycle",timeout=30000)
    expect(page.locator("#caution")).to_contain_text("Perlu penanganan khusus")
    expect(page.locator("#handling-steps")).to_contain_text("layanan khusus")
    checks.append("battery_keeps_recycle_label_and_specialist_collection_warning")
    page.locator("#catalog-search").fill("Gelas kaca")
    page.locator(".catalog-item").click()
    page.locator("#integrity").select_option("DAMAGED")
    page.locator("#confirmed").check()
    page.locator("#recommend").click()
    expect(page.locator("#method")).to_have_text("Trash",timeout=30000)
    expect(page.locator("#caution")).to_contain_text("Perlu penanganan khusus")
    expect(page.locator("#handling-steps")).not_to_contain_text("Gunakan kembali hanya")
    expect(page.locator("#source-list a")).to_have_attribute("href","https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/broken-glass/")
    if args.live_gemini:
        expect(page.locator("#recommendation-source")).to_have_text("Gemini langsung")
    checks.append("broken_drinking_glass_has_trash_reason_sources_and_no_reuse_steps")
    page.screenshot(path=str(root / "artifacts/camera-demo-special-classification.png"),full_page=True)
    page.set_viewport_size({"width":390,"height":844})
    page.locator("#clear").click()
    expect(page.locator("#photo-stage")).to_be_hidden()
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    page.screenshot(path=str(root / "artifacts/camera-demo-mobile.png"),full_page=True)
    checks.append("mobile_layout_and_photo_cleanup")
    # Denial is exercised with a fresh browser context and a controlled API rejection.
    denied = browser.new_context(viewport={"width":390,"height":844})
    denied.add_init_script("Object.defineProperty(navigator.mediaDevices,'getUserMedia',{value:()=>Promise.reject(new DOMException('Denied','NotAllowedError'))})")
    denied_page = denied.new_page()
    denied_page.goto(args.url,wait_until="networkidle")
    expect(denied_page.locator("#global-message")).to_contain_text("Izin kamera")
    assert denied_page.locator("#choose-file").is_visible() and denied_page.locator("#manual").is_visible()
    checks.append("camera_denied_keeps_file_and_manual_fallback")
    assert not errors,errors
    report = {"checks":checks,"browser_errors":errors,"camera":"fake Chromium device; physical camera not accessed",
              "real_yolo":True,"live_gemini":args.live_gemini}
    (root / "artifacts/camera-demo-browser.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,indent=2))
    browser.close()
