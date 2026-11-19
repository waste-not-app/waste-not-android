# WasteNot: Implementation and Test Plan

Version 1.2 · 28 September 2026 · Core plus AI extension · Implementation design

## 1. Splitting the work

Assign owners for Android, backend and data, content and design, and QA and demo. One person can cover more than one role. Build one vertical flow with the real UI and API before finishing every screen in parallel. Every task has a result someone can check, not just a progress percentage.

Reference documents: [PRD](PRD.md), [API](API.md), [ARCHITECTURE](ARCHITECTURE.md), [SCREEN_SPEC](SCREEN_SPEC.md), [DESIGN](DESIGN.md).

## 2. Weekly backlog

### Week 1: foundations and authentication

| ID | Work | Depends on | Done when |
|---|---|---|---|
| W1-01 | Agree on units, what Reduce means, recycling rate, XP, and scope cuts | PRD review | The team's decision notes |
| W1-02 | Create the Android project and pin the toolchain and dependencies | W1-01 | The debug build and empty app run |
| W1-03 | Theme, fonts, typography, basic components | DESIGN | Light and dark previews + font scale |
| W1-04 | Five-tab navigation, auth root, Back | W1-02 | A recording of the flow and tab restore |
| W1-05 | Backend, DB migrations, debug env, health | API | Migrations rerun cleanly, readiness tested |
| W1-06 | Register, login, refresh, logout + token store | W1-05 | Real auth, and logout revokes the session |
| W1-07 | Screens S01 to S03 + validation and errors | W1-03,06 | Invalid credentials and offline don't crash |

Week 1 gate: one account can be created, signed in, signed out, and restored. The APK holds no backend secrets. Secrets never show up in logs. The theme and navigation work on the demo device.

### Week 2: complete tracking

| ID | Work | Depends on | Done when |
|---|---|---|---|
| W2-01 | Create log, unit constraints, idempotency | W1-05 | Replays and parallel requests with one ID create one record |
| W2-02 | Editor, unit parser, draft restore, feedback | W2-01 | g/kg/count input, retry without losing entries |
| W2-03 | List, detail, filters, pagination in API and UI | W2-01 | S07 to S10 use the user's data |
| W2-04 | Update, delete, version conflicts | W2-03 | Two editors on the same version trigger the right conflict |
| W2-05 | Dashboard aggregates and invalidation | W2-01,04 | The summary changes after create, edit, and delete |
| W2-06 | Account and cache isolation tests | Auth + all log APIs | Account B can't see account A's data |

Week 2 gate: the full create → read → update → delete flow works; restarting never loses saved logs; A and B data stay separate; the dashboard uses no hardcoded numbers.

### Week 3: guides and evaluation

| ID | Work | Depends on | Done when |
|---|---|---|---|
| W3-01 | Review 12 to 20 starting articles (proposed target) | Content sources | Real URLs and review dates |
| W3-02 | Guide search, list, and detail | W3-01 | Keywords and no-result cases tested |
| W3-03 | Statistics, daily series, categories and methods | W2-01 | The reference dataset produces the PRD's numbers |
| W3-04 | Chart + text alternative + 7/30 days | W3-03 | Screen readers get units and values |
| W3-05 | Baseline XP and levels | W2-04 | 9, 10, and 11 logs test the level boundary |
| W3-06 | Streaks and achievements once the core is stable | W3-05 | Empty days, backdating, delete and relock tested |

Week 3 gate: the demo from sign-in to statistics runs on a real server; guides are reviewed; numbers match across screens. If the core fails, week 4 focuses on fixing the core.

### Week 4: resilience and the demo

