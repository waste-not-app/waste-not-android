# WasteNot Design System

Version 1.4 · 13 November 2026 · Core plus AI extension · iOS inset-grouped and Liquid Glass

**Direction:** a fresh, practical habit log that's easy to read. The design was made specifically for personal waste tracking and Reduce, Reuse, and Recycle guidance, as the user asked. The Titip.in reference is not a source for WasteNot's colors, fonts, component shapes, or composition.

This document is the styling source for the [HTML mockup](design-system.html), the [screen spec](SCREEN_SPEC.md), and the Android implementation. Rules for amounts, time, Reduce, and XP follow the [PRD](PRD.md). The HTML shows simulated data; it doesn't prove the backend or Android app exist.

## 1. Identity and design decisions

People open WasteNot many times for short tasks: logging an activity, checking their history, looking up how to handle something, and understanding their progress. It should feel friendly without looking like a kids' game, a marketing campaign, or a tool for judging people.

| Decision | WasteNot's choice | Why |
|---|---|---|
| Character | Fresh, direct, supportive | Users need clear actions and no judgment |
| Action color | Pine green | Sets important actions apart with strong contrast |
| Progress color | Lime | Marks habit progress with enough energy |
| Background | Green-tinted white | Keeps forms and numbers dominant and comfortable for repeated use |
| Font | Plus Jakarta Sans | One friendly sans family with clear hierarchy that works for dense UI labels |
| Composition | One main summary, then a list | Users read one decision first, not four competing numbers |
| Shape | 12 to 24 dp corners depending on function | Soft on main areas, still crisp on inputs and lists |
| Recurring identity | Bold numbers, unit labels, lime progress areas | Puts the logged activities and habits first |
| Icons | Simple material and action symbols | Speed up finding categories without photos |
| Language | English by default with an Indonesian translation, concise and non-judgmental | Trash is a logging method, not a bad label for the user |
| Motion | Short transitions on actions | Helps users follow state changes without getting in the way |

Design read: a personal logging app for students and young people, friendly and utilitarian, **ENERGY 2 / RHYTHM 2 / MOTION 1**. Layouts stay stable inside the app; the design catalog varies only as much as it needs to compare tokens, components, and screens.

No editorial serifs, terracotta, marketplace promo cards, decorative patterned backgrounds, stock illustrations, or photos of made-up people. The Android identity uses the logo and W mark the product owner supplied in `docs/logo/`, along with the WasteNot wordmark. Category icons stay functional UI assets, not environmental certification badges.

## 2. Semantic colors

Components use role names. Hex values are only defined in the theme. Lime is never used for small text on white.

| Token / CSS | Light | Dark | Material 3 / role |
|---|---|---|---|
| background / `--bg` | #F4F7F2 | #101B15 | background |
| surface / `--surface` | #FFFFFF | #19281F | surface |
| surfaceVariant / `--soft` | #E8EFE7 | #26392D | surfaceContainer |
| onSurface / `--ink` | #182B20 | #F0F5ED | onSurface, main text |
| onSurfaceVariant / `--muted` | #526457 | #B2C4B6 | secondary text that stays readable |
| outline / `--line` | #738477 | #7F9484 | inputs, controls, strong dividers |
| subtleOutline / `--divider` | #D9E3D8 | #344C3C | decorative separators, not required control borders |
| primary / `--primary` | #195B3D | #B7DEB8 | primary, main buttons |
| onPrimary / `--on-primary` | #FFFFFF | #122C1C | main button text |
| primaryContainer / `--primary-soft` | #E0EFDF | #274C35 | active choices, supporting content |
| onPrimaryContainer / `--on-primary-soft` | #1B4E32 | #D5EDD3 | active choice text |
| accent / `--accent` | #D2EF69 | #D2EF69 | progress, compact call-to-action areas |
| onAccent / `--on-accent` | #24390E | #24390E | all text on lime |
| error / `--error` | #A82832 | #FFB4BA | validation, delete |
| errorContainer / `--error-soft` | #FDECEE | #4A232B | failure message areas |
| warning / `--warning` | #735117 | #EDD28E | non-fatal attention |
| warningContainer / `--warning-soft` | #FAF0D7 | #3B321C | warnings and editorial drafts |

