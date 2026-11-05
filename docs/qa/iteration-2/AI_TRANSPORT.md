# AI transport and durable reference checkpoint

This checkpoint adds the Android API and log-reference layer. The live scan flow is **not complete**: `CaptureSheet` still selects `UnconfiguredScanRepository` outside demo mode. No new AI APK or native visual acceptance is claimed here.

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

Wire the catalog and APIs through the scan repository, ViewModel and UI; collect the independent condition/access/risk fields; preserve server bands; handle missing context and conditional advice; show actual sources; transfer the reference to the editor without deleting the server detection before save; support explicit retry and cancellation/expiry handling. Verify the full public-backend flow on API 30 and the current Android runtime, inspect screenshots against S21–S25, produce a trial APK and pass exact-head GitHub CI. Broad screen/navigation acceptance remains open.