| ID | Work | Condition | Done when |
|---|---|---|---|
| W4-01 | Regression tests + high-priority fixes | Always | The test matrix has evidence |
| W4-02 | Accessibility, font scale, insets, themes | Always | TalkBack, keyboard, and 200% font checks |
| W4-03 | Demo build, staging config, backup and restore | Always | Artifacts and a restore procedure |
| W4-04 | Opt-in reminders | Only if the core gate passes | Permission denied, off, cancellation on logout |
| W4-05 | Photos | Only if there's capacity | Private upload and retry, not a placeholder |
| W4-06 | Maps | Last, and only with facility data | Verified facilities, a no-permission fallback |
| W4-07 | Presentation practice and recording limitations | Always | A demo recording and the final bug list |

Don't tick an optional checklist item as done just because a mockup exists. This schedule doesn't assume every stretch goal fits in one month.

## 3. Main acceptance tests

| ID | Scenario | What must happen |
|---|---|---|
| AT-01 | Register a new account and sign in again | The account is saved; the profile belongs to it |
| AT-02 | Duplicate email, short password, wrong password | The right error; no fake session is created |
| AT-03 | Add 250 g of Plastic, Recycle | One log, 10 XP, detail readable after a restart |
| AT-04 | Tap save twice / retry after a timeout | One record and 10 XP, not two |
| AT-05 | Weight of 0, negative, too large, more than 3 kg decimals | Rejected by client and server |
| AT-06 | COUNT of 2.5, or weight and count together | Rejected; no silent unit conversion |
| AT-07 | Add 2 items of Reduce | avoided_count goes up, handled totals stay the same |
| AT-08 | Only Reduce, no handling | Recycling rate is null, not 0, 100, or NaN |
| AT-09 | Mixed g and item data | Separate summaries; units are never added together |
| AT-10 | The 5-row dataset from PRD §9.4 | Today 650 g, period 1,400 g, 50%, 50 XP, streak 2 |
| AT-11 | Filter PLASTIC + RECYCLE | Only logs that match both |
| AT-12 | Change filters while an old request is still running | The old response never replaces the new result |
| AT-13 | Edit 250 g to 300 g | The total goes up 50 g, XP doesn't change |
| AT-14 | Delete one log | The record leaves the active set; XP -10; statistics and streak recalculated |
| AT-15 | Two updates with an old version | The second update gets 409; the user's draft stays |
| AT-16 | Read, PATCH, or DELETE another account's object | 404, data unchanged |
| AT-17 | Log out A, then sign in as B | A's cache, drafts, and stack don't appear |
| AT-18 | Access token expired, refresh valid | One refresh, and the request continues once |
| AT-19 | Refresh revoked or expired | Sign in again, no refresh loop |
| AT-20 | Search guides with different capitalization and no results | Consistent results and a way to clear the search |
| AT-21 | Switch the statistics range between 7 and 30 days | Title, dates, series, and summary all cover the same period |
| AT-22 | Exactly at the Asia/Jakarta day rollover | Today, streak, and ranges shift consistently |
| AT-23 | Rotation or process death on a draft | The non-secret draft comes back for the same user; the password doesn't |
| AT-24 | 200% font scale, TalkBack | Fields and CTAs are readable and reachable |
| AT-25 | Dark and light + landscape + keyboard | No missing text or covered buttons |
| AT-26 | 1,000 logs, pagination, a later page fails | The first data stays; retry doesn't duplicate items |

For date updates specifically: an old date that's kept may be outside the create window, but changing to a new date must stay within the last 30 days. Test this so corrections to old logs aren't blocked for no reason.

## 4. Optional feature cases

- Reminders: off by default, permission denied, turned on then logged out, day already logged, time changed, time zone stays consistent, no promise of an exact time.
- Photos: valid and invalid files, fake MIME types, files too large, image decoding limits, cancelling the picker, upload retries, unauthorized downloads, expired signed URLs, orphan cleanup.
- Maps: permission denied, GPS off, no facility data, stale location, provider failure, opening external navigation with the user's choice.
- Achievements: exact thresholds, delete causing a relock, repeated requests not duplicating unlock events, no scientific environmental value.

## 5. Just enough automated testing

