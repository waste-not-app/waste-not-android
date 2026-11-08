# AI transport and durable reference checkpoint

This records the API/log-reference checkpoint at `e5fc51b`. The following [UI checkpoint](UI_SCAN_PREVIEW.md) wires `RemoteScanRepository` through AppContainer and CaptureSheet and adds the catalog/context/recommendation UI. The [live Android checkpoint](AI_LIVE_ANDROID.md) now verifies real YOLO/Gemini, provenance, disk restoration and create replay at the model/transport seam on API 37. The camera/navigation journey and broad native acceptance remain open; the initial UI preview uses explicit demo data.

## Implemented

- Authenticated catalog, JPEG multipart detection, recommendation and detection deletion share the core HTTP client and session handling. Uploads carry an explicit request UUID; the transport does not automatically generate another operation after a failure.
- Detection responses preserve server confidence bands and per-class thresholds. Raw detector classes cannot prefill a confirmed material. Duplicate/foreign object references, contradictory status/band metadata, invalid bounds and expired responses are rejected.
- Recommendation responses must match the requested detection, object, category, object key and context revision. READY, NEEDS_CONTEXT, conditional advice, special handling, source metadata and prototype results remain distinct. Special handling alone does not remove a valid method; prototype output cannot become live provenance.
- Core create accepts the confirmed recommendation reference; PATCH rejects this create-only input. Method corrections retain the reference. A category change or Reduce action requires an explicit switch to manual entry.
- Editor snapshots and account-bound disk drafts retain the exact reference through an uncertain save, process recreation and explicit replay. Unknown outcomes keep the payload locked, even if a later retry reports AI expiry. Existing manual snapshot formats remain readable.

## Verification

`testDebugUnitTest` and `lintDebug` passed with 229 tests, 0 failures/errors/skips before the final pre-commit check. New coverage includes 11 AI HTTP cases, 4 editor cases, 1 disk-restoration case and 1 core create/replay case. The HTTP fixtures are copied verbatim from the committed AI contract examples; their hashes and prototype status are recorded in `app/src/test/resources/ai-contract/README.md`. The catalog example contains 78 items; this is a fixture count, not a live catalog measurement.

The mandatory pre-commit hook also runs `lintDebug test`. The local `test --dry-run` task graph resolves debug unit tests; this is separate from CI building debug/staging/release APKs. The hook result must be checked on the actual commit. These checks do not establish a native camera/upload flow, a production Gemini result, visual fidelity or release readiness.

## Remaining for the scan checkpoint

Source wiring, independent context fields, server bands, missing-context/conditional/source rendering, explicit retry and detection-reference handoff are implemented. API 37 live verification covers the model/transport path, including deletion of the parent detection after a successful core write and replay from a restored locked draft with simulated acknowledgement loss. Finish the camera/UI path, API 30, actual interrupted-network/cancellation/expiry UI, account transitions, broad screen/navigation acceptance and exact-head CI for the resulting live APK.
