# WasteNot: Product and Design Kit

Product and design kit v1.4 · 13 November 2026

Start by opening **[design-system.html](design-system.html)** in a browser. There's no build step, nothing to install, and no internet connection needed. Keep the CSS, JavaScript, and `assets` folder next to the HTML file, and if you got the kit as a ZIP, extract everything first.

## What's in the kit

| File | Contents |
|---|---|
| [AI_SPEC.md](AI_SPEC.md) | The YOLO, guide, and LLM pipeline: datasets, validation, privacy, scope, and evaluation |
| [sources/AI_PLAN_ORIGINAL.md](sources/AI_PLAN_ORIGINAL.md) | A copy of the team's original plan, kept as a source rather than the final implementation spec |
| [PRD.md](PRD.md) | Full requirements, scope, validation, metric rules, acceptance criteria, and open decisions |
| [DESIGN.md](DESIGN.md) | WasteNot's identity, light and dark colors, type, spacing, components, accessibility, and Compose handoff |
| [design-system.html](design-system.html) | Visual foundations, 39 components, 25 screens and panels, and an interactive prototype |
| [SCREEN_SPEC.md](SCREEN_SPEC.md) | Screens S01 to S25, dialogs D01 to D04, components C01 to C39, with their fields, actions, and states |
| [ARCHITECTURE.md](ARCHITECTURE.md) | The Android Compose app, state and navigation, backend, and infrastructure |
| [API.md](API.md) | The data model and the proposed API contract |
| [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) | A four-week backlog and the app's test scenarios |
| [QA.md](QA.md) | Results of checking the HTML package, and the limits of that check |
| `assets/fonts/` | The Plus Jakarta Sans variable font and its SIL OFL license |
| `verify-ai.cjs` | Tests for the scan sheet, virtual camera, result correction, failure cases, and manual fallback |
| `verify.cjs` | Automated recheck using Node.js and Playwright |

## How to review the mockup

1. Open Visual foundations to see the palette, type, spacing, and radii.
2. Try the sample controls under Components.
3. Open All screens for the full gallery. Gallery previews are static; "Try screen" opens the interactive version.
4. In Try the flow, pick a screen and a state. Try adding, editing, and deleting a log, searching guides, filtering, and changing the statistics range.
5. Switch the theme from the docs navigation. Use Reset demo data to go back to the starting data.

The demo date is fixed at **21 September 2026** so the sample statistics match the PRD: five activities, 1,400 g handled, two items avoided, a 50% recycling rate, and 50 XP. "Today" in the demo shows 650 g handled. The date doesn't follow your device clock.

Prototype data lives in memory and disappears when you reload. The gallery and cover are snapshots of the starting data, so they don't change when you edit the prototype. The numbers on the Home, History, Statistics, and Profile prototypes update together. Don't enter real credentials or personal data.

## Chosen design direction

Pine green for actions, lime for progress, a green-tinted white background, and Plus Jakarta Sans. The design is built specifically for logging and waste habits; it doesn't reuse the colors, fonts, or marketplace UI from the Titip.in example.

- **v1.1** added the pine-to-lime gradient, soft highlights, and the floating glass navigation dock the user asked for.
- **v1.2** added the Log/Scan action at the top center of the dock and a camera bottom sheet with a manual fallback.

The decisions, the opaque fallback, and the Compose handoff are in DESIGN.md.

## Trying the AI scan

Open Home in Try the flow and tap Log/Scan at the top center of the dock. The bottom sheet offers Turn on camera, Take photo, a simulated scan, and Log manually. Use the simulated sample to try it without a camera.

Pick a scenario: one or several objects, a low score, an unknown object, special handling, or a network or LLM failure. Check the object, condition, and method, then enter the amount and save. The camera needs browser support and permission, so localhost or HTTPS is recommended. The HTML never uploads anything or runs a real YOLO model or LLM.

## Prototype limits

The prototype is a UI review tool. Sign-in and server responses are simulated. There's no backend, no network requests with user data, no photo upload, no notification scheduling, and no location access. Photos are only previewed locally. Locations show an empty state because there's no verified dataset yet. Guide articles are editorial samples that need review before they become production content.

Photos, reminders, achievements, and Maps still follow the PRD's priorities. Having a design for something doesn't mean it's been built. S10 (filters), S18 (attachments), S20 (states), and S06 (feedback) are shown separately for handoff; they aren't all new Android routes.

## Running the checks again

Any modern browser can open the HTML directly. To rerun the automated checks, use the Node.js and Playwright already installed in the test environment:

```sh
node verify.cjs
node verify-ai.cjs
```

If Playwright lives somewhere else, set `PLAYWRIGHT_PATH` to that package's path. `QA_DIR` optionally sets the folder for screenshots and reports; without it, results go to a system temp folder named `wastenot-qa`. You don't need Playwright to use the prototype.

The kit doesn't change the PDF or HTML sources it was given and doesn't create an Android project. Teams can drop the whole folder into `docs/product-design/` in an implementation repository and the relative links keep working.

## Changes in v1.2 and open decisions

Every document was expanded to follow the team's plan and the user's bottom sheet direction. The AI baseline uses private server-side inference, the five tabs stay, amounts are entered by hand, and Reduce is never inferred from a photo.

The team still has to choose the model, dataset, provider, hardware, budget, and final evaluation thresholds. Web tests don't prove model quality or the Android implementation. The original sources are included, and AI_SPEC explains technical conflicts rather than copying every assumption from the sources as fact.

## Refinements in v1.3

The S21 to S25 previews now show the complete sheet: step indicator, camera framing, object illustration, condition, and advice review. The manual fallback stays visible in the sheet footer, and demo settings are tucked into a disclosure. Corrections keep what the user entered, manual drafts are protected, and switching from the camera to the simulated sample stops the stream. The v1.2 product and API rules still apply.

## Refinements in v1.4

The mockup and `DESIGN.md` were tidied up using an **iOS inset-grouped plus Liquid Glass** approach, with less inline text:

- Statistic cards and list rows are grouped in solid containers with consistent radii.
- The floating header and dock use a Liquid Glass effect (`backdrop-filter: blur(22px) saturate(1.8)` with a specular rim highlight), with content flowing behind them.
- Segmented controls and switches are clearer.
- Long methodology notes moved into `(i)` disclosures.

The product and API rules, all 25 screens (`S01` to `S25`), and all 39 components (`C01` to `C39`) are unchanged.
