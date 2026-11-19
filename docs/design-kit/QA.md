# WasteNot: Design Kit Checks

Latest check: 13 November 2026 (v1.4 mockup refinements, iOS inset-grouped + Liquid Glass). Evidence for materials v1.1 to v1.3 below is kept as history. Scope: HTML, CSS, JavaScript, documents, fonts, and interaction simulation. These are **not** test results for the Android app or the backend.

## v1.4 results (iOS inset-grouped + Liquid Glass)

- PASS `verify.cjs`: all 22 checks passed (25 screens and panels, 39 components, 360/390/768/1024/1512 px in light and dark, 200% text stress, 4 dialogs, 8×5 states, forms, filters, guides, statistics, reminders, photos, locations, 0 JS errors).
- PASS `verify-ai.cjs`: the whole AI bottom sheet flow passed (4 widths × 2 themes, Escape and focus restore, multiple, low, none, special, offline, and LLM fallbacks, virtual camera, 200% text stress, 0 JS errors).
- Contrast for the new v1.4 tokens was calculated with the WCAG AA formula: 10 of 10 light and dark pairs are at least 5.96:1 (target 4.5:1).

## AI v1.2 results

- PASS `verify.cjs`: 25 screens and panels, 39 components, five viewports in two themes; regressions for create, edit, delete, states, units, navigation, and 200% font passed with no JavaScript errors.
- PASS `verify-ai.cjs`: the sheet at 360/390/768/1512 px × two themes; Escape closes it and returns focus to the main action.
- PASS: the category must be confirmed, the method can be corrected, and the amount stays empty; a scan adds no record or XP, and one valid submit adds one log.
- PASS: multiple objects, low scores, unknown, special handling, network failure, and LLM failure all have a manual fallback. Special handling offers no use-advice button.
- PASS: capture and retake use Chromium's virtual camera; capture and close stop the track. Permission denial was tested with a fake getUserMedia rejection. Physical and Android cameras haven't been tested.
- PASS: cancelled async results are ignored; manual entry is reachable with 200% sheet text; no JavaScript errors.
- Visual review: Home with the dock action, the camera sheet and scan results, and the S21 to S25 previews were checked. Photos aren't uploaded; boxes and advice are labeled as illustration or simulation.
- The PRD, DESIGN, SCREEN_SPEC, API, ARCHITECTURE, IMPLEMENTATION_PLAN, and README were updated; AI_SPEC and a copy of the source plan were added. The API and infrastructure contracts are still designs.

Not tested: real YOLO, the model, dataset, accuracy, and calibration, the actual LLM provider and its safety, endpoints, backend, and file retention, latency and cost, and the Android camera and physical device permissions. No AI result claims are based on the browser simulation.

## What was checked

| Check | Result / evidence |
|---|---|
| Inventory | PASS: 25 gallery items S01 to S25, 39 components C01 to C39, 4 dialogs D01 to D04 |
| Offline file | PASS: opened as a local file in Chromium; local fonts load; no data server needed |
| Desktop and mobile | PASS: all 25 screens at 360, 390, 768, 1024, and 1512 px widths; no horizontal overflow |
| Themes | PASS: the size matrix repeated in light and dark; the theme control and logging flow were also tried |
| States | PASS: 8 data screens × loading/empty/error/offline/content; plus 6 sample states on S20 |
| Sample numbers | PASS: 1,400 g, 2 items avoided, 50% recycling rate, streak 2; an empty denominator gives null |
| Unit validation | PASS: g, kg with a comma or point, integer items, value limits; NaN, Infinity, and exponents rejected |
| Unit conversion | PASS: 250 g → 0.25 kg → 250 g; switching to items clears the value so no fake conversion happens |
| Contrast | PASS: 28 text token pairs at least 4.5:1, lowest measured 5.40:1; 4 outline/background pairs at least 3:1 |
| Font | PASS: the Plus Jakarta Sans variable font loads the wght axis 200 to 800; the OFL license is included |
| Large text | PASS: 200% font stress on Home, forms, history, and statistics at a 390 px viewport with no horizontal overflow |
| Visual | PASS: screenshots of all 20 screens reviewed; long forms and content scroll; labels and CTAs stay inside the frame |
| Runtime | PASS: no JavaScript page errors during browser checks |
| Documents | PASS: source requirements and new decisions are kept apart; the Titip.in styling reference was replaced by WasteNot's direction |

The 200% CSS stress test is a web check, not a substitute for Android fontScale or TalkBack testing. The contrast checked covers the color pairs the system defines; this report isn't a WCAG certification of the whole product.

## Material revision v1.1

