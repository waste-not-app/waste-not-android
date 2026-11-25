# Agent Guide: WasteNot Android

Instructions for AI coding agents working in this repository. Read this file before writing or changing code.

## Project

WasteNot is an Android app for logging personal waste handling through Reduce, Reuse, Recycle, and Trash, with AI-assisted waste detection backed by YOLO and an LLM recommendation pipeline.

- **Code layout.** One activity, one Gradle module (`:app`), package `com.example.wastenot`.
- **Backend.** Every build uses the real backend and real accounts. A fresh install opens sign-in or registration; authentication and activity data are never generated locally.
- **Test data.** Fixtures live only in test source sets.
- **API address.** Every variant defaults to `https://waste-not-api.bccdev.id/v1/`.

## Specifications

The product and design specification is the design kit v1.4 in [`docs/design-kit/`](docs/design-kit/README.md). Read the relevant document before you write UI, API client, or domain code.

| Task | Read first |
|---|---|
| UI, screens, components | [`DESIGN.md`](docs/design-kit/DESIGN.md), [`SCREEN_SPEC.md`](docs/design-kit/SCREEN_SPEC.md) |
| Theme, color, typography, spacing | `DESIGN.md` §2 to §4 |
| Navigation, routes, back stack | [`ARCHITECTURE.md`](docs/design-kit/ARCHITECTURE.md) §6, `SCREEN_SPEC.md` §2 |
| ViewModels, state, repositories | `ARCHITECTURE.md` §3 to §5 |
| Networking, Retrofit, DTOs | [`API.md`](docs/design-kit/API.md), `ARCHITECTURE.md` §8, [`docs/ANDROID_NETWORK.md`](docs/ANDROID_NETWORK.md) |
| Domain rules (XP, recycling rate, Reduce) | [`PRD.md`](docs/design-kit/PRD.md) §6 to §9, [`docs/ANDROID_MODELS.md`](docs/ANDROID_MODELS.md) |
| Authentication, tokens, sessions | `ARCHITECTURE.md` §8, `API.md` §3 |
| Offline reads, the mutation queue, conflicts | [`docs/OFFLINE_SYNC.md`](docs/OFFLINE_SYNC.md) |
| Accessibility, font scale, insets | `DESIGN.md` §9, `ARCHITECTURE.md` §7 |
| AI scan, camera, YOLO | [`AI_SPEC.md`](docs/design-kit/AI_SPEC.md), `SCREEN_SPEC.md` §3.21 to §3.25, `ARCHITECTURE.md` §13 |
| AI endpoints | `API.md` §10, `AI_SPEC.md` §6 |
| Photo privacy and cleanup | `AI_SPEC.md` §8 |
| Acceptance criteria | [`IMPLEMENTATION_PLAN.md`](docs/design-kit/IMPLEMENTATION_PLAN.md) §3, §4, and §10, [`QA.md`](docs/design-kit/QA.md) |
| Dev plugins and the navigation graph | [`docs/DEV_PLUGINS.md`](docs/DEV_PLUGINS.md) |
| CI/CD and releases | [`docs/CICD_SETUP.md`](docs/CICD_SETUP.md), `.github/workflows/` |

For an interactive visual reference, including the AI scan flow, open [`docs/design-kit/design-system.html`](docs/design-kit/design-system.html) in a browser.

## Stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose with Material 3; dynamic color disabled |
| Navigation | Navigation 3 (`androidx.navigation3`) only |
| State | ViewModel with StateFlow and unidirectional data flow |
| Network | Retrofit and OkHttp |
| Camera | CameraX |
| Storage | Room for account-scoped activity, outbox, and snapshots; DataStore for the theme; SharedPreferences for drafts; Android Keystore for the refresh token |
| Dependency injection | Constructor injection through a simple `AppContainer` |
| Build | AGP 9.5.1, Gradle 9.9.1, JDK 17, Compose BOM 2026.09.00 |
| SDK | Min 30 (Android 11), compile 37, target 36 |

## Rules

### Design and UI

- **Appllama.** For Android UI audits, design, and implementation, load `appllama-usage` and `appllama-app-design-skill` before proposing changes. Study comparable screens and flows; use public references if MCP is unavailable and document the limit. Apply the patterns in Compose, Material 3, and Navigation 3 using the WasteNot design kit. Follow the device restrictions below.

- **Design tokens.** Take colors, typography, and spacing from `DESIGN.md` §2 to §4. Never hardcode hex colors in composables. The theme is `WasteNotTheme`.
- **Font.** Plus Jakarta Sans only.
- **Layout.** Touch targets are at least 48 dp. Forms must never be covered by the keyboard. Layouts are edge-to-edge with correct insets.
- **Navigation dock.** The dock uses the glass style from `DESIGN.md` §2, and the opaque fallback must still work.
- **Text.** All user-facing text goes in string resources. English lives in `values/` and is the default; every string also needs its Indonesian translation in `values-in/`. Format dates and numbers with the device locale.
- **Content descriptions.** Every action icon needs a `contentDescription`. Use `null` only for decorative icons that already have a text label.

### Domain

