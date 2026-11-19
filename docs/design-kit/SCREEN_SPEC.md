# WasteNot: Screen and Component Spec

Version 1.3 · 28 September 2026 · Core plus AI extension · Implementation design

See the [PRD](PRD.md) for product rules and [DESIGN](DESIGN.md) for styling. The screen IDs here are the same ones used in the [HTML design system](design-system.html). Optional screens don't automatically join the MVP scope. All visual data is labeled simulation, with a reference date of 21 September 2026.

## 1. Shared patterns

- App bar: the screen title, Back when it isn't a main destination, and at most two actions that really matter.
- Main navigation: Home, History, Guide, Statistics, Profile. Every icon has a label.
- Long content scrolls vertically. Form CTAs never cover inputs or the keyboard.
- The first load shows progress and context text; refreshes keep the last data.
- A first-time empty state is different from an empty search or filter result. Network errors are different from field validation.
- Every data change waits for the server to confirm; never show success just because a button was tapped.
- Save status and errors are announced to accessibility services. Focus returns to the dialog's trigger when the dialog closes.

## 2. Screen inventory

| ID | Name | Priority | Main purpose | Route / overlay |
|---|---|---|---|---|
| S01 | Session restore | Must | Decide the navigation root | Session |
| S02 | Sign in | Must | Start a session | Login |
| S03 | Register | Must | Create an account | Register |
| S04 | Home | Must | See today and start logging | Home |
| S05 | Log activity | Must | Create one valid log | LogCreate |
| S06 | Log saved | Must, feedback | Confirm the result and next steps | Result on the detail screen, not a tab |
| S07 | History | Must | Find your own logs | History |
| S08 | Log detail | Must | Check and correct data | LogDetail(id) |
| S09 | Edit log | Must, by design | Fix data | LogEdit(id) |
| S10 | History filters | Must | Choose list criteria | Bottom sheet |
| S11 | Guide | Must | Look up material information | Guide |
| S12 | Guide detail | Must | Read handling steps and sources | GuideDetail(id) |
| S13 | Statistics | Must | Review a 7- or 30-day range | Statistics |
| S14 | Profile and progress | Must + baseline XP | See the account and habits | Profile |
| S15 | Achievements | Should | See requirements and progress | Achievements |
| S16 | Settings | Supporting design | Set display and preferences | Settings |
| S17 | Reminders | Should | Choose an optional reminder | ReminderSettings |
| S18 | Photo attachment | Stretch | Add a supporting image | Editor panel |
| S19 | Recycling points | Could | See verified facilities | RecyclingPoints |
| S20 | App states | Documentation | Compare failure, empty, and loading states | Not its own production route |
| S21 | Camera and scan | AI increment | Photo and scan, with manual fallback | Bottom sheet |
| S22 | Object confirmation | AI increment | One object and category correction | Sheet phase |
| S23 | Object condition | AI increment | Context from the user | Sheet phase |
| S24 | Advice confirmation | AI increment | Review the method before the amount | Sheet phase |
| S25 | AI fallback | AI increment | Recovery and manual entry | Sheet phase |

## 3. Screen details

### S01: Session restore

Show the WasteNot wordmark and "Getting your logs ready…". There's no artificial splash delay and no mandatory onboarding carousel. The session repository reads the credentials, verifies or refreshes the session if needed, then sets the root to Sign in or Home. With no credentials, go straight to Sign in.

When the network can't validate an uncertain session, show "Can't check your session yet", "Try again", and "Sign in again". Never show personal data from an identity that isn't confirmed. Back follows system behavior. The HTML has a "Simulate a valid session" button because it doesn't really authenticate.

### S02: Sign in

Order: wordmark → the title "Pick up your habit again" → email → password with show/hide → form errors → "Sign in" → "New here? Create an account". Use the email keyboard for email, masking for the password, and IME next/done in order. Password managers and paste are allowed.

Empty and format checks happen before the request. Wrong credentials show a generic "That email and password don't match." Network errors are separate. While submitting, the button says "Signing in…" and is disabled. Success clears the auth stack. If the session expired, a banner explains why the user has to sign in again. Forgot password isn't shown as a dead link; it's not in the baseline yet.