- Gradients, canvas, and glass tint composites: 1,212 color samples across both themes, with a minimum text ratio of 5.24:1 (target 4.5:1). The glass samples test black-to-white backgrounds at the specified tint opacity; dock text uses onSurface. This is a color check, not a visual certification of every pixel.
- The dock blur is active in Chromium in both themes; all five navigation targets are at least 48×48 px at a 360 px viewport.
- Reduced motion keeps scrolling without animation; forced colors turn off the backdrop blur. The CSS also has an opaque surface fallback when backdrop-filter isn't available or reduced transparency is on; those last two conditions were checked in the CSS rules, not on physical devices.
- The matrix and click-through below were repeated with v1.1 styling. Screenshots of 20 screens per theme were made for visual review, and the desktop preview was updated.
- This implementation is a web interpretation of glass. Apple's native Liquid Glass effect and Android backdrop performance aren't claimed to be implemented or tested.

## Click-through evidence

| Element / flow | Action and result |
|---|---|
| Screen selection | S01 to S25 each opened through the selector and gallery buttons; target IDs match |
| Gallery filters | Access, tracking, insight, profile, optional, and all change the set of previews |
| Theme selector | Light and dark update the whole page and prototype; system follows the browser preference |
| Empty log form | Save asks the user to choose a method |
| Invalid weight | 0 is rejected with the 1 g to 100 kg weight message |
| Create | 100 g Recycle creates one new log, taking the count from 5 to 6 |
| Detail | "View log" opens the newly created 100 g |
| Edit | The value changed to 300 g; the count stays 6, so no extra create XP |
| Delete | Cancel keeps the log; confirming brings the count back to 5 |
| Reduce method | Saved in both themes; shown separately from handled weight |
| History filters | Plastic AND Recycle gives one row; reset brings back five |
| Sheet reset | Clears the draft category, method, and dates, rather than returning to the original filter values |
| Guide search | A query with no results shows an empty state; clearing brings the list back |
| Guide detail | The article opens; "Log this activity" fills in the Plastic category and Recycle method |
| Statistics | The 7/30 day toggle changes the range; "See daily numbers" opens the text data |
| Form draft | Back opens a confirmation; Escape keeps the text; Discard changes clears the draft |
| Selector while dirty | Cancel shows the active screen ID again, not the cancelled target |
| Dialogs | All four dialogs open; the keyboard moves to the dialog actions; Escape closes; the conflict loads the latest simulated version |
| Reminders | Opting in enables the time field; 21:30 is saved as a simulated preference; off disables the time |
| Photo | A local PNG shows a preview; Remove clears the preview; text/plain is rejected |
| Location | Area search says there's no data yet; a simulated permission denial offers a search fallback |
| State gallery | Loading, empty, error, offline, session, and filter each change the message and action |
| Registration | A mismatched password confirmation is rejected; matching values go into the simulated account |
| Logout and login | Logout clears the demo session data; login opens an empty Home |
| Bottom navigation | All five tabs go to Home, History, Guide, Statistics, and Profile |
| Password component | The eye button toggles masked and visible |
| Filter chip | Clicking toggles aria-pressed |
| Segment component | 30 days becomes the only active choice |
| Filter sheet component | Opens an overlay; the Glass category gives one row after Apply |
| Snackbar component | The button shows feedback in a live region |
| Switch component | The checkbox changes state and feedback without browser permission |
| Search component | Typed text appears in the sample search feedback |
| Native inputs and pickers | Text, select, date, time, radio, and checkbox fields use built-in browser behavior |
| Reset demo | Restores the five starting records and clears drafts, filters, and previews |

Non-interactive elements such as badges, progress, decorative icons, and gallery previews aren't presented as fake buttons. Document links and anchors were checked against their targets in the local package. The simulation doesn't do server authentication, database transactions, uploads, geolocation, or scheduling.

## Antislop delivery gate

### Hard gate

- R-02 PASS: no em dashes in the delivered HTML and DESIGN content.
- R-03 PASS: the 25 screens × 5 widths × 2 themes matrix produces no horizontal overflow.
- R-17 PASS: product numbers come from a labeled simulated dataset; screen and component counts are taken from the DOM.
- R-18 PASS: no testimonials, photos of people, or made-up user claims.
- R-23 PASS: the user asked for a design direction; the text wordmark, demo initial avatar, and editorial placeholders are labeled.
- R-24 PASS: documentation navigation points to sections and files that exist; the five app tabs have real destinations.
- R-25 PASS: measured token text pairs ≥5.40:1; control border pairs tested ≥3:1.
- R-26 PASS: control behavior is recorded in the click-through matrix; disabled and preview controls have clear roles.
- R-27 PASS: loading, empty, error, offline, and content states exist on data screens; success and session states are shown too.
- R-28 PASS: no filler FAQ.
- R-32 PASS: native buttons, inputs, and dialogs work with the keyboard; focus-visible is defined; Escape cancels dialogs.
- R-33 PASS: the implementation lives directly in HTML, CSS, and JS; no UI patch helpers are included.
- R-34 PASS: light and dark themes were tested at every width and with logging interactions.
- R-35 PASS: Chromium ran the prototype, click-through and screenshots were available during QA; `verify.cjs` is included to rerun the checks.
- R-36 PASS: no claims about production performance, certified security, or environmental reduction.
- R-37 PASS: DESIGN.md sets the direction first; the palette and fonts are independent, as the user asked.
- R-38 PASS: demo data, editorial guides, Maps without a dataset, and feature options are stated explicitly.