Backend: domain tests for formulas and units, integration tests for authorization, idempotency, and transactions. Android: ViewModel tests for loading, save, error, and refresh, the amount parser, and one Compose UI journey of sign in → add → history with a fake repository. Separate end-to-end tests use real staging to prove the integration.

Don't write tests that just copy the implementation. Pick data that breaks assumptions: a zero denominator, Reduce only, mixed units, version conflicts, multiple accounts, and a POST whose response was lost. Charts don't need a screenshot test for every data point; check the structure and semantics and visually review the important states.

## 6. Device and environment matrix

| Configuration | Purpose |
|---|---|
| Main demo device, OS version recorded | Evidence for the presentation flow |
| Compact 360 dp emulator, normal font | Minimum density |
| Compact with 200% font | Text and form reflow |
| Landscape + keyboard open | Safe insets and scrolling |
| Tablet or a resize to at least 600 dp | Rail and width constraints |
| An API level with runtime notification permission | If reminders are built |
| Normal, offline, and slow or timed-out connections | Data resilience and feedback |

Fill in real results after testing. The documentation package doesn't set latency or jank numbers that haven't been measured.

## 7. Test record template

```text
Date:
Android build / commit:
Backend build / commit:
Device and OS:
API environment:
Test case:
Steps:
Expected result:
Actual result:
Evidence (screenshot / redacted log):
Status: NOT TESTED / PASS / FAIL
Issue and owner:
```

## 8. A 5 to 7 minute demo script (proposed)

1. Sign in with a test account and explain that logs are private.
2. Add one valid Recycle log; open the detail and history and show it persists after a refresh.
3. Add an item-based Reduce; show that it's kept apart from handled weight.
4. Open statistics and explain the recycling rate's denominator.
5. Search a guide, open its source, then show the log prefill.
6. Show XP and level, and one failure state that recovers without duplicates.
7. Sign out and sign in to another account to prove isolation.

Avoid demos with fake numbers presented as real user data. Test accounts and simulated logs are fine if they're identified as such. Be open about options that aren't built yet.

## 9. Release gate

- [ ] Every Must FR and the baseline XP and level are done, or there's an approved scope change.
- [ ] No data loss bugs, account leaks, blocking crashes, or duplicate requests.
- [ ] The API runs with TLS, and secrets aren't in the APK, Git, or logs.
- [ ] Guide content is reviewed, with real sources listed.
- [ ] Backup and restore are tested for the shared host.
- [ ] The build can be reinstalled and the demo flow repeated.
- [ ] The implementation README covers setup, actual versions, env names, and limitations.
- [ ] Test results come with evidence, not just an empty checklist.

A public launch needs extra work the demo doesn't cover: password reset, a privacy and retention policy, an account deletion flow, ongoing operations, a security review, and the requirements of the actual distribution channel.

## 10. The AI increment after the core (28 September revision)

The latest request covers computer vision, recommendations, and a mockup of the Log/Scan button on the navbar that opens the camera in a bottom sheet. The four weeks in §2 stay as the historical core plan; the extra AI estimate is agreed after a model and device spike, and isn't assumed to fit into week 4. Put AI ahead of Maps, reminders, and photo attachments if that's the team's demo focus.

| Stage | Owners | Result / gate before moving on |
|---|---|---|
| AI-01 Contract and taxonomy | AI + backend + content | Pick a candidate checkpoint, read its actual labels, audit dataset and weight licenses, set the pilot classes and capabilities |
| AI-02 Camera sheet and manual | Android + design | S21 capture, permission, close, Back; manual opens S05; the camera stops in the background |
| AI-03 Detection service | AI + backend | Multipart endpoint, bounded worker, preprocessing and boxes, TTL and cleanup, a baseline latency report |
| AI-04 Review and mapping | Android + backend | S22 multiple objects with one choice, uncertain and unknown, server-validated category correction |
| AI-05 Guides and context | Content + Android | S23 independent conditions, versioned PUBLISHED guides, unknown and no-guide fallback |
| AI-06 Recommendation | Backend + AI | Structured provider, schema + evidence + allowed methods + special handling, secrets and budget |
| AI-07 Log integration | Android + backend | S24 → S05 manual amount → create one log; provenance, statistics, and no double XP |
| AI-08 Evaluation and pilot | QA + all owners | Per-class holdout report, safety and fallback, privacy, cost, devices; a written go/no-go decision |

