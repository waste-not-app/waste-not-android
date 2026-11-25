# Network Layer

References: [API specification](design-kit/API.md) and [architecture §8](design-kit/ARCHITECTURE.md).

`WasteNotNetwork` provides:

- one OkHttp client;
- one kotlinx.serialization converter;
- the Retrofit API;
- an executor that returns typed results.

`CoreRepository` maps `snake_case` DTOs to domain models. An empty base URL yields `NOT_CONFIGURED` instead of falling back to a production address.

## Rules

- **Credential scoping.** Bearer tokens attach only to the API's scheme, host, port, and path prefix. Caller-supplied `Authorization` and `Cookie` headers are stripped from any other request.
  - Login, registration, refresh, and both password recovery endpoints never carry an old bearer token.
  - Google challenge issuance and token exchange also carry no bearer. The Google ID token is kept only in memory for its one exchange and is not used as a WasteNot bearer.
  - Automatic redirects are disabled. Source links open through the external link flow, never through an authenticated endpoint.
- **No body or header logging.** The optional diagnostic hook receives only the status, duration, and a sanitized request ID. It never sees URLs, exception messages, passwords, tokens, or payloads.
- **Typed failures.** Repositories return either a success with pagination or an `ApiFailure`. The failure keeps the status, code, field errors, request ID, and retry deadline.
  - Technical server messages are never shown to users.
  - Field errors on `409` and `422` map to forms and resources.
  - `5xx` responses use a generic failure.
- **Rate limits.** A `429` `Retry-After`, given in seconds or as an HTTP date, blocks further API requests until the deadline. The result reports the rate limit so the UI can show the wait. There is no retry loop.
- **Distinct error kinds.** Timeouts, network failures, malformed responses, and cancellation are reported separately, and cancellation propagates to the caller. `SessionRepository` handles `401` refreshes one at a time; the HTTP executor never retries writes on its own.
- **Validation.** Metadata and capabilities are display data, not authorization. Missing or disabled capabilities read as `false`. Responses with invalid enums, limits, quantity combinations, time zones, dates, or pagination are rejected as malformed.
- **One-shot writes.** `POST`, `PUT`, `PATCH`, and `DELETE` bodies are marked one-shot on every call, including empty deletes, so OkHttp never replays them on a `503` or `421`. Any retry must be an explicit repository call with a known payload and ID.

## Contract fixtures

Shared fixtures live with the tests so the backend can use the same files:

- [`metadata.json`](../app/src/test/resources/contracts/metadata.json)
- [`waste-logs-page.json`](../app/src/test/resources/contracts/waste-logs-page.json)
- [`validation-error.json`](../app/src/test/resources/contracts/validation-error.json)

- **Enums.** Categories and methods are arrays of enum strings. A new enum value requires an app update.
- **Limits.** `limits` holds `{min, max}` objects for `weight_g`, `item_count`, `name_length`, and `password_length`, and integers for the other limits. Server limits may only tighten the PRD baseline. Forms use the server limits, while the model parsers still enforce the canonical maximums.

## Account recovery and deletion

- **Password reset.** `RemoteAccountManagement` calls `POST auth/password-reset` and `POST auth/password-reset/confirm` without session credentials.
  - The `202` response acknowledges the request; it does not prove an email was sent.
  - The recovery code is pasted into the app, not opened from a link.
  - Passwords are never trimmed, and DTOs and state redact secrets from `toString`.
- **Account deletion.** `DELETE me` sends the current password, or a fresh Google ID token for a passwordless account, with the account's frozen WasteNot credentials.
  - `403 WRONG_PASSWORD` is not a session expiry, so local data stays available.
  - `403 GOOGLE_REAUTH_FAILED` also leaves the account and local data intact.
  - `204` revokes the session and clears that account's local data: Room, outbox, receipts, snapshots, identity, drafts, and scan cache. A normal sign-out keeps the queue, as described in [OFFLINE_SYNC.md](OFFLINE_SYNC.md).
- **Pending deletion.** `503 ACCOUNT_DELETION_PENDING` means the server committed the deletion. The app clears the local account and signs out, noting that server-side attachment cleanup is still pending. A regular `503` or a network failure is never treated as a confirmed deletion. Local cleanup failures get their own notice, and the app never claims that every physical copy or backup is gone.

## Tests

```sh
./gradlew :app:testDebugUnitTest --tests 'com.example.wastenot.data.network.*'
```

The tests cover:

- data, pagination, and error mapping;
- metadata;
- malformed responses;
- credential scoping and redirects;
- `429` deadlines;
- timeouts and cancellation;
- the guarantee that writes are never replayed.

A regression test shows that `POST`, `PUT`, `PATCH`, and `DELETE` are each sent exactly once after a `503`. The fixtures use simulated data and fake credentials.