The main summary area uses a `#123F30` → `#226948` gradient with `#FFFFFF` text in both themes for brand continuity, and `#DBEBDC` for its secondary text. Focus uses a 3 px primary outline on the web and Material's indication on Android. The main chart uses primary; tracks and baselines use the divider color. Destructive buttons use error with white text in light mode and error with `#380B12` in dark mode.

Method colors don't represent any international bin color standard. Reduce and Recycle use the green family, Reuse uses the warning container, and Trash uses soft; always show the method's name. Categories are recognized by their label and material icon, not by memorizing seven colors.

### Contrast

Aim for at least 4.5:1 for normal text, 3:1 for large text, and 3:1 for required control and focus borders against the adjacent background. Test the actual pairs in both themes, including selected, error, and disabled states that still carry information. Never fade locked text until it's unreadable. The package check results are in [QA.md](QA.md).

### Material v1.1: gradients and glass

The 23 September revision followed the user's request for a more layered look with a touch of iPhone-style glassmorphism and liquid glass. The pine and lime identity and the font stay the same. This is a visual interpretation for WasteNot, not Apple's native material.

| Material / CSS token | Light | Dark | Use |
|---|---|---|---|
| `--pine-start/end` | #123F30 → #226948 | Same | Summary, white text; 135° angle |
| `--lime-start/end` | #EDF8BE → #C9EB72 | Same | Reduce, avatar, active icons; text #24390E |
| `--action-start/end` | #226948 → #164B35 | #D0EBC1 → #ADD8B1 | Primary buttons; onPrimary follows the theme |
| `--canvas-start/end` | #FCFDF9 → #DFEEDB | #17261D → #203D2B | Phone background, 155° angle |
| `--glass-tint` | White 92% | #19281F 94% | Dock tint that keeps it readable |
| `--glass-edge` | White 95% | #D0EDD6 24% | 1 px decorative reflection line |
| `--glass-shadow` | #163D27 14% | Black 38% | Dock and frame shadow |

The dock has a radius of 26, a 16 px backdrop blur, 1.2 saturation, and a 1 px inset highlight. Inside the mockup frame it sits 7 px from the left and right and 9 px from the bottom. Each of the five targets is at least 48 dp tall. The active item has a lime area, an icon, and a label, not just transparency. The dock sits in the layout flow so it never covers the last piece of content. On Android, add the device's safe area and navigation bar insets; don't copy the frame margins as system insets.

Glass is limited to navigation; forms, error messages, and dialogs stay opaque. The summary gradient puts focus on the number, and thin highlights add depth without refraction animations. There's no full-screen blur, no pointer-following effect, and no looping motion. Browsers without backdrop-filter get an opaque surface for the dock, and a reduced-transparency preference also turns the blur off. The dark theme uses a dark tint that's tested separately.

In Compose, turn the gradients into brushes and the shapes and elevation into theme styling. The background blur is an optional enhancement: the Android baseline uses an opaque surface with the same highlight border and shadow. Choose a backdrop implementation after profiling on the minimum device; blurring a component's own content is not a substitute for blurring what's behind it. No glass dependency is needed to start. Test scrolling, frame time, fontScale, and contrast before turning it on in production.

## 3. Type and fonts