### S03: Register

Fields for name, email, password, password confirmation, and a visible starting time zone. The password helper says 12 characters minimum. Full validation is in PRD §8. A "Create account" button and an "Already have an account? Sign in" link. Don't add a checkbox agreeing to documents that don't exist yet.

A duplicate email error is attached to the email field. Validation errors never clear other fields. Passwords aren't persisted when the app process is killed. On success, go straight into the session if the API issues one, with Home as the root. The privacy policy and consent for public distribution are separate release work.

### S04: Home

The screen focuses on today's total and the logging action. Layout:

1. A greeting with the user's name and local date.
2. The "Waste handled today" summary by weight; items handled appear separately if there are any.
3. Reduce shown separately: "2 items avoided" in the sample data.
4. The recycling rate with its activity basis; today's sample is 50% of 2 handling activities.
5. A supporting "Log manually" button; the main Log/Scan action lives in the dock.
6. A compact XP and level progress; the sample is 50/100 XP at level 1.
7. The three most recent activities, each opening its detail; "See history" goes to S07.

The dashboard isn't a list of environmental achievement numbers. A new user's empty state says "Nothing logged today yet", with a CTA to log and access to guides. If there's data from yesterday but nothing today, don't hide all-time progress. A full-screen error is only for a first load with no cache; a failed refresh shows a banner over the old data.

### S05: Log activity

A single scrollable form, no wizard. Fields in order: method → category → Weight or Count → value and unit → activity date → optional notes → attachment if the feature is on → the Save CTA.

The method is a single choice labeled Reduce, Reuse, Recycle, Trash, each with a one-line meaning. The category uses a menu or picker that shows full labels. Weight defaults to g; choosing kg only changes the display, and the payload stays integer grams. Switching to Count disables the weight field, drops it from the payload, and relabels the field "Item count".

If the method is Reduce, change the label to "Amount avoided" and show "Tracked separately from waste you handled." Category and method are never quietly filled in on an empty draft. A prefill from a guide is visible and can be changed.

Validate on blur and on submit; don't show errors before the user interacts. An invalid submit focuses the first error. A valid submit sends one request with a client_request_id. When a timeout leaves the outcome unknown, the original entries stay for a safe retry. Back on a dirty form opens D03. The date defaults to today in the account's time zone.

### S06: Log saved

Feedback appears after the server returns an ID. It summarizes the category, method, value, date, and XP change, with "View log" and "Back to home" buttons. "+10 XP" only appears after a new create, not a replay or an edit. On Android this feedback can be a banner or snackbar on the S08 detail screen so there's no extra route. The HTML shows it separately to make the transaction result clear.

No repeating confetti, automatic sounds, or "saving the planet" claims. TalkBack hears "Log saved". If the feedback includes an important action, it doesn't disappear before the user can read it.

### S07: History

The History header, filters, active criteria chips, a list grouped by date in descending order, and a supporting "Log manually" button. Each row has a category icon, the category, method, date or short note, and the value with its unit. Reduce rows are labeled "avoided".

Rows open S08. Pagination loads 20 logs at a time; loading and errors for later pages sit at the bottom of the list. Filters combine category, method, and date. Scroll position and filters survive opening and closing a detail. The first empty state invites logging; a filtered empty state offers a reset. A deleted item only disappears after the 204, followed by an aggregate refresh.

### S08: Log detail

A category title, the method, a large value, the activity date and time zone, the notes or "No notes", a photo if there is one, and created and updated times in a secondary section. "Edit log" goes to S09, and "Delete log" opens D01. There's no public sharing.

Deleted or 404 shows "Log unavailable" and goes back to History. It doesn't reveal whether a log belongs to someone else. If an old detail is cached, editing and deleting aren't allowed without a valid version. An expired private photo reloads through the authenticated endpoint rather than switching to a public URL.

### S09: Edit log

Uses the S05 editor with the original data and version. The title is "Edit log" and the CTA is "Save changes". Add the helper text "Edits update your statistics but don't add XP." The date still follows the validation window; an old log outside the window may keep its old activity_date, but a new date must pass the create rules.