- **Reduce.** Reduce counts items avoided (`avoided_count`). It never adds to the total waste handled.
- **Recycling rate.** RECYCLE activities divided by REUSE + RECYCLE + TRASH activities, times 100. A zero denominator gives `null`.
- **XP.** 10 XP per active log, removed again when the log is deleted. Derive it from active logs, never from a stored cache.
- **Units.** Weight is in whole grams, and item counts are integers. Never convert counts to kilograms.
- **Server numbers.** Key figures come from the server; never recompute them independently on the client.
- **Impact claims.** Never show CO₂ figures or "verified impact" claims. WasteNot tracks habits; it does not claim scientific impact.

### AI

- **Decision support only.** The user must confirm before a log is saved. Never save a waste log automatically.
- **No Reduce from scans.** Never recommend REDUCE from a photo scan. Reduce is available only through manual entry, without `ai_provenance`.
- **Scan entry point.** The Log/Scan button sits at the top center of the dock; it is not a sixth tab. The five tabs stay Home, History, Guide, Statistics, and Profile.
- **Scan sheet.** A scan opens a multi-step `ModalBottomSheet` (S21 to S25), not a new route.
- **Scan photos.** Delete temporary scan photos when the flow exits, on logout, and after one hour at the latest. Keep a photo only when the user opts in to an attachment.
- **LLM data.** Never send photos, names, emails, locations, or tokens to the LLM. Only `object_key`, the category, the condition context, guide excerpts, and the allowed methods may be sent.
- **Output validation.** The server validates every LLM output. An invalid `recommended_method` returns `UNAVAILABLE` with a `null` method, and the user picks one manually.
- **Confidence bands.**
  - At least 0.70: may be offered.
  - 0.50 to 0.69: show as uncertain.
  - Below 0.50: manual selection required.
- **Scan state.** `ScanViewModel` holds only immutable state and never holds an Activity, a Context, or a camera reference.

### Architecture

- **Code structure.** Organize code by feature. Do not add feature modules, a generic `BaseViewModel`, or one use case per endpoint.
- **ViewModels.** Never hold a Context, Activity, or NavController.
- **Routes.** Routes carry only primitive or serializable IDs.
- **State ownership.** Screen data and queries belong to account-scoped state holders. Scroll state and sheet drafts stay in composition.
- **Credentials.** Passwords live in memory only while the form is open. Access tokens stay in memory. Refresh tokens use Keystore-backed storage that is excluded from backups.
- **Writes and retries.** Every write carries a `client_request_id`, so retries never create duplicates. Network errors never discard data that is already on screen. Never show success before the server confirms it.
- **Logging.** Interceptors log only the status, duration, and request ID. Never log the `Authorization` header or personal payloads.
- **Configuration.** Never hardcode the API base URL in composables or ViewModels.

### Workflow

- Work on feature branches and use Conventional Commits without agent signatures.
- Do not access emulators. Device checks wait until the owner connects a physical phone and asks for them. Host unit tests, database tests, and builds may run without a device.
- **Comments.** Comment only on what the code cannot express. No narrating comments.
- **Visual evidence.** Render checks and backend integration checks are different kinds of evidence. Neither replaces a screenshot comparison with the design kit.
- **Third-party branding.** Never reuse branding from the unrelated example project in `docs/archive/`.

## Commands

```sh
make check                           # lint and unit tests
make check-ci                        # full CI gate
./gradlew assembleDebug              # build the debug APK
./gradlew :app:exportNavGraphHtml    # export the interactive navigation graph
./gradlew :app:navLint               # find unreachable destinations
./gradlew :app:navCheck              # compare the navigation graph with the baseline
```

## Maintainer skills

On the maintainer's machine, read the matching skill before starting this kind of work:

| Area | Skill |
|---|---|
| Android architecture and delivery | `/home/oktaavsm/.codex/skills/android-dev/SKILL.md` |
| Jetpack Compose | `/home/oktaavsm/.codex/skills/android-jetpack-compose-expert/SKILL.md` |
| State ownership | `/home/oktaavsm/.agents/skills/compose-state-hoisting/SKILL.md` |
| Kotlin Flow state and events | `/home/oktaavsm/.agents/skills/kotlin-flow-state-event-modeling/SKILL.md` |
| UI, screenshot, and navigation tests | `/home/oktaavsm/.agents/skills/compose-ui-testing-patterns/SKILL.md` |
| Lifecycle effects and timers | `/home/oktaavsm/.agents/skills/compose-side-effects/SKILL.md` |
| Android tools | `/home/oktaavsm/.skills-library/catalog/android-cli/SKILL.md` |
| Debugging | `/home/oktaavsm/.skills-library/catalog/systematic-debugging/SKILL.md` |
| UI filter | `/home/oktaavsm/.agents/skills/antislop/SKILL.md` |
| UI conventions | `/home/oktaavsm/.skills-library/catalog/ui-skills/SKILL.md` |
| Implementation and tests | `/home/oktaavsm/.skills-library/catalog/clean-code/SKILL.md` |
| Self-review | `/home/oktaavsm/.skills-library/catalog/code-reviewer/SKILL.md` |

Skill examples never justify downgrading dependencies, migrating navigation, or adding unverified stability annotations.
