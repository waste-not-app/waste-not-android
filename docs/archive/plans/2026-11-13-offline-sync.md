# Offline activity synchronization implementation plan

**Goal:** Read and edit WasteNot activities without a connection, persist every local change in Room, and synchronize safely with the real account/backend after reconnection.

**Architecture:** Account-owned Room records and a transactional outbox are the application read source. Network refreshes reconcile server records without overwriting queued local changes. WorkManager resumes authenticated synchronization; stable request IDs prevent duplicate creates, and version conflicts remain visible until the user resolves them. Server progress snapshots retain their confirmation status.

**Tech stack:** Existing Kotlin/Compose/Navigation 3, Room with KSP, WorkManager, Retrofit and Android Keystore. Minimum API30. No new UI framework or backend replacement.

## Work

1. Reconcile organization issue status against current engineering evidence; retain incomplete device, human-content and model acceptance separately.
2. Add account-owned database entities, DAO transactions, exported schema and converters. Persist confirmed records, pending mutations, mapping of local/server IDs and summary snapshots. Protect pending rows during paged server refresh.
3. Implement offline create/update/delete and ordered synchronization. Handle ambiguous create responses, process restart, dependent edits/deletes, backoff, revoked sessions and explicit version conflicts. Never silently overwrite server changes or resend a new create UUID.
4. Restore only previously verified account identity while offline, retain pending work per owner, and prevent one account's worker from executing under another account. Keep passwords/tokens out of Room and worker inputs.
5. Integrate observable history/detail and pending/conflict feedback. Distinguish local save from confirmed server save and XP. Preserve drafts, filters, account-day behavior and scan provenance rules.
6. Add host-side database/repository/sync tests and run appropriate lint/build/contract checks. Repair dependency download configuration if needed. The owner has now authorized the connected physical phone. Run the scoped integration/UI checks on that phone; no emulator access.
7. Review, commit, open PR, check exact-head CI, merge safe changes and update all affected issue/board status.

## Acceptance

- Cached records remain readable after process restart offline.
- Offline create/edit/delete survives restart and reconnects without duplicate records or XP awards.
- Multiple operations on one record preserve order; unresolved 409 retains local input and offers explicit resolution.
- Refresh cannot overwrite queued changes or resurrect a pending deletion.
- Logout/account switch never leaks records or replays another owner's mutations; expired credentials preserve owned changes for reauthentication.
- Network data, saved progress and queued local work are represented distinctly in the UI.
- No passwords/access/refresh tokens stored in Room, no dummy product adapters, no provider keys/worker endpoints in Android.
- Host tests/build/CI evidence and the exact scope of physical-phone acceptance are recorded honestly.