If nothing changed, the CTA may be disabled. A version conflict opens D04 and the draft is kept. Success returns to the latest detail. If loading fails before the form is ready, empty fields are never shown as the log's real values.

### S10: History filters

A modal bottom sheet titled "Filter history", with a single category choice (All + 7 categories), a single method choice (All + 4 methods), start and end dates, "Reset", and "Apply filters". The sheet's editing state stays separate from the active filters until Apply. Cancel, Back, or dismissing doesn't apply changes.

The start date can't be after the end date. If only one date is filled in, the app clears both or asks for the pair, as the visible field validation says. Reset clears the draft filters immediately; Apply closes the sheet and fetches from the first page again. The sheet scrolls at large font scales, and focus stays inside the modal while it's open.

### S11: Guide

A search field reading "Search bottles, cardboard, or other materials", an optional category, and a list of titles with their recommended method and a short summary. Proposed debounce is 300 ms, cancelling old requests; the clear button has a contentDescription. The search q is at most 100 characters.

Empty results: "No guides found. Try a more general material name." with "Clear search". Network errors: "Couldn't load the guides" with a retry. If a sample article hasn't been reviewed, the HTML labels it as a draft; production never loads DRAFT articles.

### S12: Guide detail

Layout: item title → category and method → summary → numbered steps → things to keep in mind → sources and review date → "Log this activity". Source links open the browser with a clear host. The content never claims every facility accepts the same materials.

The CTA opens S05 with the starting category and method, without creating a log automatically. Archived or 404 articles have a way back. If a source or review date is missing, the prototype says "Editorial sample; not verified yet" and never invents links or dates.

### S13: Statistics

A "7 days" / "30 days" toggle, an explicit date range, a summary of weight and count handled with Reduce shown separately, a daily chart with unit labels, a category list, and a four-row method breakdown. Show the activity-based recycling rate with its numerator and denominator.

The 7-day reference dataset gives 1.4 kg, 5 activities, 2 items avoided, and a 50% recycling rate. The gram chart never mixes in counts; use a selector or separate series when handled COUNT data exists. Days with no data show 0 instead of disappearing. Every chart has a text description or table. An empty range shows "No logs in this period", a choice of other periods, and Log activity.

When the range changes, only the statistics section shows loading, and an old response never overwrites the new range. If a request fails, the old range's data stays correctly labeled; never put a 30-day title over 7-day numbers.

### S14: Profile and progress

Name, email, time zone, level, XP bar, all-time activity, streak if enabled, an achievements link, settings, and "Sign out". The avatar starts with initials, not a photo of a made-up person. The sample name and email in the mockup are labeled "Demo User".

At level 1 the sample progress bar shows 50/100 with the label "50 XP to level 2". The streak counts days, not logs. With nothing logged: level 1, 0 XP, and a CTA to log. A profile fetch error never shows old account data. Sign out opens D02.

### S15: Achievements (Should)

A list of titles, requirements, numeric progress with a bar, and locked or unlocked status. Locked text never relies on low opacity. Requirement details open in an accordion. Sample: First step 1/1 unlocked; Getting into the habit 5/5 unlocked; Sorted for recycling 2/3; A consistent week 2/7.

Status comes from active data, so deleting a log can lock an achievement again. When the capability is off, the app hides the link. The HTML still includes the design, marked "Should".

### S16: Settings

Theme choice (System, Light, Dark); a reminders link when the feature is on; the app version; and a privacy summary explaining personal data and optional photo access. The theme setting is stored locally and applies immediately. The account's time zone is read-only in the baseline; don't offer an editor the API doesn't support.

A production privacy link only appears once the document actually exists. The HTML shows an explanation of the draft scope without claiming to be an official policy.

### S17: Reminders (Should)

A switch that defaults to off, an adjustable time "Around 8:00 PM", the sentence "The reminder may arrive late depending on your device.", and a "Save reminder" button. Notification permission is requested after the user chooses to turn it on. If permission is denied, show an explanation and a link to system settings rather than asking again and again.