### Purpose gate

- R-01 PASS: pine for actions, lime for progress; the reasoning is in DESIGN §1 to §2.
- R-04 PASS: symbols show materials and actions, with labels; the choices are explained in DESIGN §7.
- R-06 PASS: Plus Jakarta Sans was chosen for UI and numbers; one family and a documented role scale.
- R-07 PASS: pine and lime gradients set apart the summary and progress; the mint background gives the dock a base, as the user asked.
- R-08 PASS: arrows are only Back and navigation chevrons, not decoration on every CTA.
- R-09 PASS: badges are used for methods and status; no promotional badges.
- R-10 PASS: the user asked for glassmorphism, and it's limited to the dock and its material sample; forms and dialogs are opaque.
- R-12 PASS: shadows set apart the dock, frame, and overlays; thin highlights on the summary and CTAs; lists have no shadows.
- R-13 PASS: no glow.
- R-14 PASS: the app layout follows forms, lists, and statistics; component and gallery grids are used to compare the catalog.
- R-19 PASS: short control transitions, reduced motion available, no repeating decorative motion.
- R-22 PASS: no unrelated stock illustrations; icons are functional.

### Liveliness and quality locks

- Dials PASS: ENERGY 2 / RHYTHM 2 / MOTION 1 were written down before the mockup was made.
- Focal point PASS: the number, form, list, or main action is set apart on each screen in DESIGN §5.
- Whitespace PASS: 4 to 48 spacing and the section and field divisions are applied consistently.
- Accent PASS: lime marks prevention and progress; primary handles actions.
- Identity PASS: bold numbers, units, and pine and lime areas form the recurring identity.
- Design read PASS: user context, visual language, and dials are recorded in DESIGN §1.
- C-1 PASS: the reasoning behind color, font, layout, spacing, icons, and shapes is recorded.
- C-2 PASS: control behavior is tested and the simulation's limits are stated.
- C-3 PASS: sections cover foundation needs, components, screens, the prototype, and handoff.
- C-4 PASS: sizes, themes, states, and keyboard were checked; Android validation stays implementation work.
- C-5 PASS: no impact or user numbers are presented as facts.
- R-05 PASS: documentation pages have different content types; the app follows each screen's job.
- R-11 PASS: radii of 8, 12, 16, and 24 differ by function; the dock uses 26; inputs stay at 12.
- R-15 PASS: CTAs name the action, such as Save log, Apply filters, and Remove photo.
- R-16 PASS: no marketing buzzwords standing in for product function.
- R-20 PASS: the design is built for Reduce, Reuse, and Recycle logging and doesn't copy the source's marketplace UI.
- R-21 PASS: light, dark, and system options are available.
- R-29 PASS: pine and lime are the identity colors; error and warning colors are used semantically.
- R-30 PASS: doesn't copy a popular product's layout or the Titip.in example.
- R-31 PASS: the reasoning for big decisions is in DESIGN §1.

## Not proven by this package

The Android build, TalkBack on a device, Android fontScale, API ownership, session security, backend latency, query performance, private uploads, WorkManager, a Maps provider, and the editorial accuracy of the guides. All of these have to be tested during implementation, following IMPLEMENTATION_PLAN.md.

## v1.2 machine evidence

Included reports: [core regression](assets/qa-core-v1.2.json), [AI browser simulation](assets/qa-ai-v1.2.json), and [AI flow preview](assets/preview-ai.png). `verify.cjs` and `verify-ai.cjs` can be rerun as the README describes. A structure check found 60 valid local links; the plan copy matches its source file byte for byte.

## v1.3 mockup verification

- PASS: 22 core regression groups, 25 screens × 5 viewports × 2 themes; interaction, 200% font, and JavaScript error checks.
- PASS: the sheet flow at four viewports × two themes, AI and manual scenarios, virtual camera capture, permission denied, and the fallback at 200% font.
- PASS: choosing the simulated example stops the camera; a stream from a late permission grant is stopped immediately; Escape stops the track before the dialog finishes closing.
- PASS: a dirty draft isn't overwritten by a new scan. Corrections keep the object, category, condition, and reuse intention; changing the material resets the previous condition.
- Visual review: the camera sheet with the step indicator, the manual footer, and the condition form preview use the same components as the interaction; text and main targets are readable.
- C07, the catalog layout, DESIGN, SCREEN_SPEC, and README were aligned with the secondary manual action and the main Log/Scan. The v1.2 domain and API contract didn't change.

Latest evidence: [core v1.3](assets/qa-core-v1.3.json), [AI browser v1.3](assets/qa-ai-v1.3.json), [preview](assets/preview-ai.png). The v1.2 reports are kept as history. The physical camera, Android, YOLO, and production LLM haven't been tested.
