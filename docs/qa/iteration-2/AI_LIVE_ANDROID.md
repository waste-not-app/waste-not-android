# Android and live AI checkpoint

7 November 2026. This follows the [initial screen preview](UI_SCAN_PREVIEW.md). Android calls the public Go API at `https://pred-autoscaler.bccdev.id/wastenot/api/v1/`; it does not receive worker tokens or Gemini keys. Go and AI now live in separate repositories. Six Android HTTP examples were copied byte-for-byte from AI commit `3a70c89` and checked against the Go contract pin at `9d00e4a`; the paired checker passed all 34 artifacts.

## Verified model, transport and persistence on API 37

`RemoteAiLifecycleTest` passed on an owned API 37 emulator against the public HTTPS service, with dummy data and hot reload disabled. The [manifest](ai-live/api37-model/build.json), [native log](ai-live/api37-model/native.log), [step evidence](ai-live/api37-model/native-fixture.json), [backend readiness identity](ai-live/api37-model/readiness.json) and [database verification/cleanup](ai-live/api37-model/result.json) bind the actual APKs and sources. Go identified the live release as `9d00e4a2a92344ca9d92f762c42cec7c3da10b70`.

The test exercises the production photo cache, remote scan repository and ScanViewModel, followed by core storage and the account-bound disk draft store. Test calls model actions directly; this is not yet evidence of interacting through the camera/editor navigation UI.

- A fresh fixture account reads live capabilities and the four PUBLISHED pilot object keys.
- The production photo cache normalizes a [public PET photograph](../../../app/src/androidTest/assets/ai/README.md) before a real YOLO upload. Material and object key remain empty until explicit confirmation.
- On this image, baseline COCO actually predicts **vase, UNCERTAIN, confidence 0.57813048**, model `candidate-0ebbc80d4a76`. The test preserves this result and explicitly corrects the material/type to PET. It does not require or claim correct automatic bottle recognition.
- Unknown former contents produces NEEDS_CONTEXT; returning to conditions preserves the requested field. An explicit food/drink fixture answer obtains READY/RECYCLE from Gemini, `prototype_only=false`, guide `pilot-pet-bottle:6`.
- Replaying the recommendation retains the result; scan/advice alone leave logs and XP at zero.
- Handoff deletes the local photo while retaining the server reference. The editor starts with a blank quantity; explicitly filling 250 g and saving creates one log and 10 XP. A second create UUID cannot reuse the recommendation.
- After the actual server create, the test **simulates losing its acknowledgement** by setting the client timeout state, persists/restores the locked draft and deletes the detection. Replaying the original create UUID/payload still returns the same log without another XP award. This proves the live provenance/replay contract with simulated client acknowledgement loss, not an actual network interruption.
- Delete returns active logs/XP to zero. The host verifies the owned audit snapshot still holds Gemini, prototype false and the actual YOLO checksum, then deletes only the prechecked fixture account. Users, sessions, refresh tokens, logs, AI operations and metadata for that fixture are all zero afterwards.

Condition answers and local receiving access are synthetic test context. This single sample does not establish model accuracy, local facility acceptance, content safety review or a latency percentile. The photograph is CC0; credit and the original checksum are in the asset README.

## Release configuration

The previous publication command inherited demo adapters and allowed an empty API origin. The working change explicitly passes `wastenot.useDummyData=false` and `wastenot.hotReload=false`, and refuses an empty `WASTENOT_RELEASE_API_BASE_URL`. The repository variable was absent; it was set to the public HTTPS origin and read back. This configuration change did not trigger a deployment.

The actual release shell step passed two invocation cases (configured origin/remote adapters and missing-origin refusal), alongside five packaging/version/input cases. Signed publication still requires the resulting commit's CI and main/tag/manual release run.

## Still open

The camera/editor navigation journey with real services is being verified separately, including scan light/dark and configuration changes. API 30, interrupted-network/cancellation/expiry UI, account transitions, broader accessibility/device/font coverage and the remaining visual-state acceptance are not proven by this model test. General guide articles remain a separate Go catalog, empty at the last backend check; the four AI policies do not fill that catalog.