When off, the time is disabled or stays visible as a preference with no active schedule. Signing out cancels the account's reminders. The HTML only simulates the preference; it doesn't ask for browser permission or send real notifications.

### S18: Photo (Stretch)

An editor panel with "Choose photo", a preview, the file name and size, "Replace", "Remove photo", upload status, and "Continue without a photo". Use the Android Photo Picker. Photos are never required. If the type or size is wrong, the error attaches to the panel and the notes field stays available.

The HTML uses a local image preview without uploading, with a label saying it's a simulation. It never adds stock photos that look like the user's, and never asks for camera access without an explicit action.

### S19: Recycling points (Could)

The design has a title, "Use my location", an area search, a list of facilities, and a map slot. Since there's no location dataset yet, the mockup defaults to "Recycling point data isn't available yet", explaining that the feature needs verified locations. There are no fake pins or distances.

When built: tell apart loading, permission denied, location unavailable, empty results, provider errors, and stale data. Device location is optional; the manual list stays available. A point's detail shows its name, address, accepted materials, hours if a source has them, the verification date, and an external navigation action. Never guarantee that a facility is open without verification.

### S20: State gallery

A documentation panel that lets reviewers pick loading, empty, filtered-empty, error, offline, session-expired, and success. This isn't a menu shipped to Android users. The states are reused on the relevant screens with context-specific text.

| State | Sample text | Action |
|---|---|---|
| Loading | "Loading your history…" | Wait; content isn't actionable |
| First empty | "No logs yet. Start with something you did today." | Log activity |
| Empty filter | "No logs match these filters." | Reset filters |
| Error | "Couldn't load your history." | Try again |
| Offline | "You're offline. Your entries are still here." | Try saving again |
| Session expired | "Your session ended. Sign in again to open your logs." | Sign in |
| Success | "Log saved." | View log |

## 4. Dialogs and sheets

| ID | Trigger | Content and action | Cancel / Back |
|---|---|---|---|
| D01 Delete log | S08 | Names the category and date; "Your statistics and XP will be recalculated."; a destructive Delete button | Cancel, data unchanged |
| D02 Sign out | S14 | "You'll need to sign in again to open your logs."; Sign out | Cancel |
| D03 Discard draft | Dirty editor + Back | "Your changes aren't saved."; Discard changes | Keep editing |
| D04 Conflict | PATCH/DELETE 409 | "This log has changed. Load the latest version before you try again."; Load the latest version | Stay on the draft; nothing is overwritten |

Destructive buttons use the error color, not the regular CTA color. An active request disables repeat confirmation. Server errors show in the dialog or page; the dialog never closes as if it succeeded. Escape in the HTML acts as cancel. Focus returns to the trigger after cancelling.

## 5. Component catalog and Compose mapping

