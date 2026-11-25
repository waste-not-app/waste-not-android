# Offline Activity and Synchronization

Activity is always read from Room, even when the network is available. Creates, updates, and deletes write the local change and a queued operation in one transaction, and WorkManager sends the queue when the network returns. Guides and AI features remain online only.

## Storage and accounts

`activities.db` stores the following, each owned by a specific account:

- activities;
- the operation queue;
- create receipts;
- server snapshots;
- the last verified account identity.

Access tokens, refresh tokens, and passwords never enter the database or worker input. The refresh token stays encrypted with the Android Keystore.

- **Offline sign-in.** A previously verified account can open its local data offline while its Keystore credential exists. This never produces an access token or calls authenticated APIs. Once online, the app runs refresh and `/me` to verify the account before syncing. The first sign-in needs a connection.
- **Sign-out.** Removes the active identity and credentials but keeps that account's queue, so it can resume when the same account signs in again. The sign-out dialog explains this. A worker for account A never runs as account B.
- **Backups.** The database and credentials are excluded from backups and device transfers.
- **Account deletion.** After the server confirms `DELETE /me` and the account's sessions and work are stopped, `ActivityDao.clearOwner` removes that owner's data in one transaction. Other owners' data is untouched. This never runs on a normal sign-out.

## Change flow

1. **Create.** Stores the request UUID, the original payload, a local identity, and a `CREATE` operation. The receipt keeps the UUID and payload stable across lost responses and app restarts.
2. **Edit and delete.** These queue behind earlier operations on the same activity. The local identity still resolves after the server assigns its own ID. The UI uses local revisions, while requests use the stored server version.
3. **Sending.** The request version is frozen in the queue.
   - A lost `POST` response is retried with the same UUID and payload.
   - A lost `PATCH` response counts as accepted only if a `GET` shows exactly the next version with identical fields.
4. **Acknowledgements.** Acknowledging an older operation never overwrites fields that a newer local operation changed. Local deletions are hidden from history until the server acknowledges them.
5. **Failures.** Network, server, and rate-limit errors use WorkManager retry and backoff. Permanent failures and conflicts are stored for the user to review.

Work uses a unique name per account with `APPEND_OR_REPLACE`, so changes made while a worker is finishing still get their turn. Each worker run syncs every operation available when it starts, then pulls the latest server state.

## Conflicts and AI provenance

- **Conflicts.** A `409` never overwrites the server or discards local fields. The sync panel shows the local fields next to the server version it last read. The user can confirm a resend based on that version or discard the local change, and is asked to review again if the server changes once more. Conflicting deletes appear in the panel even though they are hidden from history.
- **Expired AI references.** If an AI reference has expired or become invalid, the activity fields are kept. The user may explicitly save the activity as a manual entry. That conversion uses a new UUID after a definitive server rejection, drops the AI provenance, and never claims the recommendation is still valid.

## Reads, summaries, and cache limits

- **History.** History pages 20 rows at a time from Room, with category, method, and date filters.
  - Background sync pulls server pages of 20 rows at a time.
  - History is complete only after the first pull finishes, and the UI says so until then.
  - Data that was never downloaded is not available offline.
- **Pull safety.** A pull never overwrites activities that have pending local changes. Server pagination is not a snapshot, so an activity missing from a pull is checked with a `GET` before it is removed from the cache. An interrupted pull never deletes unseen cache entries.
- **Summaries.** Summaries, statistics, XP, and streaks use the last snapshot the server confirmed. Recent activity on the home screen comes from Room, and saving locally never announces new XP. The sync panel explains the difference between local changes and server summaries.
- **Snapshot staleness.** Snapshots carry their server confirmation time and a cache marker.
  - Home shows the cache marker in the summary's info disclosure. Profile and Statistics show it inline. Previous-day or previous-period data is still flagged rather than presented as current.
  - Refresh failures are stored separately from the payload, so an old snapshot stays readable without hiding the error.
- **Achievement refresh.** Each acknowledged create, update, or delete stores a random revision in the snapshot metadata within the same transaction.
  - `ActivityStore.synchronizationAcknowledgements` replays the latest revision for the account, deduplicated.
  - `AchievementsViewModel` reloads achievements when that revision changes. XP is never computed locally.

## Verification

**Host tests.** Room host tests cover:

- reopening the database file;
- retries after lost responses;
- create, edit, and delete ordering;
- protection of local changes;
- filters and pagination;
- receipts;
- conflicts;
- owner isolation.

Session tests check that an offline identity never yields API credentials and that sign-out clears the active identity.

**Device tests.** Device tests run only on a physical phone approved by the owner.

- `OfflineActivityDeviceTest` is opt-in through `verifyOfflineLive=true`. It creates a separate backend account, uses a test-only database and Keystore alias, and cleans up its records. Transport failures are simulated with a closed loopback socket so wireless ADB stays connected; this is not an airplane-mode test.
- `ActivitySyncUiTest` checks the local-save message and the conflict decisions, and captures screenshots.

**Schema changes.** The Room schema is exported to `app/schemas/`. Every schema change needs a migration that preserves the queue. Never add a destructive fallback migration.

**Side-by-side QA build.** When the installed app uses the release key, build the debug APK and `debugAndroidTest` with `-Pwastenot.debugApplicationIdSuffix=.offlineqa`. They install alongside the main app as "WasteNot QA", so the main app and its data stay intact.

**Process-death test.**

1. Run the persistent-offline test with `offlinePhase=prepare` and an `offlineRunId` UUID.
2. Keep the report at `cache/qa/offline-sync/device.json`.
3. Kill only the `.offlineqa` process.
4. Rerun the test with `offlinePhase=recover`, the same `offlineRunId`, and `offlineRequestId` set to the report's `local_id`.

The prepare phase keeps the test database and credentials; the recover phase restores and then cleans them up. Never run production WorkManager tests against the main app package; they require the `.offlineqa` suffix.