A single **Plus Jakarta Sans** family, upright variable weight 200 to 800. The mockup uses 400, 500, 600, 700, and 800. The font comes from the [Google Fonts repository](https://github.com/google/fonts/tree/main/ofl/plusjakartasans) and is bundled locally with its SIL Open Font License in `assets/fonts/`. Opening the HTML never calls Google Fonts.

| Role | Android size / line height | Weight | Use |
|---|---|---|---|
| displayLarge | 48/56 sp | 800 | Main dashboard and detail numbers; never paragraphs |
| displayMedium | 36/44 sp | 800 | Auth headings or small summaries |
| displaySmall | 32/40 sp | 700 | Large titles |
| headlineLarge | 28/36 sp | 700 | Page titles |
| headlineMedium | 24/32 sp | 700 | Sub-screen titles |
| headlineSmall | 20/28 sp | 700 | Important section titles |
| titleLarge | 18/26 sp | 700 | List and component headers |
| titleMedium | 16/24 sp | 600 | Row and card titles |
| titleSmall | 14/20 sp | 600 | Group labels |
| bodyLarge | 16/24 sp | 400 | Body, input, guides |
| bodyMedium | 14/22 sp | 400 | Short help, main metadata |
| bodySmall | 12/18 sp | 400 | Secondary metadata, not main content |
| labelLarge | 14/20 sp | 700 | Buttons |
| labelMedium | 12/18 sp | 600 | Chips and navigation |
| labelSmall | 11/16 sp | 600 | Status bar and documentation IDs only |

Normal text tracking is 0; large numbers can use -1 sp; headings go no lower than -0.5 sp. Use tabular numerals for changing values, and keep units visually attached. Never fake weight or italics. All production text lives in string resources; web document titles may be larger than the app scale.

The HTML uses px for the design canvas; Android uses dp for spacing and sp for text. Don't scale sp with the device width. At 200% font scale, layouts reflow and row and form heights grow.

### Fonts in Compose

The variable font source is included for the web and for archiving. The simplest Android mapping is to export static instances of the same font at weights 400, 500, 600, 700, and 800 with an OFL-friendly font tool, or to use variable font support that works with the team's min SDK and toolchain. Verify the result on the minimum device rather than relying on synthetic bold.

Suggested resource names: `plus_jakarta_sans_regular`, `medium`, `semibold`, `bold`, `extrabold`. Declare one `FontFamily` with matching weights. Define all 15 Typography roles explicitly so none quietly falls back to the default font. Include the license when distributing the font.

## 4. Spacing, sizes, shapes, and elevation

| Token | dp / web px | Use |
|---|---|---|
| space1 | 4 | Tight icon-to-label gaps |
| space2 | 8 | Metadata and content in one group |
| space3 | 12 | Gaps between controls or compact rows |
| space4 | 16 | Card content, between fields |
| space5 | 20 | Compact page padding |
| space6 | 24 | Between sections |
| space8 | 32 | Separating large groups |
| space12 | 48 | Auth and empty-state space |

Radii: 8 for status badges, 12 for buttons and inputs, 16 for small cards, and 24 for the main summary and top of modal sheets. Pills are only for chips that can actually be selected, not every card. Initial avatars may be round.

Inputs are at least 56 dp tall, main buttons at least 52 dp, and every touch target at least 48×48 dp. Icons are 24 dp, and category icons are 24 dp inside a 44 to 48 dp area. The NavigationBar is about 80 dp before system insets, with 12 sp labels. App bars are at least 64 dp. Forms are free to grow taller with large text.

Elevation is 0 for in-page lists and cards, 1 to 2 dp for bars or a FAB when layers need separating, and 6 dp for sheets and dialogs. The phone frame shadow in the HTML only groups previews and doesn't carry over to the Android UI. Blur is only for the navigation dock and the matching material sample in the catalog.

## 5. Layout and screen hierarchy

Home: a small greeting → one dominant number area → the log button → Reduce, rate, and progress → recent activity. History: filters and the list are the main content. Statistics: period → totals → daily series → breakdown. Forms: labels and controls in reading order, without a card per field.

One focus per screen: S02/S03 the access form, S04 today's summary, S05/S09 the editor, S06 confirmation, S07 the activity list, S08 the log's values, S11 search, S12 handling steps, S13 period totals, S14 level, S15 achievement requirements, S16 theme choice, S17 opt-in, S18 photo choice, S19 location availability, S20 the state message.

| Available width | Android pattern |
|---|---|
| Under 600 dp | One column, NavigationBar, 20 dp margins |
| 600 to 839 dp | NavigationRail, forms up to 560 dp, roomier lists |
| 840 dp and up | List-detail for history and guides if built; content never stretches without limit |

Breakpoints are a project layout decision. Window resizing, landscape, split screen, the keyboard, and foldables follow the available width, not device name detection. Handle `Scaffold` padding and the safeDrawing and IME insets once. The save button never covers the last field.

Documentation web: a sidebar on desktop and wrapping navigation at the top on mobile; a gallery of 1 to 3 columns with frames up to 390 px. Frame canvases scroll so you can inspect the whole form. Static gallery canvases have a marker and a button to open the interactive version. Don't read the preview frame height as a required Android screen height.

## 6. Component specs

The full C01 to C39 inventory is in [SCREEN_SPEC](SCREEN_SPEC.md). Visual rules that apply across components:

| Group | Default | Selected / focus | Disabled / error |
|---|---|---|---|
| Primary button | primary/onPrimary, radius 12, weight 700 | Press feedback, visible focus ring | soft/muted with a readable label; loading shows text |
| Secondary button | surface/ink, 1 px outline | primary-soft + primary outline | Takes no action when disabled |
| Text action | primary, no large fill | Clear underline or focus | Destructive uses error |
| Field | surface, label above, 1 px outline | 2 px primary outline, contrasting caret | Error outline + text message, not color alone |
| Method selector | 2×2 grid, or a list when space is tight | Primary-soft, radio or check, label | Helper text per method; single choice |
| Chip | soft, 12 to 14 label | primary/onPrimary or primary-soft | The remove button has an accessible label |
| Waste row | Material icon + category and method + value | The whole row is the touch target | Important numbers are never dimmed |
| Progress | Soft track, primary or lime fill depending on the background | Value and current/target label | 0 is not "unavailable" |
| Badge | Radius 8, padding 4/8 | Status always in words | Locked achievements still meet 4.5:1 |
| Dialog | Surface, padding 24, heading 20 | Focus stays in the dialog | Destructive and cancel actions kept apart |
| Empty / error | Functional icon, title, cause, one action | Focus goes to recovery | Never shows fake statistics |

Android shape tokens: 14 dp for fields and controls, 16 dp for compact metrics, 20 dp for grouped cards and glass headers, and 28 dp for dialogs, sheets, and the dock. Pills, icon badges, and photos keep their own shapes. The dock sizes each destination from its label and minimum touch target; it shows a horizontal scroll indicator only when the row cannot fit.

Fields in the HTML are native input, select, date, and time elements, not complex picker replicas. In Compose, use OutlinedTextField, SingleChoiceSegmentedButtonRow, RadioButton, FilterChip, DatePicker and TimePicker, ModalBottomSheet, and AlertDialog as fits. The design system doesn't require a Kotlin wrapper for every example.

## 7. Icons, imagery, and visualization

Navigation icons: a house for Home, a list or clock for History, a book for Guide, bars for Statistics, and a simple silhouette for Profile. Category icons: a leaf (Organic), a bottle (Plastic), a sheet (Paper), a container (Glass), a can (Metal), a device (Electronics), and three dots (Other). Category labels always show; icons never replace the information.

The web uses local inline SVG with a 24×24 viewBox and 2 px stroke. Android can use VectorDrawables from the same paths or equivalent Material Symbols. Use `contentDescription=null` for decorative icons next to text that already says what they mean; standalone action icons must have a description. Avoid emoji, because their shapes and baselines vary between platforms.

The baseline has no stock photos. User photos only appear in the attachment panel after they're chosen. A map with no data shows an honest empty state, not made-up pins. Charts show logged activity and weight, not carbon reduction. Unit labels and text alternatives are always available.

## 8. States, microcopy, and motion

| Situation | Sample text | Action |
|---|---|---|
| Nothing logged | "Nothing logged today yet." | Log activity |
| Reduce | "Tracked separately from waste you handled." | Keep filling in the form |
| Submitting | "Saving…" | Button disabled |
| Success | "Log saved." | View log |
| Network failure | "The log wasn't saved. Your entries are still here." | Try again |
| Empty filter | "No matching logs." | Reset filters |
| Delete | "Your statistics and XP will be recalculated." | Delete log / Cancel |
| Session ended | "Sign in again to open your logs." | Sign in |

Use "waste handled", "items avoided", and "recycling rate based on activities" as the PRD defines them. Never say "verified impact" or "CO2 saved", and never sound like you're blaming someone for Trash. Sample data is labeled as such in the documentation and in every mockup.

Motion: 120 ms for hover and press states, 180 ms at most for simple content changes; no parallax, confetti loops, floating art, or numbers that keep animating. Loading always has text. Respect reduced motion on the web and the Android system animation scale. Success never depends on seeing an animation.

## 9. Accessibility and resilience

- 48 dp touch targets, a logical focus order, visible focus, and a name for every button.
- Dialogs can be cancelled, Escape on the web acts like Back, and focus returns to the trigger.
- Field labels stay visible; placeholders don't replace labels. Field errors are linked to their fields.
- Large input and 200% font scale never cut off labels. Long text wraps instead of forcing horizontal scroll.
- Charts have descriptions, an alternative table or list, and units, and never rely on color alone.
- Data screens have loading, empty, error, and success states. A failed refresh keeps the old data and explains why.
- Contrast is checked in both themes. The dark theme isn't just inverted colors.
- The web simulation never stores passwords, contacts a backend, turns on location or notifications, or uploads photos. The local camera only starts after a user action and permission; AI inference stays simulated.

## 10. Compose handoff and prototype scope

Implementation mapping: `WasteNotTheme` picks the light, dark, or system ColorScheme and loads the Plus Jakarta Sans Typography and Shapes; `Dimens` groups the spacing. The theme is the source of styling values, while domain models stay in the data and state layers. Dynamic color is off by default in this design so contrast pairs and the identity don't change without QA; supporting wallpaper colors can be a later decision.

The HTML includes S01 to S25, the C01 to C39 catalog, dialogs D01 to D04, a theme switch, search and filters, form validation, simulated add, edit, and delete with summary updates, state selection, and local photo previews. The starting numbers follow PRD §9.4 with the demo date fixed at 21 September 2026, even though the design document was revised on 23 September. Reset demo restores the reference dataset, and reloading also resets the simulated data.

S10 is the filter sheet design, S18 the attachment panel, and S20 the state gallery; they aren't three new product tabs. The gallery shows them separately for handoff. Authentication, server conflicts, private uploads, Android permissions, and scheduling are only simulated. Android acceptance still has to be proven by implementation and on-device testing.

## 11. Checklist before implementation

- [ ] Every component color uses a role token, not scattered hex values.
- [ ] Every Typography role uses the right family and weight.
- [ ] Fields, the bottom bar, the FAB, and dialogs pass font scale and inset checks.
- [ ] Method colors are backed by labels; Trash is never shown as a user failure.
- [ ] Every screen ID and component actually used has a Compose equivalent.
- [ ] The sample dataset never ships in a production build as real user results.
- [ ] Guide and location content is verified before publishing.
- [ ] Android QA is recorded separately from the HTML documentation QA.

## 12. AI styling and the main navbar action (v1.2)

The pine and lime direction, Plus Jakarta Sans, and the glass dock still apply. The 28 September request added a main button at the center of the navbar that opens a camera bottom sheet, with the manual fallback visible without the scan having to fail first.

The Log/Scan action sits prominently at the top center of the dock (it doesn't replace the Statistics or Guide tab). It uses the lime gradient, onAccent text, a 24 dp camera icon, a minimum height of 52 dp, and a minimum width of 136 dp. The dock leaves 32 dp above it so the action never covers the five navigation labels; content stays above the dock in the layout flow. On wide screens the action sits near the rail; don't force six destinations.

The camera sheet uses an opaque surface over a scrim, a 28 dp top radius, 24 dp padding (16 when space is tight), a height limit of 92% of the available space, and internal scrolling. The close and manual buttons are at least 48 dp. The keyboard and system bars never cover the fallback. The drag handle in the HTML is decorative, not a promise of a drag gesture; Android uses native sheet behavior and Back. Initial focus goes to the title; Escape cancels and stops the camera.

The camera preview is a dark area with white text kept apart from the feed; controls never put unbacked text over an image of unknown contrast. Bounding boxes are solid or dashed lines with labels and an alternative list. Scores are labeled "Detection score", with no correctness checkmark or green that promises safety. Low confidence and special handling use warning colors plus text, never color alone.

Advice content: the method first, a short reason, the source and review date, cautions, and Change or Use. Glass is never used on the condition form or warning cards. Photos get no filters or gradients that change how the material looks. Loading is just an indicator and text, with no made-up percentages. Confirmation moves on to an empty amount field; never show a weight estimated from the photo.

C35 to C39 and S21 to S25 complete the catalog at 39 components and 25 screens and panels. [AI_SPEC](AI_SPEC.md) defines the model's limits, and [SCREEN_SPEC](SCREEN_SPEC.md) defines the states. The HTML can try the local camera, but detection and the LLM are always labeled as simulated.

## 13. Visual refinements in v1.3

The sheet uses a four-step indicator, with the current step shown as a lime circle with a text label. The camera preview uses dark pine, a dashed framing area, and high-contrast text. Object results use a large material icon in a bounding box illustration labeled as a sample; it doesn't claim coordinates from real inference.

Dividers separate the header, the scrolling content, and the manual footer. The footer stays opaque so its text doesn't mix with the camera feed and stays reachable when the content is long. Simulation settings and the reasoning behind advice sit in disclosures. The manual forms on Home and History become secondary actions so the dock's Log/Scan is the main focus. The v1.1 radii, gradients, glass dock, and typography still apply.

## 14. Visual refinements in v1.4: iOS inset-grouped, Liquid Glass, and tighter copy

The 13 November revision tidied up text density and surface structure after reviewing the mockup and Android app directly:

1. **iOS inset-grouped surfaces.** Activity lists, detail metadata rows, guide results, profile and settings menus, and statistics breakdowns no longer float over bare dividers. Related items sit in one solid card (`--surface`, `20 dp` outer radius or `16 dp` for compact metrics) with thin internal separators inset to align with the text (`inset-left: 68 px` for rows with icons).
2. **Targeted Liquid Glass (at most 2 layers per viewport).**
   - The **floating Liquid Glass dock** (`TabDock`) floats at the bottom edge with `backdrop-filter: blur(22px) saturate(1.8)`, a `1 px` specular rim highlight, a soft shadow, a lime active capsule, and a prominent `Log / Scan` button. Content behind the dock keeps flowing and blurs softly as you scroll.
   - The **sticky glass header** on long lists uses a soft glass tint so the page title and action buttons stay sharp over scrolling content.
   - Forms, inputs, error banners, dialogs, and content cards stay **solid and opaque**, so numbers stay readable and WCAG AA contrast (`≥ 4.5:1` for normal text, `≥ 3:1` for components) holds in both light and dark themes.
3. **Text density rules (copy tightening).**
   - Every summary and metric card shows at most **1 label + 1 main number + 1 line of context**. Methodology notes ("Your personal log...", "Share of all activities...") moved into a disclosure (`(i)` / `<details>`) or short helper text, so users see the data and main action right away.
   - Editorial warning banners no longer block the top of a list when a status badge (`Draft`) already conveys the information.
4. **Jetpack Compose library mapping (v1.4, minSdk 30, Kotlin 2.3.20).**
   - **Liquid Glass:** `Haze` (`chrisbanes/haze`) for real-time backdrop blur on the floating dock and sticky header, with an automatic translucent surface fallback on Android 11 (API 30).
   - **Clean iOS-like components:** the inset-grouped structure, segmented pill, toggle switch, and sheet header are built from `WasteNotTheme` tokens with `Compose Unstyled` or `Composables UI` patterns, without mixing M3 widgets with `Compose Cupertino` or changing the theme system.
   - **Charts, photos, and skeletons:** `Vico` for the daily statistics chart (`S13`), `Coil` for loading private photos (`S18`), and `Compose Shimmer` for loading skeletons (`S20`).
5. **Color harmony and lively details.**
   - **Primary and secondary buttons:** primary buttons use layered emerald (`#146342 → #1D7A52` light; `#52D69A → #3BC487` dark); secondary buttons use a soft emerald tint (`--soft`, `#E5F2EA` / `#1E3529`) so they belong to the same family as the main button instead of looking pale gray.
   - **Restrained metric tiles:** secondary metric cards keep a solid surface (`--surface`) with a softly accented badge icon (`.metric-spark`), avoiding giant neon lime blocks, especially in dark mode.
   - **Softly colored material icons and an ambient canvas:** each material category (organic, plastic, glass, paper, metal, electronics) gets its own soft tint, and the canvas background carries a subtle ambient glow so the Liquid Glass dock and header refract real color depth.
   - **Glass forms and soft-well inputs:** form cards use soft frosted glass (`glass-form`); input, dropdown, date, and time fields use a soft-well tint (`--field-bg`) with an emerald focus halo (`--focus-halo`); dropdowns use a custom chevron instead of the stiff OS arrow; the weight input and unit merge in `.quantity-compound`; and method radios use a custom ring indicator (`appearance: none`). In Compose these map to a custom `Surface` + `BasicTextField` (`Compose Unstyled` or `RikkaUI`), dropdown and sheet pickers, and `Compose-Pickers` or Material 3 `DatePicker` and `TimePicker` inside a frosted sheet.

## Android logo identity

The artwork source is `docs/logo/wastenot-app-icon.png` and `docs/logo/wastenot-w-mark.png`, relative to the Android repository root. The icon file name was normalized from `EcoLoop Green App Icon-Photoroom.png`; the original PNG content is unchanged. The full icon is used for the app identity, and the transparent W mark is used for the wordmark, the adaptive launcher, and summary accents. The artwork is kept as is, not redrawn into a generic leaf icon.

- Dark pine follows the logo's base, and lime marks progress and important actions. Cream gives the welcome screens a calm space. Plus Jakarta Sans stays so the new identity doesn't change the hierarchy of numbers and labels.
- The adaptive icon places the mark inside the safe area without stacking a rounded box inside the launcher mask. The monochrome version follows the W mark's silhouette.
- The summary watermark is decorative, low contrast, and sits behind the content; numbers, units, and labels stay dominant.
- TalkBack doesn't announce the mark again next to the WasteNot name. The header reflows at narrow widths and large text sizes.
- Nothing changes in the authentication, onboarding, scan, or save flows. Light and dark modes and ENERGY 2 / RHYTHM 2 / MOTION 1 still apply.

Android branding tokens: pine `#0C3023` → `#17533A`, cream `#F7F6E5`, lime `#CEF067`. Cream has contrast ratios of 13.17:1 and 8.25:1 against the two ends of the summary gradient. Status and error colors don't follow the logo colors.

Run `python3 scripts/generate_brand_assets.py` from the Android repository to regenerate the UI bitmaps, the adaptive and monochrome launchers, the splash screen, and the legacy mipmaps from mdpi to xxxhdpi. The generator needs Pillow; the source PNGs stay unchanged in `docs/logo/`.