| ID | Component | Key properties | Variants / states | Compose |
|---|---|---|---|---|
| C01 | Wordmark | text | Light/dark | Text + Plus Jakarta Sans ExtraBold |
| C02 | App bar | title, back, actions | Root/detail | TopAppBar |
| C03 | Primary button | label, enabled, loading | Default/pressed/focus/disabled/loading | Button |
| C04 | Secondary button | label | Default/disabled/focus | OutlinedButton |
| C05 | Text action | label | Normal/destructive | TextButton |
| C06 | Icon button | icon, contentDescription | Selected/disabled | IconButton |
| C07 | Log manually | label | Opens S05 | Supporting OutlinedButton; C35 for scanning |
| C08 | Text field | value, label, helper, error | Empty/filled/focus/error/disabled | OutlinedTextField |
| C09 | Password field | value, visibility | Masked/visible/error | TextField + visual transformation |
| C10 | Search | query, onClear | Empty/query/no result | OutlinedTextField |
| C11 | Quantity field | mode, unit, rawText, error | g/kg/items; Reduce helper | TextField + single-choice unit |
| C12 | Category picker | selected, options | Default/selected/error | ExposedDropdownMenuBox or a sheet |
| C13 | Method selector | method | Four single-choice methods | RadioButton or a choice list |
| C14 | Filter chip | label, selected, onRemove | Selected/unselected/disabled | FilterChip/InputChip |
| C15 | Range selector | 7d/30d | Selected/loading | SingleChoiceSegmentedButtonRow |
| C16 | Method badge | method, label | Reduce/Reuse/Recycle/Trash | Surface + Text; not an action |
| C17 | Waste log row | log, onClick | Default/pressed | ListItem / Surface |
| C18 | Summary surface | label, value, unit, caption | Zero/value/unknown | Surface + Text |
| C19 | XP progress | current, target, level | Zero/partial/full | LinearProgressIndicator |
| C20 | Achievement row | title, current, target | Locked/unlocked | ListItem + ProgressIndicator |
| C21 | Guide row | title, category, method | Default/pressed | ListItem |
| C22 | Bar chart | dailyValues, unit, labels | Empty/data | Canvas + semantics + text alternative |
| C23 | Method breakdown | four counts, total | Empty/data | Rows + progress + label |
| C24 | Bottom navigation | route, destinations | Selected/unselected | NavigationBar |
| C25 | Navigation rail | route, destinations | Medium/expanded | NavigationRail |
| C26 | Modal sheet | title, content, actions | Open/closed/scroll | ModalBottomSheet |
| C27 | Confirmation | title, body, primary, cancel | Default/destructive/busy/error | AlertDialog |
| C28 | Snackbar / banner | message, action | Success/error/offline | SnackbarHost / Surface |
| C29 | Async state | mode, message, onRetry | Loading/empty/error | Shared composable |
| C30 | Reminder switch | checked, onChange | Off/on/denied | Switch |
| C31 | Date / time input | value, range | Valid/invalid/disabled | DatePicker / TimePicker |
| C32 | Photo attachment | uri, status | Empty/preview/upload/error | Photo Picker + image surface |
| C33 | Source link | title, URL | Reviewed/draft | TextButton / open URI |
| C34 | Category icon | category, description | Seven categories | VectorDrawable / Icon |

Map component shapes to tokens rather than blindly copying CSS px into dp or sp. Components that only appear once can stay inside their screen; the catalog is a design language, not an instruction to build 34 Kotlin wrappers.

## 6. Required state matrix

| Screen | Loading | Empty | Content | Error | Extra cases |
|---|---|---|---|---|---|
| S04 Home | First load / refresh | Nothing today | Summary + list | Retry / banner | Null percentage |
| S07 History | First / next page | New / filtered | Date groups | First / load more | Scroll and filters kept |
| S08 Detail | Fetch | Guided 404 | Record | Retry | Deleted / stale version |
| S11 Guide | Search / list | No results | Articles | Retry | Old search responses |
| S12 Guide detail | Fetch | Article unavailable | Steps and sources | Retry | Drafts not published |
| S13 Statistics | Load / range | Empty range | Aggregates | Retry | Mixed units / Reduce only |
| S14 Profile | Fetch | Zero progress | Account and progress | Retry | Session ended |
| S15 Achievements | Fetch | Definitions not available | Progress | Retry | Capability disabled |
| S05/S09 Form | Submit | The starting form, not an empty page | Entries | Inline / server | Dirty, timeout, conflict |

## 7. Handoff and design review

Reviewers check every ID in the HTML, then match the product rules against the PRD and API. The intentional differences between the HTML and Android: the HTML uses browser form controls, native dialogs, and simulated data; Android uses Compose components and the real API. The HTML doesn't prove authentication, private uploads, Android scheduling, server ownership, or device performance.

Checklist: every field has a label; every action has a destination or feedback; color is never the only status signal; error text is readable; large fonts don't cover CTAs; charts explain their units; data sources and feature options are visible; every sub-screen can go back without losing filters or drafts that should be kept.

## 8. AI camera bottom sheet, S21 to S25

The 28 September revision added the Log/Scan action at the top center of the dock while keeping the five tabs. It's an action button, not a sixth tab, and doesn't use aria-current. The sheet is opaque, with 28 top corners, a decorative drag handle, a close button of at least 48 dp, a title, and a manual fallback. The camera only starts after a user action and permission. Android may ask for permission as soon as the scan action is tapped; the HTML has a Turn on camera button so the demo never triggers a permission prompt without a choice. Close, Escape, and Back stop the camera and return focus to the trigger.