Fine-tune after the baseline taxonomy, then benchmark on-device and quantization if latency, cost, or privacy really call for it. Neither is a reason to delay the manual fallback. Camera scanning and photo attachments are separate work.

### Extra acceptance tests for the implementation

| ID | Scenario | Required result |
|---|---|---|
| AT-AI-01 | Tap the center dock action from each of the five tabs | One camera sheet; the previous tab stays; close returns focus |
| AT-AI-02 | Permission denied or permanently denied, no camera | A relevant message and manual entry; no repeated prompts |
| AT-AI-03 | Capture, review, retake, cancel | Nothing uploads before confirmation; temporary files are cleaned up |
| AT-AI-04 | Rotation, background, or process death with the camera on | No camera leak, the draft comes back, recapture if the file is gone |
| AT-AI-05 | Many boxes | One explicit choice; no auto-count or batch save |
| AT-AI-06 | Thresholds 0.49, 0.50, 0.699, 0.70, unknown | States match the candidate thresholds; no claims of certainty |
| AT-AI-07 | The bottle class without a material | Confirmation required; no automatic mapping to PLASTIC |
| AT-AI-08 | Object or condition changes during an LLM request | The old result is ignored and the recommendation reruns with the new context |
| AT-AI-09 | Invalid LLM JSON, enum, or evidence | Null method and fallback; never saved as a decision |
| AT-AI-10 | Batteries, sharp items, special risks | Policy override, null method, curated guide, manual available |
| AT-AI-11 | Reduce from a photo of waste | Never recommended automatically; a manual path for prevention activities |
| AT-AI-12 | Network, timeout, overload, budget used up | The draft stays, no retry loop; manual tracking works |
| AT-AI-13 | Accept or change the advice, enter 250 g, save twice | One final log, 10 XP; confidence is never used as a statistic |
| AT-AI-14 | Cross-owner, expired, or already linked recommendation | The right 404/410/409, no cross-account access, an explicit manual option |
| AT-AI-15 | Fake files, oversize, orientation, invalid boxes | Correct validation, no crash or decompression bomb, aligned overlay |
| AT-AI-16 | Cleanup on success, failure, disconnect, crash | No raw images in logs or backups; the scavenger meets the retention limit |
| AT-AI-17 | TalkBack, 200% font, keyboard, small landscape | The object list can be chosen without boxes; CTAs and manual are reachable |
| AT-AI-18 | AI capability off | Manual logging still works from the main action; auth and history unaffected |

### An extra 2 to 3 minute AI demo

From the navbar, open the camera sheet, take a photo, confirm sending it, choose or correct the object, fill in the condition, check the sources, accept or change the method, enter the amount yourself, save, and show one record and the XP change. Repeat with the camera denied or an unsure model to prove the manual fallback. If you're only using the HTML, say the detection and recommendation results are simulated fixtures; never present the demo as real inference.

### AI gate before the pilot

- [ ] License, taxonomy, model, mapping, provider, and guide versions are recorded.
- [ ] Holdout evaluation and pilot class targets are agreed; no accuracy numbers are assumed yet.
- [ ] AT-AI-01 to 18 have results and evidence from a real implementation.
- [ ] Camera, upload, and cleanup are tested on Android devices; web tests don't replace that.
- [ ] Special handling policy, invalid output rejection, and the manual fallback pass.
- [ ] Budget, rate limits, timeouts, observability redaction, and rollback are tested.
