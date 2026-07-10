# Regimen — Architecture

Regimen is a local-only Android gym-tracking application. Users define **routines** (workout
templates) in advance, then **record** actual workouts against them — sets, reps, and weight —
with optional cardio, rest timing, and progress tracking. There is no backend, account system, or
network dependency; all data resides on the device.

This document is the architectural reference for the application: its screens, navigation, the
core workout loop, key product decisions, and the data model. It reflects the v1 scope.

---

## Core concepts

| Concept | What it is |
|---|---|
| **Exercise** | A movement definition (e.g. Barbell Bench Press). Has a **type**: `strength` or `cardio`, a muscle group, equipment, and a built-in vs. custom flag. |
| **Cardio activity** | A cardio-type exercise (Treadmill, Running, Cycling, Rowing…). Logged into a session with **duration + distance** instead of sets/reps/weight. **Session-only — never part of a routine.** |
| **Routine (Template)** | A named, ordered list of **strength** exercises with target sets/reps/rest. Built ahead of time; workouts start from it. |
| **Session (Workout)** | An actual workout performed on a date — the log of sets × reps × weight per exercise, plus any cardio. Usually created from a routine. |
| **Body metric** | Bodyweight and user-defined body measurements tracked over time. |

**Logging model:** template-driven. The primary flow is: define a routine → start a workout from
it (exercises pre-filled) → record set data. Established users are also offered a secondary,
freeform **Quick workout** entry point (see [Workout entry](#workout-entry)).

---

## Navigation

Single-Activity, Jetpack Compose, Navigation Compose (type-safe routes). Top-level navigation is
a **bottom tab bar** with five destinations:

1. **Home** · 2. **Routines** · 3. **History** · 4. **Progress** · 5. **Settings**

**Active Workout is not a tab.** It is a full-screen destination launched from Home or Routines,
backed by a foreground service. While a workout is in progress, navigating elsewhere surfaces a
persistent "workout in progress" banner that returns the user to the active session.

---

## Screen inventory

### Tab 1 — Home

- **S1. Home** — greeting, primary **Start Workout** CTA, **quick-start routine chips** (tapping
  a recent routine begins a session immediately), a **this-week summary** (workouts / volume /
  time), and a **streak** indicator. No recent-workouts list; that is provided by the History
  tab.
  - **Empty state (new user, no routines):** minimal — one line of text and a CTA leading into
    **Create your first routine** (guided). This is the sole cold-start path; no seeded routines
    or demo data are provided.
  - **Established user:** also surfaces a secondary, de-emphasized **Quick workout** (freeform)
    entry.
  - _Built in #14:_ time-of-day greeting, primary **Start Workout** CTA, quick-start routine
    chips (ordered most-recently-used first), a **this-week** card (workouts / volume / time)
    plus a weekly **streak**, and the freeform **Quick workout** entry for established users
    (≥1 completed workout). New-user empty state routes to **Create your first routine** (real
    navigation). At the time of this build, the workout-start actions displayed a "coming soon"
    snackbar pending the Active Workout implementation (S13), which landed in #15; the
    empty-state Create-routine CTA was fully wired from the start.

### Tab 2 — Routines
- **S2. Routines List** — a flat, **reorderable** list of saved templates. No folders in v1.
- **S3. Routine Editor** — create or edit a routine: name, add/reorder exercises, per-exercise
  target sets/reps/rest. Uses the shared Exercise Picker (S16). **Strength exercises only**;
  cardio is excluded from templates.

### Tab 3 — History

- **S4. History** — **calendar view**: a month grid with workout days marked; tapping a day
  opens Session Detail. (A chronological list view is deferred to a later version.)
- **S5. Session Detail** — read-only view of a past workout. Actions: **Repeat workout**, **Save
  as routine**, edit, delete.
  - _Built in #12:_ read-only view, **Save as routine** (strength exercises only), and
    **Delete**.
  - _Built in #15 (Phase 3c):_ **Repeat** (start the same workout again — from its routine, or a
    freeform clone with prior numbers prefilled) and **Edit** (reopen the session in Active
    Workout). Both open Active Workout. Edit is not gated on another workout being in progress —
    editing historical data does not conflict with a live session running elsewhere (see the
    "Edit re-timestamps a past session" entry under Deferred/backlog for the underlying
    decoupling).

### Tab 4 — Progress

- **S6. Progress Overview** — a **PR list** (records per exercise) and a **workout-frequency
  chart**. No estimated-1RM or volume-trend charts in v1.
  - _Built in #13:_ PR list (heaviest weight per exercise, formatted to the unit preference) and
    an 8-week workout-frequency trend (shared `LineChart`), with the Body Measurements entry
    point retained on the tab root. Empty state applies until the first workout is completed.
- **S8. Body Measurements** — **bodyweight** (built-in) plus **user-defined custom measurement
  types** (e.g. waist, arm, body-fat %), each with a trend chart. No fixed preset list; users add
  the types they require.
  - **S8a. Add Measurement Entry** — bottom sheet.

> There is no separate per-exercise progress chart screen in v1. Per-exercise history and PRs
> are shown in Exercise Detail (S11).

### Tab 5 — Settings

- **S9. Settings** — **units** (metric/imperial: kg/lb + km/mi), **theme** (light / dark / system
  plus a dynamic-color toggle), **rest-timer default duration**, **custom measurement type
  management**, an entry point to the Exercise Library, and **data export to JSON** (deferred).
- **S10. Exercise Library** — all exercises (built-in and custom). Ships with a **curated
  ~50–100 built-in set** (common barbell/dumbbell/machine/bodyweight movements, **plus cardio
  activities**). Filterable by **type** (strength/cardio), **muscle group**, and **equipment**,
  with **free-text search**. No favorites in v1. Also serves as the picker source (S16).
- **S11. Exercise Detail** — description, target muscles, and **per-exercise history, PRs, and
  best set**.
- **S12. Add/Edit Custom Exercise** — create or edit a user-defined **strength** exercise. Cardio
  is predefined-only in v1; no custom cardio.

### Cross-cutting / modal screens

- **S13. Active Workout** (full screen) — the core loop. See the
  [detailed spec](#s13-active-workout--detailed-spec).
  - _Built:_ per-set logging (weight/reps/completion), skip/include, adding and removing
    exercises via the picker, cardio entry, session notes, a live session timer, discard/finish
    (Phase 1); rest timer (Phase 2); an in-progress **Resume banner**, single-active-workout
    resume, process-death resume, and a `POST_NOTIFICATIONS` request (Phase 3a); a **foreground
    service with a persistent Pause/End notification and a Pause action** (Phase 3b — the
    session timer and recorded duration exclude paused time); Session-Detail Repeat/Edit
    (Phase 3c).
- **S14. Rest Timer** — bottom sheet/overlay within Active Workout. **Started manually** (no
  auto-start); adjustable duration, defaulting to the routine's per-exercise rest target. On
  completion: **vibration, an audio chime, and a system notification** (using the user's default
  sound).
  - _Built in #15 (Phase 2):_ per-exercise Rest button opens a bottom-sheet countdown defaulting
    to the routine's rest target (falling back to the global rest default), adjustable in ±15s
    increments, running alongside the session timer; on completion, vibration plus the default
    notification sound. The notification renders on Android versions below 13 unconditionally,
    and on 13+ once `POST_NOTIFICATIONS` is granted (Phase 3).
- **S15. Workout Summary** — post-finish recap: duration, total volume, sets, and PRs achieved.
  For a freeform Quick workout, offers **Save as routine**.
  - _Built in #15 (Phase 1)._
- **S16. Exercise Picker** — reusable bottom sheet for adding exercises; used by the Routine
  Editor (S3) and Active Workout (S13). Provides search, multi-select, and a link to add a
  custom exercise (S12). **Context-filtered:** routines show strength exercises only; Active
  Workout shows all exercises, including cardio.
- **S17. Onboarding** (optional, first-run) — minimal: units and theme, always skippable.

---

## S13 Active Workout — detailed spec

The core loop, and the highest-risk screen; users spend the majority of session time here.

- **Entry** — from a saved routine (exercises pre-filled) or a freeform **Quick workout**
  (established users only). New users are guided to create a routine first.
- **Session timer** — total elapsed workout time; starts on session begin and **runs
  continuously** until finish. *Resting does not pause it.* A distinct **Pause** action
  (available in-app and from the persistent notification) can pause or resume the whole session.
- **Rest mode** — started **manually** via a Rest button; there is no auto-start on set
  completion. The rest countdown runs alongside the session timer; default duration is taken
  from the routine's per-exercise rest target and is adjustable.
- **Per-set logging** — each exercise lists its sets with freely editable **reps and weight**.
  Sets can be added or removed at any point. Each set has a **completion checkoff** as a progress
  indicator, independent of the rest timer (since rest is manual).
- **Prefill** — each exercise's sets are prefilled from **the most recent session of the same
  routine**. For a freeform workout, or a newly added exercise with no routine history, fields
  default to blank.
- **Skip** — an exercise can be marked **skipped** (greyed out, labeled) and un-skipped
  mid-workout. If left skipped, it is recorded in history as skipped (an adherence signal) rather
  than removed.
- **Cardio** — a cardio activity (from the library's cardio list) can be added to the session,
  recording **duration and distance**. Cardio entries are session-only; they are never part of a
  routine.
- **Other** — exercises can be added or removed mid-session (via S16); per-exercise and
  per-session notes are supported; completed sets are checked for PRs.
- **Resilience** — the screen **must survive process death, rotation, and backgrounding.** The
  in-progress session is persisted to Room continuously, not only on finish.
- **Persistent notification (foreground service)** — while a workout is active, an ongoing
  notification exposes **Pause** and **End workout** actions and backs the continuously running
  timer. Requires a foreground service and the `POST_NOTIFICATIONS` permission (Android 13+).
- **Finish** → navigates to S15 Workout Summary.

---

## Key decisions (v1 scope)

### Onboarding and empty states

- **Onboarding:** minimal — at most 2–3 screens (units and theme, optional), always skippable.
- **Empty states:** minimal and functional throughout — a short line of text and a single CTA,
  no illustrations.

### Workout entry

- The primary path is **routine-driven**; new users are directed to guided create-first-routine.
- A secondary freeform **Quick workout** is available to established users. A freeform session
  can be **saved as a routine** afterward.

### Workout mechanics

- **Supersets:** deferred to v2. v1 processes exercises sequentially. The data model is designed
  so supersets can be introduced later without a disruptive migration (see below).
- **Per-set checkoff:** retained, as a progress indicator.
- **Warm-up sets and plate calculator:** out of scope for v1.

### Exercises and routines

- **Built-in library:** a curated set of approximately 50–100 movements ships with the
  application; users may add custom strength exercises.
- **Library taxonomy:** filterable by type, muscle group, and equipment, with text search. No
  favorites in v1.
- **Routine organization:** a flat, reorderable list; no folders in v1.
- **PR definition:** **heaviest weight lifted** per exercise (derived, not stored).

### System and polish

- **Theming:** **dynamic color (Material You)** on Android 12+, with a branded fallback palette
  below that; light/dark follows the system setting and is user-overridable.
- **Units:** a global **metric/imperial** preference governs weight (kg/lb) and cardio distance
  (km/mi). Values are **stored canonically** (weight in kg, distance in meters) and converted
  only at display/entry time, so switching units never loses precision.
- **Rest alert:** vibration, an audio chime, and a system notification (using the user's default
  sound).
- **Active-workout notification:** persistent, foreground-service-backed, with Pause and End
  actions.
- **Data export:** deferred, but designed toward **JSON** (full-fidelity backup and re-import).

---

## Reusable components
- **Exercise Picker sheet (S16)** — shared by the Routine Editor and Active Workout.
- **Set-entry row** — weight/reps input and completion toggle; used in Active Workout.
- **Chart component** — used by Progress (frequency) and Body Measurements (bodyweight and
  custom-measurement trends).
- **Empty states** — first-run Routines, History, and Progress.

---

## Adaptive / foldable support

Rolled out **screen by screen**, not in a single pass. Shared infrastructure lives in
`ui/adaptive/WindowAdaptive.kt`:

- `RegimenPosture` (`Compact` / `Tabletop` / `BookOrExpanded`) — Regimen's own simplified layout
  classification, derived from `androidx.compose.material3.adaptive`'s
  `currentWindowAdaptiveInfo()` (`windowPosture` and `windowSizeClass`).
- `LocalRegimenWindowInfo` (a `CompositionLocal`) and `ProvideRegimenWindowInfo { }` — provided
  once in `MainActivity.setContent`, wrapping both the Onboarding gate and `RegimenApp`, so any
  descendant screen can read `LocalRegimenWindowInfo.current` without navigation-argument
  plumbing.

**Convention:** every screen's `BookOrExpanded` width cap uses
`WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp` (from `androidx.window.core.layout`) rather than
a hardcoded `600.dp` literal, since it is the same breakpoint `classify()` uses to promote a
window to `BookOrExpanded` in the first place, not an arbitrary value. Other per-screen magic
numbers (e.g. Home's `480.dp`/`960.dp` dashboard caps, Routine Editor's `420.dp` stepper-row
threshold) are legitimately screen-specific and have no shared constant to reference; these
remain as commented literals.

Dependencies (versions pinned directly in `libs.versions.toml`; the first two are not included in
the Compose BOM, and the third tracks `material3`'s own version in lockstep):
`androidx.window:window:1.5.1`, `androidx.compose.material3.adaptive:adaptive:1.2.0`,
`androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha23` (reuses the
`material3Expressive` version reference).

The app shell (`RegimenApp.kt`) uses `NavigationSuiteScaffold` for the five-tab navigation; its
`layoutType` is driven by `RegimenPosture.toNavigationSuiteType()` (Compact/Tabletop →
`NavigationBar`, BookOrExpanded → `NavigationRail`) rather than
`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo`, for consistency with the rest of the
rollout; it is kept as its own named mapping function to provide a seam for a future override.
`NavigationSuiteType.NavigationDrawer` (rendered as a `PermanentDrawerSheet`) is supported by the
library but deliberately not used: this is out of scope, since desktop-class widths are not a
target — only foldable/book posture is — and Google's own default logic does not auto-select a
drawer either (only ever Bar or Rail).

`navigationSuiteColors` pins `navigationRailContainerColor` to
`MaterialTheme.colorScheme.surfaceContainer`. `NavigationRail`'s own default is
`colorScheme.surface`, while `NavigationBar` and a collapsed `MediumTopAppBar` both default to
`surfaceContainer`; without this override, the rail (BookOrExpanded) appeared visually
inconsistent with the bar (Compact/Tabletop) and with the collapsed top bars added across
Routines, History, Progress, and Settings.

Rollout checklist (updated as each screen is adapted):

```
[✓] App shell (RegimenApp.kt) — NavigationSuiteScaffold: NavigationBar for Compact/Tabletop,
    NavigationRail for BookOrExpanded; WorkoutInProgressBanner docked at the bottom of the
    content pane in both, with .navigationBarsPadding() added after a screenshot showed it
    flush against the bottom edge in Rail mode. No NavigationDrawer tier (out of scope).
    RegimenNavHost content is width-capped and centered at 600dp for both Compact and Tabletop
    (Tabletop retains the bottom bar regardless of actual width, and can be genuinely wide — a
    half-opened, horizontal-hinge AVD state was confirmed at approximately 852dp); only
    BookOrExpanded is full-bleed. Verified on-device.
[✓] Onboarding (S17) — tabletop: navigation controls in the bottom pane, content/title in the
    top pane; book/expanded: content constrained to a 600dp maximum width and centered;
    compact: unchanged.
[✓] Home (S1) — BookOrExpanded: the week/month summary and frequency/bodyweight charts are
    arranged side by side (960dp maximum width, centered) rather than simply width-capped.
    Tabletop is treated identically to Compact (a scrollable dashboard, with no fixed
    hinge-adjacent controls to protect). Empty states were also added for the frequency and
    bodyweight charts instead of hiding them (text only for frequency; text plus a "Log
    bodyweight" CTA into Body Measurements for bodyweight). Verified on-device.
[✓] Routines (S2) / Routine Editor (S3) — both capped at 600dp and centered on BookOrExpanded
    (a list-detail split was considered and rejected — see docs/todo-foldable-rollout.md);
    Compact/Tabletop unchanged. Routine Editor's Sets/Reps/Rest steppers measure per-card width
    via `BoxWithConstraints` (a single row at ≥420dp, otherwise the original two-row split)
    rather than keying off posture. Verified on-device.
[✓] History (S4) / Session Detail (S5) — both capped at 600dp and centered on BookOrExpanded
    (History's seven-column month grid required this most, to keep day cells at a reasonable
    tap-target size); Compact/Tabletop unchanged. Verified on-device.
[✓] Progress (S6) / Body Measurements (S8) — all three capped at 600dp and centered on
    BookOrExpanded (the same `LazyColumn`-of-cards shape as Session Detail/Routine Editor);
    Compact/Tabletop unchanged. Measurements List's empty state uses the 480dp cap, matching
    Home/Routines. The Add Measurement Entry sheet (S8a) was not width-capped; it received the
    compact-landscape scroll fix instead (see below), as did the Exercise Picker (S16). Verified
    on-device.
[✓] Settings (S9) — capped at 600dp and centered on BookOrExpanded, consistent with
    Onboarding/Routines/History/Progress; Compact/Tabletop unchanged. The top app bar's
    collapse-on-scroll behavior is unaffected, as it lives on the Scaffold's modifier, outside
    the capped content. Verified on-device.
[✓] Exercise Library (S10) / Exercise Detail (S11) — both capped at 600dp and centered on
    BookOrExpanded, consistent with the rest of the rollout; Compact/Tabletop unchanged. Add/Edit
    Custom Exercise (S12) required no change, as it was already unaffected by the modal-sheet
    compact-landscape issue. Verified on-device.
[✓] Active Workout (S13) — top bar switched to `MediumTopAppBar` with
    `exitUntilCollapsedScrollBehavior()`, for consistency with other nested/detail screens. The
    exercise list and the floating toolbar are capped together at 600dp and centered on
    BookOrExpanded, so the toolbar does not exceed the width of the content above it; Compact/
    Tabletop unchanged. No Tabletop hinge split was required: the toolbar is already anchored to
    the absolute bottom edge, by the same reasoning that already applies to the bottom
    navigation bar. Verified on-device.
[ ] Rest Timer (S14, embedded in Active Workout) — not yet independently adapted; assessed as
    likely low-risk (a short, fixed-content sheet) per the modal-sheet survey below, but
    unverified.
[✓] Workout Summary (S15) — capped at 600dp and centered on BookOrExpanded, consistent with the
    rest of the rollout; Compact/Tabletop unchanged. Switched to `MediumTopAppBar` with
    `exitUntilCollapsedScrollBehavior()` for consistency with other detail/recap screens.
    Verified on-device.
[✓] Exercise Picker (S16) — no width cap required (same reasoning as S8a above); already
    unaffected by the modal-sheet compact-landscape issue via the pinned-header/scroll-body/
    pinned-footer fix. Reused as-is by Active Workout.
```

Exercise Library/Detail remains a candidate for a true list-detail split via
`androidx.compose.material3.adaptive:adaptive-layout`'s `ListDetailPaneScaffold`, should that be
pursued — a separate artifact and decision from the shared infrastructure above, which Onboarding
does not use (it has no list/detail shape). Routines was also considered but rejected in favor of
the simpler width-cap pattern (see the rollout checklist above and
`docs/todo-foldable-rollout.md`): a genuine pane split would require reworking the List/Editor
navigation structure and dropping the existing container-transform shared-element animation
between them.

---

## Data model (Room entities)

```
Exercise(id, name, type, muscleGroup, equipment, isCustom)
    type = strength | cardio

Routine(id, name, position)
RoutineExercise(id, routineId, exerciseId, position, targetSets, targetReps, targetRestSec)

Workout(id, startTime, endTime, note, routineId?)
WorkoutExercise(id, workoutId, exerciseId, position, isSkipped)

SetEntry(id, workoutExerciseId, setNumber, weightKg, reps, isComplete)
    strength WorkoutExercises only; weight stored canonically in kg

CardioEntry(id, workoutExerciseId, durationSec, distanceMeters?)
    cardio WorkoutExercises only; distance stored canonically in meters

MeasurementType(id, name, unit, isBuiltIn)   -- "Bodyweight" is built-in
BodyMetric(id, measurementTypeId, date, value)
```

**Notes**

- **PRs** are derived (`max(weightKg)` per exercise), not stored, for the initial
  implementation.
- **Supersets (future):** add a nullable `supersetGroupId` to `RoutineExercise` and
  `WorkoutExercise`, and keep ordering position-based, so grouping can be layered on without a
  disruptive migration.

---

## Tech stack

| Area | Choice |
|---|---|
| Language / build | Kotlin, Gradle Kotlin DSL, version catalog, **KSP** (kapt is maintenance-only) |
| UI | Jetpack Compose, **Material 3 Expressive**, single-Activity |
| Navigation | Navigation Compose (type-safe routes) |
| Architecture | **MVVM + UDF** with a **full use-case (domain) layer** — `ui → domain/use-cases → data/repository → Room DAO`; ViewModels expose immutable `StateFlow` UI state |
| DI | Hilt (`2.57.2`, `androidx.hilt` `1.3.0`) |
| Persistence | Room (`2.8.4`) + Coroutines/Flow; DAOs return `Flow` |
| Active Workout runtime | Foreground service (persistent notification, Pause/End). Needs `FOREGROUND_SERVICE` (+ type) and `POST_NOTIFICATIONS` (Android 13+) |
| minSdk | 26 (Android 8) |

### Verified versions (July 2026)
- Compose BOM `2026.06.00` · AGP `9.2.0` (Gradle 8.11+) · Kotlin `2.3.x` · KSP `2.3.4` ·
  Room `2.8.4` · Navigation Compose `2.9.6` · `androidx.window` `1.5.1` ·
  `androidx.compose.material3.adaptive` `1.2.0` ·
  `androidx.compose.material3:material3-adaptive-navigation-suite` `1.5.0-alpha23`.

### Material 3 Expressive stability caveat

Stable `material3` is version `1.4.0`, which does **not** include the Expressive APIs. The
application overrides the Compose BOM's `material3` version to `1.5.0-alpha23` (the
`material3Expressive` version entry in `libs.versions.toml`, applied to the
`androidx-compose-material3` library alias) and opts into `@ExperimentalMaterial3ExpressiveApi`
where required. This accepts alpha API churn (APIs may change between releases) as a **known,
accepted risk**.

Adopted to date:
- **Theme**: `RegimenTheme` uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
  (`ui/theme/Theme.kt`) instead of plain `MaterialTheme`.
- **Expressive shapes**: the Home streak tile uses `MaterialShapes.Cookie9Sided.toShape()` as a
  decorative icon-badge shape (`ui/home/HomeScreen.kt`).
- **Navigation transitions**: `RegimenNavHost` applies shared-axis-x transitions (slide and fade,
  reversed on pop) via `NavHost`'s `enterTransition`/`exitTransition`/`popEnterTransition`/
  `popExitTransition`, replacing the platform default cross-fade.

Not yet implemented: shape morphing on press, and a true container-transform (as opposed to the
shared-axis slide/fade above); to be revisited if the visual benefit justifies the added
complexity.

---

## Deferred / backlog (post-milestone, not blocking)
Cross-cutting enhancements captured for later; none block the numbered build order.

- **Externalize strings to `strings.xml`.** User-facing text is currently hardcoded in Composables
  throughout. Migrate all of it to `res/values/strings.xml` (enabling localization and
  consistency), using **`<plurals>`** (quantity strings) wherever a count drives wording (e.g.
  "N workouts", "N-week streak", "N exercises", "N reps", "in the last N weeks"). Prefer
  parameterized resources over string concatenation.
- ~~**Remove emoji everywhere.**~~ **Done.** Replaced the "🔥 N-week streak" line on Home with a
  `MaterialShapes`-badged `Icons.Filled.Whatshot` icon (see the streak tile below), and the "🏆
  Personal records" header on Workout Summary with `Icons.Filled.EmojiEvents`. No emoji remain in
  user-facing strings.
- ~~**Adopt Material 3 Expressive (design discussion).**~~ **Done** (see the "Material 3
  Expressive stability caveat" section above for what was implemented: expressive theme/motion
  scheme, expressive shapes, shared-axis-x navigation transitions). Shape morphing and
  container-transform remain open for a future pass.
- ~~**Home screen: split the "This week" card into smaller expressive cards.**~~ **Done.**
  Replaced the single card with a `WeekSummarySection`: three per-stat `StatTile`s (Workouts /
  Volume / Time) in a row, plus a dedicated `StreakTile` styled with the primary container color
  and an expressive-shape icon badge (`ui/home/HomeScreen.kt`). A frequency sparkline was
  considered but omitted from this pass; still open if desired later.
- ~~**Historical-data cutoff in graphs (discussion).**~~ **Done.** Added a shared `HistoryRange`
  enum (4w / 3m / 1y / All, `domain/model/Enums.kt`) and a `HistoryRangeSelector` segmented-button
  component (`ui/components/`), wired into both charts:
  - **Progress frequency chart**: `GetWorkoutFrequencyUseCase` now takes a `HistoryRange`
    (default 3m) instead of a fixed eight-week count; `ALL` spans back to the first logged
    workout.
  - **Measurement trend**: `MeasurementDetailViewModel` filters the trend chart's entries by the
    selected range's cutoff (`HistoryRange.cutoffMillis()`); the entries list below remains
    unfiltered (full history). Downsampling for long series was not implemented; still open if a
    range proves too dense to render well.
- ~~**Edit re-timestamps a past session.**~~ **Done**, then revised: editing a past session
  originally reused the "workout in progress" mechanism (`Workout.endTime` cleared to `null` via
  `ReopenWorkoutUseCase`, so the edit would match the same database query as a live workout). This
  caused editing to spuriously display the global resume banner, block starting a genuinely new
  workout, and start the foreground service's persistent notification — none of which apply to
  editing historical data. This was decoupled as follows:
  - `ReopenWorkoutUseCase` and `Workout.preEditEndTime` were removed (database version bumped to
    5, with a corresponding `MIGRATION_4_5` — see `data/local/migration/Migrations.kt` — since
    the project now exports Room schema history rather than destructively clearing the database
    on every version bump). Editing no longer touches `endTime` or `pausedAt`;
    `SessionDetailViewModel.edit()` opens `ActiveWorkoutRoute(workoutId)` directly, with no
    database write and no "finish your active workout first" guard, since editing historical
    data is unrelated to a live workout running elsewhere.
  - `ActiveWorkoutUiState.isEditingPastSession` is derived from `endTime != null` at load time
    (a workout that already has an end time when opened is being reopened for editing, not
    started live) — the same signal as before, without requiring a database mutation to
    establish it.
  - Because editing no longer clears `endTime`, `observeInProgressId()` (`WHERE endTime IS
    NULL`) no longer matches an edit. The resume banner, the foreground-service controller
    (`ActiveWorkoutServiceController`), and the single-active-workout check all key off that same
    query, so all three were corrected by this one change rather than requiring separate special
    cases.
  - "Done" and "Cancel edit" during an edit now navigate directly (to Workout Summary, or back,
    respectively) rather than routing through `FinishWorkoutUseCase`'s reactive `endTime`-driven
    navigation, since there is nothing to write — editing never changes `endTime`.
  - Editing mode continues to omit the live session timer, Pause/Resume, and the per-exercise
    "Rest" timer button (`ActiveWorkoutUiState.isEditingPastSession`), none of which apply to a
    static past session. The bottom toolbar (below) shows a static "Editing session" label
    instead.
- **Active Workout bottom toolbar (redesign).** The elapsed timer, Pause/Resume, and Finish were
  moved out of the top bar (now title plus close/cancel-edit icon only) into a dedicated floating
  pill toolbar anchored above the bottom edge, over the scrolling content (`ActiveWorkoutToolbar`
  in `ActiveWorkoutScreen.kt`):
  - Built from plain `Surface`-style primitives (shadow, clip, fill) rather than the alpha
    `HorizontalFloatingToolbar` API, which expanded to fill the available height when placed in
    `Scaffold`'s `bottomBar` slot, and whose default shadow elevation was not visible against a
    dark background.
  - Tinted with the theme's primary color (darkened slightly while paused, as a status
    indicator, rather than a fixed color).
  - Pausing and resuming animate a circular color reveal (transitioning from the previous to the
    new color, originating near the Pause/Resume button) together with a small scale change (a
    low-stiffness spring), driven by a continuous `animateColorAsState` for the base fill rather
    than a discrete cutover, to avoid a single-frame flicker of the previous color.
  - Pause/Resume and Finish are `FilledIconButton`s (Finish is icon-only, a checkmark) with
    inverted colors (a light container against the primary-tinted pill) so they read as distinct
    controls. Tapping the pill outside the Finish button also triggers Pause/Resume, following
    the convention of persistent media-playback controls, except while editing a past session.
- ~~**Code structure: non-UI classes under `ui/`.**~~ **Done.** `ActiveWorkoutService`,
  `ActiveWorkoutServiceController`, and `RestAlerts` were moved out of `ui/active/` into a
  dedicated `dev.gouthaman.regimen.service` package (manifest, `RegimenApplication`, and
  `ActiveWorkoutViewModel` updated accordingly). `ui/active/` now contains only Compose UI
  (`ActiveWorkoutScreen`, `ActiveWorkoutViewModel`, `WorkoutSummaryScreen`,
  `WorkoutSummaryViewModel`).
- ~~**Rest-alert sound toggle (Settings).**~~ **Done.** Added `UserPreferences.restChimeEnabled`
  (default on) and a corresponding Settings switch ("Rest timer sound");
  `RestAlerts.fire(chimeEnabled)` skips `playChime()` when disabled. A related issue found during
  verification was also fixed: on Android 8+, notification sound is a *channel* property rather
  than a per-notification property, so gating `playChime()` alone was insufficient, since the
  notification would still play the channel's default sound. `RestAlerts` now creates two
  channels (`rest_timer` with sound, `rest_timer_silent` with `setSound(null, null)`), and
  `notifyDone()` posts to whichever channel matches the preference. Vibration and the
  notification itself fire regardless of the setting.
- ~~**Separate weight vs. distance units.**~~ **Done.** `UserPreferences` now exposes independent
  `weightUnit`/`distanceUnit` fields (each a `UnitSystem`), backed by separate DataStore keys
  with a fallback to the previous single `unit_system` key for existing installs. Settings and
  Onboarding each present two selectors; `SessionFormat`, `MeasurementFormat`, and Active
  Workout's weight/cardio rows apply the appropriate unit independently.
- ~~**Bottom-tab navigation correctness.**~~ **Done**, then hardened following a rotation issue
  identified during the foldable rollout. Two related gaps in the single-`NavHost` design
  (top-level routes are siblings of pushed detail routes) were fixed in `RegimenApp.kt` without
  moving to per-tab nested graphs (reconsidered during the foldable rollout and ruled out — see
  `docs/todo-foldable-rollout.md`; the flat structure was found not to have the defect it
  initially appeared to have):
  1. **Re-tapping the active tab** pops that tab back to its root
     (`navController.popBackStack(dest.route, inclusive = false)`) instead of being a no-op.
  2. **A pushed detail screen keeps its parent tab highlighted.** This is tracked via
     `activeTabIndex` (an `Int?` index into `topLevelDestinations`) rather than by walking the
     live back stack: it is set eagerly at the point of navigation intent (every `navigateToTab`
     call site also calls `onNavigateToTab`, so the tab highlights immediately rather than
     waiting on a destination-changed callback), with a `DisposableEffect`-installed
     `OnDestinationChangedListener` as a fallback for cases with no explicit intent (cold start,
     popping back up to a tab's own root). This replaced an earlier approach that walked
     `navController.currentBackStack` for the most recent top-level entry, after a rotation issue
     was identified: that state was held in a plain `remember`, which reset to `null` on the
     Activity recreation that rotation triggers, and nothing recovered it for a non-top-level
     screen such as Session Detail. This was fixed by switching to `rememberSaveable` and storing
     the index (natively Bundle-savable) rather than the route object itself (route types are
     plain Kotlin objects, not `Parcelable`).

---

## Status

The full data/domain layer (Room entities/DAOs/database plus seed data, Hilt DI, repositories,
DataStore preferences, use cases), the navigation shell, and every screen through Settings are
implemented and verified on-device. The foldable/large-screen rollout
(`docs/todo-foldable-rollout.md`) is complete for every screen, including the **S13 Active
Workout** pass (top-bar consistency and a `BookOrExpanded` width cap; no Tabletop hinge split was
required, as the floating toolbar is already bottom-anchored). Remaining work: the S14 Rest Timer
foldable pass (currently unadapted; assessed as low-risk, given its short, fixed-content sheet)
and the "Deferred / backlog" items above.