| ID | Panel / focus | Fields / actions | Validation and states |
|---|---|---|---|
| S21 | Camera and photo review | Preview, turn on camera, take photo, review, scan, retake, manual | Permission denied, unavailable, capture not ready; send only after confirmation |
| S22 | Detection / confirm one object | Boxes + list, scores, object choice, category correction, confirmation | Empty, low confidence, unknown material, multiple objects; the category can't be empty |
| S23 | Object condition | Cleanliness, integrity, reusable, reuse intention; cleanable and soiling when dirty, former_contents for HDPE, and risk_type when there's a risk | UNKNOWN is valid; NEEDS_CONTEXT asks for the relevant fields; changes cancel old advice |
| S24 | Recommendation | One Reduce/Reuse/Recycle/Trash label, reason, guide source and version, cautions, change or use | No auto-save; special_handling adds a warning without removing the method; continue to S05 for a manual amount |
| S25 | Failed / unavailable | Cause, retry, manual, close | Camera denied, unknown, offline, timeout, invalid LLM output, no reviewed guide; never adds XP |

S21 to S25 are phases of one sheet flow in the product; they appear as five gallery entries for handoff. The HTML preview uses a labeled bounding box illustration, not boxes over a camera photo. Scores, objects, and the LLM are fixtures. The browser camera uses getUserMedia when the context is secure; nothing is uploaded. Android camera and API testing stay separate.

S05 after AI: only the confirmed category and method are prefilled; the amount is empty, the unit defaults to g, and the date follows the core rules. The user can correct everything. Cancelling a scan never creates a log. An existing dirty draft must be kept or confirmed with D03 before it's replaced; never force navigation and discard entries. Default guides that aren't verified in the mockup never become production knowledge.

| Extra component | Compose equivalent | Behavior |
|---|---|---|
| C35 Scan action | Button / FAB | Lime, camera icon + Log/Scan label, touch target of at least 48 dp |
| C36 Camera sheet | ModalBottomSheet + camera preview | Adaptive height, scrolling, permission, capture and retake, manual fallback |
| C37 Detection review | Canvas overlay + selectable list | Boxes follow the image transform; accessible labels name the object and score |
| C38 Object context | Select / radio controls | Three separate dimensions plus unknown; never infers condition from the image |
| C39 Recommendation review | Surface + method selection | One label, sources, reason, cautions; special handling is an extra warning; confirm before saving |

Prototype states can be chosen on S21: one object, several objects, low score, unrecognized, special handling, network failure, failed recommendation. All of them work without camera hardware through "Use simulated example". That button is for documentation only and never appears as a production feature. Refreshing clears the photo and temporary state.

## 9. Mockup refinements in v1.3

S21 to S25 use the same sheet components, with real fields and actions as inert previews. Open "Try the bottom sheet" to interact. The Photo → Object → Condition → Advice steps have a current-step indicator. Simulation scenario settings sit in a disclosure so the camera path is easier to read. Log manually sits in an opaque footer pinned to the bottom of the sheet.

The category, object, condition, and reuse intention are kept when going back to correct something. Changing the category resets the condition so it doesn't reuse the previous item's context. Choosing the simulated example stops an active camera and discards late permission results. A new scan from the catalog is refused with a message while the editor has a dirty draft; the user saves or goes back through D03 first. The camera and LLM stay simulated, as in v1.2.

### Conditional advice for blank conditions, policy-06

Ordinary context the user hasn't filled in stays UNKNOWN or null. allow_conditional defaults to true, allowing READY with recommendation_basis=CONDITIONAL and conditions_to_verify; false keeps the strict NEEDS_CONTEXT mode. The UI shows provisional advice and its conditions, without filling in blank data or saving automatically. Ambiguous risks, hazardous contamination, and HDPE former contents stay restricted. See the [latest behavior, reproduction, and test results](https://github.com/waste-not-app/waste-not-ai/blob/main/docs/archive/CONDITIONAL_AND_BACKGROUND.md).
