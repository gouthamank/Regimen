# Regimen - Architecture

Regimen is a local-only Android gym-tracking application. Users define **routines** (workout
templates) in advance, then **record** actual workouts against them - sets, reps, and weight -
with optional cardio, rest timing, and progress tracking. There is no backend, account system, or
network dependency; all data resides on the device.

This document is the architectural reference for the application: its screens, navigation, the
core workout loop, key product decisions, and the data model.

---

## Core concepts

| Concept                | What it is                                                                                                                                                                             |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Exercise**           | A movement definition (e.g. Barbell Bench Press). Has a **type**: `strength` or `cardio`, a muscle group, equipment, and a built-in vs. custom flag.                                   |
| **Cardio activity**    | A cardio-type exercise (Treadmill, Running, Cycling, Rowing…). Logged into a session with **duration + distance** instead of sets/reps/weight. Session-only - never part of a routine. |
| **Routine (Template)** | A named, ordered list of **strength** exercises with target sets/reps/rest. Built ahead of time; workouts start from it.                                                               |
| **Session (Workout)**  | An actual workout performed on a date - the log of sets × reps × weight per exercise, plus any cardio. Usually created from a routine.                                                 |
| **Body metric**        | Bodyweight and user-defined body measurements tracked over time.                                                                                                                       |

**Logging model:** template-driven. The primary flow is: define a routine → start a workout from
it (exercises pre-filled) → record set data. Established users (at least one completed workout)
also get a secondary, freeform **Quick workout** entry point.

---

## Module structure

- **`:app`** - composition root: `MainActivity`, `RegimenApplication`, `RegimenApp`/
  `RegimenAppViewModel`/`MainViewModel`, `ui/navigation/{RegimenNavHost,Destinations}.kt`,
  `di/CoroutinesModule.kt` (the app-wide `@ApplicationScope` coroutine-scope provider), and
  `service/*` (the Active Workout foreground service, its controller, and `RestAlertsImpl`).
- **`:core:domain`** - pure-Kotlin use-case/model/repository-interface layer
  (`domain/{model,usecase,repository,util,di,service}/`). Zero Android dependency.
- **`:core:data`** - Room DAOs/database/migrations, repository implementations, DataStore
  preferences (`data/{local,repository,prefs}/`).
- **`:core:common-ui`** - shared `@Composable` formatters consumed by multiple features:
  `SessionFormat`, `MeasurementFormat`, `ExerciseLabels`, and `UnitLabelText.kt`'s
  `UnitLabel.text()`.
- **`:core:designsystem`** - themed visual building blocks and adaptive-layout infrastructure, in
  subpackages: `theme/` (`RegimenTheme`, `Color`, `Type`), `dialog/` (`ConfirmDialog`,
  `SaveAsRoutineDialog`, `ExercisePickerSheet`), `chart/` (`LineChart`/`Sparkline`,
  `HistoryRangeSelector`), `component/` (`Stat`, `EmptyState`, `SectionHeader`,
  `UnitSystemSelector`, `ThemeModeSelector`), `adaptive/`
  (`WindowAdaptive.kt`), `dragdrop/` (`ReorderableList.kt`'s drag-to-reorder state/gesture
  helpers).
- **`:core:navigation-api`** - the `@Serializable` route types only; no composables, pure Kotlin.
- **`:feature:{settings,onboarding,exercise,measurements,progress,routines,history,home,active}`**
    - one module per bottom-tab/major screen. Each exposes a `NavGraphBuilder.xGraph()` extension
  that `RegimenNavHost` wires together, except Onboarding, which `MainActivity` shows directly as
      a first-launch gate rather than routing it through `RegimenNavHost`. `:feature:active` now
      holds
      only `ActiveWorkoutViewModel` (consumed by `:app`'s `ActiveWorkoutSheet`, which owns the live
      workout's actual UI - it has no NavHost destination of its own) and Workout Summary;
      `:feature:exercise` additionally hosts `WorkoutExerciseCard.kt` (`ExerciseCard`/
      `WorkoutExerciseRow`
      and their private `SetRow`/`CardioRow` building blocks) shared by the live sheet and
      `:feature:history`'s Edit Workout screen - both depend on `:feature:exercise` already for
      `ExerciseIcon`, and `:core:common-ui` can't host it without a core→feature dependency
      inversion.
- **`build-logic`** - an included build with convention plugins (`regimen.android.library`,
  `regimen.android.library.compose`, `regimen.android.feature`, `regimen.android.hilt`,
  `regimen.jvm.library`) that centralize each module's `compileSdk`/Compose/Hilt/Kotlin
  boilerplate.

**Architecture layering:** `ui (feature modules) → domain (use cases) → data (repositories) → Room
DAO`. ViewModels expose immutable `StateFlow` UI state. Hilt provides dependency injection
throughout; `:core:domain` declares repository interfaces, `:core:data` implements them.

---

## Navigation

Single-Activity, Jetpack Compose, Navigation Compose (type-safe routes). Top-level navigation is
a **bottom tab bar** (or a `NavigationRail` on wide/book/expanded layouts) with five destinations:

1. **Home** · 2. **Routines** · 3. **History** · 4. **Progress** · 5. **Settings**

**Active Workout is not a tab, and the live in-progress workout is not a NavHost destination
either.** It's a persistent two-state draggable sheet (`ActiveWorkoutSheet`, in `:app`) docked
above the tab bar/rail: collapsed, it's a mini-player-style banner; expanded (via drag or tap), it
fills the screen with the full Active Workout UI. There's no third, resting midway state -
releasing mid-drag snaps to whichever end is closer. The sheet itself mounts/unmounts with a
grow/shrink-from-the-bottom-edge transition (`RegimenApp`'s `AnimatedVisibility`, keyed off whether
a workout is in progress at all) rather than abruptly appearing/disappearing - if a workout ends
(Finish/Discard) while the sheet is Expanded, this shrinks the whole full-screen content away
rather than cutting to whatever's behind it. It's backed by a foreground service, and persists
across tab switches since it lives alongside `NavHost`, not inside it. Editing a past
(already-finished) session (Session Detail's "Edit") is a different, simpler screen entirely - Edit
Workout, a normal `EditWorkoutRoute` NavHost destination (owned by `:feature:history`) with the
usual slide transition, since there's no "in progress" state for an edit session to collapse to and
none of the live session's timer/pause/rest-timer/finish machinery applies to editing historical
data. Repeating a past session (Session Detail's "Repeat") instead starts a brand-new live workout
and expands the same `ActiveWorkoutSheet`.

Re-tapping the already-active tab pops that tab back to its own root rather than being a no-op.
A pushed detail screen (e.g. Session Detail) keeps its parent tab highlighted in the bar/rail.

---

## Screen inventory

### Tab 1 - Home

- **Home** - greeting (time-of-day, or a generic fallback before any workout has been logged),
  primary **Start Workout** CTA, **quick-start routine chips** (ordered most-recently-used first;
  tapping one begins a session immediately), a **this-week** summary (workouts / volume / time)
  with a weekly **streak** indicator, a **this-month** summary, a workout-frequency chart (last 4
  weeks), and a bodyweight trend chart (last 4 weeks, with a "Log bodyweight" CTA into Body
  Measurements when no entries exist yet).
  - **Empty state (no routines yet):** a short line of text and a **Create your first routine**
    CTA (switches to the Routines tab). This is the sole cold-start path; no seeded routines or
    demo data are provided.
  - **Established user:** also surfaces a secondary, de-emphasized **Quick workout** (freeform)
    entry alongside routine quick-start chips.

### Tab 2 - Routines

- **Routines List** - a flat, **reorderable** list of saved templates. No folders.
- **Routine Editor** - create or edit a routine: name, add/reorder exercises, per-exercise target
  sets/reps/rest. Uses the shared Exercise Picker sheet. **Strength exercises only** - cardio is
  excluded from templates.

### Tab 3 - History

- **History** - **calendar view**: a month grid with workout days marked; tapping a day opens
  Session Detail, or a picker dialog if multiple sessions happened that day. No chronological
  list view.
- **Session Detail** - read-only view of a past workout (date, duration, per-exercise sets/cardio,
  notes). Actions: **Repeat workout** (starts the same workout again, resuming an in-progress one
  if there already is one), **Edit** (reopens the session in **Edit Workout** - sets/cardio/note
  only, no timer - without touching its original timestamps), **Save as routine** (strength
  exercises only), **Delete**.

### Tab 4 - Progress

- **Progress** - a **personal-records list** (heaviest weight per exercise, or best reps for
  bodyweight-only exercises) and a **workout-frequency chart** with a selectable range (4 weeks /
  3 months / 1 year / all time). A Body Measurements entry point sits at the top of this tab.
- **Body Measurements** - **bodyweight** (built-in) plus **user-defined custom measurement types**
  (e.g. waist, arm, body-fat %), each with a trend chart (same selectable-range control as
  Progress's frequency chart). No fixed preset list beyond bodyweight.
    - **Add Measurement Entry** - bottom sheet.

Per-exercise progress (history and PRs) lives on Exercise Detail, not on a separate screen.

### Tab 5 - Settings

- **Settings** - units (metric/imperial: kg/lb + km/mi, weight and distance selected
  independently), theme (light/dark/system plus a dynamic-color toggle), rest-timer default
  duration, a rest-alert sound toggle, custom measurement type management, and an entry point to
  the Exercise Library. Data export to JSON is not implemented.
- **Exercise Library** - every exercise (built-in and custom), filterable by type
  (strength/cardio), muscle group, and equipment, with free-text search. Ships with a curated set
  of built-in strength and cardio movements. No favorites. Also serves as the exercise-picker
  source.
- **Exercise Detail** - description, target muscles, and per-exercise history, PRs, and best set.
- **Add/Edit Custom Exercise** - create or edit a user-defined **strength** exercise. Cardio is
  predefined-only; no custom cardio activities.

### Cross-cutting / modal screens

- **Active Workout** (full screen) - the core logging loop. See the
  [detailed spec](#active-workout--detailed-spec) below.
- **Rest Timer** - bottom sheet within Active Workout, started manually (no auto-start on set
  completion). Defaults to the routine's per-exercise rest target (or the global default),
  adjustable in ±15s increments, running alongside the session timer. On completion: vibration,
  an optional audio chime (per the rest-alert sound setting), and a system notification.
- **Workout Summary** - post-finish recap: duration, total volume, sets completed, and any PRs
  achieved. For a freeform Quick workout, offers **Save as routine**.
- **Exercise Picker** - reusable bottom sheet for adding exercises, shared by the Routine Editor,
  Active Workout, and Edit Workout. Search, multi-select, and a link to add a custom exercise.
  **Context-filtered:** the Routine Editor shows strength exercises only; Active Workout and Edit
  Workout show all exercises, including cardio.
- **Edit Workout** (full screen, `:feature:history`) - reopens a finished session for editing:
  the same per-exercise set/cardio logging surface as Active Workout (shared via
  `:feature:exercise`'s `ExerciseCard`), plus the session note and add-exercise, but with no
  timer, Pause/Resume, rest timer, or Finish - just a top-bar Done action and a Cancel-edit
  confirm dialog. Editing never changes the session's original timestamps and doesn't touch
  `WorkoutStatus` in a way that could conflict with a genuinely in-progress workout running
  elsewhere.
- **Onboarding** (first-run only) - units and theme selection, always skippable.

---

## Active Workout - detailed spec

The core loop; users spend the majority of session time here.

- **Entry** - from a saved routine (exercises pre-filled) or a freeform **Quick workout**
  (established users only). New users are guided to create a routine first.
- **Session timer** - total elapsed workout time; starts on session begin and runs continuously
  until finish. Resting does not pause it. A distinct **Pause** action (available in-app and from
  the persistent notification) can pause or resume the whole session; the recorded duration
  excludes paused time. Pausing while resting cancels the active rest countdown rather than
  running two timers at once. While paused, logging surfaces are disabled - set/cardio fields,
  add set, add exercise, skip/un-skip, and the Rest button - so no data can be logged against a
  frozen timer; the session note field stays editable (it isn't part of the logged workout data
  pausing is meant to freeze). Resume and discard remain reachable while paused; Finish does not -
  resume first, then finish.
- **Lifecycle** - a workout's session state is an explicit, persisted `WorkoutStatus` (
  `IN_PROGRESS`,
  `IN_REST_TIME`, `PAUSED`, `EDITING`, `COMPLETE`), not inferred from timestamp nullability. This
  makes the rest countdown, pause state, and editing mode independently recoverable across process
  death.
- **Rest mode** - started manually via a Rest button; no auto-start on set completion. Runs
  alongside the session timer, and the countdown itself is persisted (survives process death). The
  rest sheet is undismissable - tapping the scrim, pressing back, or swiping down all do nothing;
  only **Skip rest** closes it early (the countdown otherwise runs until it completes on its own).
- **Per-set logging** - each exercise lists its sets with editable reps and weight. Sets can be
  added or removed at any point. Each keystroke in a weight/reps field discards anything that
  isn't a digit (weight also allows one decimal point) - no other characters can be typed. On
  blur, the field's text is trimmed to its canonical formatting (e.g. "10.00" → "10"), and that
  value fills every later set in the same exercise whose weight (or reps) is still empty - logging
  one heavy top set doesn't require retyping the same number into every set below it.
- **Per-set completion** - a set's checkbox only becomes checkable once its numeric fields are
  actually filled in (for a non-bodyweight exercise, both weight and reps; unchecking is always
  allowed, no validation). Once checked, the set's fields lock - uncheck to edit them again.
- **Prefill** - each exercise's sets, and the session note, are prefilled from the most recent
  completed session of the same routine (the note is a common place to jot which exercises to
  advance next time). For a freeform workout, or a newly added exercise with no history, fields
  start blank.
- **Skip / Done** - a strength exercise can be marked skipped (bypasses all completion checks) or
  done (only once every set is checked complete), each via its own header icon toggle; the two are
  mutually exclusive - marking one hides the other's icon until it's undone. Marking done also
  fires automatically the moment an exercise's last set becomes complete, whether via its checkbox
  or via a rest countdown ending/being skipped, so the common case needs no extra tap. Both skip
  and done collapse the card to a one-line summary (skipped: a plain "Skipped" label; done: each
  set's logged weight/reps) with a tap target (Include / Edit) to reopen it; done additionally
  tints the card with the theme's `tertiaryFixedDim`/`onTertiaryFixed` color pair (see Material 3
  Expressive's Fixed color roles, below), distinct from skip's neutral surfaceVariant tint. A
  left-skipped exercise is recorded in history as skipped (an adherence signal), not removed.
- **Cardio** - a cardio activity can be added to the session, recording duration and distance.
  Cardio entries are session-only; never part of a routine.
- **Other** - exercises can be added or removed mid-session via the picker; per-set completion is
  checked against personal records as it happens; per-session notes are supported.
- **Resilience** - the screen survives process death, rotation, and backgrounding. The
  in-progress session is persisted to Room continuously, not only on finish.
- **Persistent notification (foreground service)** - while a workout is active, an ongoing
  notification exposes a **Pause/Resume** action and backs the continuously running timer; ending
  a workout is in-app only (Active Workout's Finish button), not exposed on the notification, so
  it always goes through that button's incomplete-workout confirmation. Requires a foreground
  service and the `POST_NOTIFICATIONS` permission (Android 13+). Tapping the notification (or the
  rest-complete notification) deep-links straight into that session's Active Workout screen via an
  `Intent` extra (`MainActivity.EXTRA_WORKOUT_ID`) read on cold start (`onCreate`) and warm start
  (`onNewIntent`, since `MainActivity` is `launchMode="singleTop"`) and consumed by `RegimenApp`
  the same way the in-app "Resume" banner navigates.
- **Bottom toolbar** - a floating pill anchored above the bottom edge, bottom-end corner (not the
  top bar). While running, it's a full-width bar tinted with the theme's
  `primaryFixedDim`/`onPrimaryFixed` pair, showing the elapsed timer (+ a breathing live-pulse dot)
  and Pause/Finish buttons. Pausing collapses the whole pill down to a compact "Resume" FAB (icon +
  "Resume" + the paused elapsed time), tinted `secondaryFixedDim`/`onSecondaryFixed` instead - a
  single `AnimatedContent` size-transform morphs between the two shapes as one coordinated motion,
  rather than animating individual buttons' widths within a fixed-size bar. Finish isn't reachable
  from the collapsed paused state - resume first. Tapping anywhere on the pill also toggles
  Pause/Resume (mini-player pattern); the whole pill also presses down slightly on touch-down.
  Editing a past session (Edit Workout) has none of this - see the Edit Workout entry under
  Cross-cutting / modal screens above.
- **Keep screen on** - a top-app-bar action toggles `keepScreenOn` on the window for as long as
  Active Workout is open; ephemeral (resets every time the screen is (re)opened), not a saved
  preference.
- **Finish** - a confirmation dialog (`ConfirmDialog`, see Reusable components) gates the actual
  finish. If every exercise is skipped, done, or has every set checked complete, the dialog's
  confirm button is enabled immediately and colored `tertiary` (a positive/"good to go" cue). If
  something is still unmarked, the dialog's text calls that out and the confirm button stays
  disabled for 3 seconds (with a neutral color) before becoming tappable, so finishing an
  incomplete session takes a deliberate beat rather than a reflexive tap. Confirming → navigates to
  Workout Summary.

---

## Key decisions

### Onboarding and empty states

- Onboarding is minimal - units and theme only, always skippable. Right after it (as soon as
  `RegimenApp` first composes, before any workout can be started), the app requests
  `POST_NOTIFICATIONS` (Android 13+) if not already resolved - this is deliberately not requested
  from within Active Workout itself, since the foreground service's first notification can fire
  before Compose even navigates there.
- Empty states are minimal and functional throughout - a short line of text and (where there's a
  concrete fix) a single CTA, no illustrations.

### Workout entry

- The primary path is routine-driven; new users are directed to guided create-first-routine.
- A secondary freeform Quick workout is available to established users. A freeform session can be
  saved as a routine afterward.

### Workout mechanics

- **Supersets:** not implemented. Exercises are processed sequentially. `RoutineExercise` and
  `WorkoutExercise` both carry a reserved, currently-unused `supersetGroupId` column so grouping
  can be layered on later without a schema migration.
- **Per-set checkoff:** a plain progress indicator, independent of the rest timer.
- **Warm-up sets and a plate calculator:** not implemented.

### Exercises and routines

- **Built-in library:** a curated set of common strength and cardio movements ships with the app;
  users may add custom strength exercises only.
- **Library taxonomy:** filterable by type, muscle group, and equipment, with text search. No
  favorites.
- **Routine organization:** a flat, reorderable list; no folders.
- **PR definition:** heaviest weight lifted per exercise (derived at query time, not stored).

### System and polish

- **Theming:** dynamic color (Material You) on Android 12+, with a branded fallback palette below
  that; light/dark follows the system setting and is user-overridable. Uses Material 3
  Expressive's `MaterialExpressiveTheme`/`MotionScheme.expressive()`. The fallback palette's
  "Fixed" color roles (`primaryFixed`/`primaryFixedDim`/`onPrimaryFixed`, and the secondary/tertiary
  equivalents) are populated from the real M3 baseline tonal values, not invented ones, since Fixed
  roles are theme-invariant by design (same value in light and dark) - used for state-differentiated
  UI (e.g. Active Workout's toolbar/done-card tints, Home's streak tile) where a role that doesn't
  flip between light/dark keeps the color meaning consistent.
- **Typography:** Sofia Sans (variable font, weight 600/700) for display/headline/title styles;
  Outfit (variable font, weight 400/500) for body/label styles - both bundled under
  `core/designsystem/res/font/`.
- **Units:** independent metric/imperial preferences for weight (kg/lb) and cardio distance
  (km/mi). Values are stored canonically (weight in kg, distance in meters) and converted only at
  display/entry time, so switching units never loses precision.
- **Rest alert:** vibration, an optional audio chime (user-toggleable), and a system notification.
- **Active-workout notification:** persistent, foreground-service-backed, with a Pause/Resume
  action only - ending a workout is in-app only.
- **Haptic feedback:** via Compose's `LocalHapticFeedback` (routes through the platform's touch-
  feedback pipeline, so it already respects the system's haptics/vibration setting - nothing here
  can or needs to override that). Used at: drag-to-reorder (a tick on drag-lift, another per swap -
  shared `DragDropState`/`dragHandle`), Active Workout's set-complete checkbox (on check only, not
  uncheck), its exercise skip/done toggles, its toolbar Pause/Resume tap, Routine Editor's
  sets/reps/rest steppers, `ConfirmDialog`'s destructive confirms, and the moment a delayed confirm
  button (see Reusable components) becomes tappable.
- **Data export:** not implemented.

---

## Reusable components (`:core:designsystem`, `:core:common-ui`)

- **Exercise Picker sheet** - shared by the Routine Editor, Active Workout, and Edit Workout.
- **`Stat`** - a labeled value (e.g. "12" over "Workouts"); the building block for stat
  rows/grids on Home and Workout Summary.
- **`ConfirmDialog`** - confirm/dismiss dialog shared by every delete/discard/finish confirmation;
  `destructive = true` colors the confirm button with the error color, `positive = true` colors it
  with tertiary instead (e.g. finishing a fully-logged workout). `confirmEnableDelayMillis` keeps
  the confirm button disabled for that long after the dialog appears, for a confirmation that
  deserves a beat before committing (e.g. finishing with something still unmarked).
- **`SaveAsRoutineDialog`** - prompts for a routine name; shared by Workout Summary and Session
  Detail.
- **`EmptyState`** - a centered message with an optional icon and action button, shared by every
  screen's empty list/search-result state.
- **`SectionHeader`**, **`UnitSystemSelector`**, **`ThemeModeSelector`** - shared section-label
  text and unit/theme segmented-button pickers (the latter two used by both Onboarding and
  Settings).
- **`LineChart`/`Sparkline`** - a self-contained Canvas-based chart, used by Progress (frequency),
  Body Measurements (bodyweight and custom-measurement trends), and Home (frequency +
  bodyweight). Draws on left-to-right (an animated reveal, not a static one-shot draw) whenever the
  underlying points list changes - including first appearance, a range-selector switch, or new data
  landing.
- **`HistoryRangeSelector`** - the 4w/3m/1y/All segmented range selector, shared by the Progress
  frequency chart and the Measurement trend chart.
- **`ReorderableList`** - drag-to-reorder gesture/state helpers (`DragDropState`,
  `rememberDragDropState`, `Modifier.dragHandle`), used by the Routines list and Routine Editor's
  exercise list.
- **`SessionFormat`/`MeasurementFormat`/`ExerciseLabels`** (`:core:common-ui`) - shared
  `@Composable` formatters for durations, weights/reps, distances, and exercise-taxonomy display
  labels.

---

## Adaptive / foldable support

Shared infrastructure lives in `:core:designsystem`'s `adaptive/WindowAdaptive.kt`:

- `RegimenPosture` (`Compact` / `Tabletop` / `BookOrExpanded`) - Regimen's own simplified layout
  classification, derived from `androidx.compose.material3.adaptive`'s
  `currentWindowAdaptiveInfo()` (`windowPosture` and `windowSizeClass`).
- `LocalRegimenWindowInfo` (a `CompositionLocal`) and `ProvideRegimenWindowInfo { }` - provided
  once in `MainActivity.setContent`, wrapping both the Onboarding gate and `RegimenApp`, so any
  descendant screen reads `LocalRegimenWindowInfo.current` without navigation-argument plumbing.

**Convention:** every screen's `BookOrExpanded` width cap uses
`WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp` (from `androidx.window.core.layout`), the same
breakpoint `classify()` uses to promote a window to `BookOrExpanded` in the first place, rather
than a hardcoded `600.dp` literal. A few screen-specific caps have no shared constant to reference
(Home's `480.dp`/`960.dp` dashboard layout, Routine Editor's `420.dp` stepper-row threshold) and
remain as commented literals.

The app shell (`RegimenApp.kt`) uses `NavigationSuiteScaffold` for the five-tab navigation: its
`layoutType` is `NavigationBar` for Compact/Tabletop and `NavigationRail` for BookOrExpanded (no
`NavigationDrawer` tier - desktop-class widths aren't a target). `navigationSuiteColors` pins
`navigationRailContainerColor` to `MaterialTheme.colorScheme.surfaceContainer` so the rail matches
the tone of the bottom bar and collapsed `MediumTopAppBar`s elsewhere.

Per-screen adaptive behavior:

- **App shell** - `NavigationBar` (Compact/Tabletop) or `NavigationRail` (BookOrExpanded); content
  is width-capped and centered at 600dp for Compact and Tabletop (Tabletop keeps the bottom bar
  even when the window is genuinely wide), full-bleed only for BookOrExpanded. `ActiveWorkoutSheet`
  docks at the bottom of the content pane in every posture when collapsed, and covers the full
  screen (regardless of posture) when expanded.
- **Onboarding** - Tabletop splits navigation controls into the bottom pane, content/title in the
  top pane; BookOrExpanded constrains content to 600dp, centered; Compact is unchanged.
- **Home** - BookOrExpanded arranges the week/month summary and frequency/bodyweight charts side
  by side (960dp max width, centered); Tabletop behaves like Compact (a scrollable dashboard, no
  hinge-adjacent controls to protect).
- **Routines / Routine Editor** - both capped at 600dp and centered on BookOrExpanded;
  Compact/Tabletop unchanged. The Sets/Reps/Rest steppers measure per-card width via
  `BoxWithConstraints` (a single row at ≥420dp, otherwise a two-row split) rather than keying off
  posture.
- **History / Session Detail** - both capped at 600dp and centered on BookOrExpanded (History's
  seven-column month grid needs this most, to keep day cells at a reasonable tap-target size).
- **Progress / Body Measurements** - capped at 600dp and centered on BookOrExpanded; the empty
  state uses a 480dp cap, matching Home/Routines.
- **Settings** - capped at 600dp and centered on BookOrExpanded; the top app bar's
  collapse-on-scroll behavior is unaffected, since it lives outside the capped content.
- **Exercise Library / Exercise Detail** - both capped at 600dp and centered on BookOrExpanded.
- **Active Workout** - the exercise list and the floating bottom toolbar are capped together at
  600dp and centered on BookOrExpanded, so the toolbar never exceeds the content's width. No
  Tabletop hinge split is needed - the toolbar is already anchored to the bottom edge, the same
  reasoning that applies to the bottom navigation bar.
- **Edit Workout** - the exercise list is capped at 600dp and centered on BookOrExpanded, same as
  Active Workout (no floating toolbar here to worry about).
- **Workout Summary** - capped at 600dp and centered on BookOrExpanded.
- **Rest Timer** - not independently adapted (a short, fixed-content sheet - low risk).
- **Exercise Picker** - no width cap; already unaffected by the modal-sheet compact-landscape
  issue via a pinned-header/scroll-body/pinned-footer layout.

Exercise Library/Detail is a candidate for a true list-detail split via
`androidx.compose.material3.adaptive:adaptive-layout`'s `ListDetailPaneScaffold`, not currently
implemented.

---

## Data model (Room entities, `:core:data`)

```
Exercise(id, name, type, muscleGroup, equipment, isCustom)
    type = strength | cardio

Routine(id, name, position)
RoutineExercise(id, routineId, exerciseId, position, targetSets, targetReps, targetRestSec,
                supersetGroupId?)

Workout(id, startTime, endTime, note, routineId?, workoutStatus, pausedAt?, accumulatedPausedMs,
        restTimeEndAt?, restTotalSec?, restWorkoutExerciseId?)
    workoutStatus = IN_PROGRESS | IN_REST_TIME | PAUSED | EDITING | COMPLETE - the single source of
        truth for session lifecycle; restTimeEndAt/restTotalSec/restWorkoutExerciseId are non-null
        only while IN_REST_TIME, pausedAt only while PAUSED
WorkoutExercise(id, workoutId, exerciseId, position, isSkipped, isDone, supersetGroupId?)
    isSkipped/isDone are mutually exclusive at the UI level (see Active Workout spec)

SetEntry(id, workoutExerciseId, setNumber, weightKg?, reps?, isComplete)
    strength WorkoutExercises only; weight stored canonically in kg

CardioEntry(id, workoutExerciseId, durationSec, distanceMeters?)
    cardio WorkoutExercises only; distance stored canonically in meters

MeasurementType(id, name, unit, isBuiltIn)   -- "Bodyweight" is built-in
BodyMetric(id, measurementTypeId, date, value)
```

Domain models (`:core:domain`'s `domain/model/`) are plain Kotlin data classes with no
persistence annotations; `:core:data`'s repositories map Room entities to/from them at the
boundary, so `:core:domain` has zero dependency on Room.

**Notes**

- PRs are derived (`max(weightKg)` per exercise, or best reps for bodyweight-only exercises), not
  stored.
- `supersetGroupId` on `RoutineExercise`/`WorkoutExercise` is reserved for future superset
  grouping; currently always null.
- Database version 5.

---

## Tech stack

| Area                            | Choice                                                                                                                                                  |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Language / build                | Kotlin, Gradle Kotlin DSL, version catalog, KSP                                                                                                         |
| UI                              | Jetpack Compose, Material 3 Expressive, single-Activity                                                                                                 |
| Navigation                      | Navigation Compose (type-safe routes)                                                                                                                   |
| Architecture                    | MVVM + UDF with a full use-case (domain) layer - `ui → domain/use-cases → data/repository → Room DAO`; ViewModels expose immutable `StateFlow` UI state |
| DI                              | Hilt                                                                                                                                                    |
| Persistence                     | Room + Coroutines/Flow; DAOs return `Flow`                                                                                                              |
| Active Workout runtime          | Foreground service (persistent notification, Pause/Resume). Needs `FOREGROUND_SERVICE` (+ type) and `POST_NOTIFICATIONS` (Android 13+)                  |
| minSdk / targetSdk / compileSdk | 26 (Android 8) / 37 / 37.1                                                                                                                              |

### Current pinned versions

Compose BOM `2026.02.01` · AGP `9.2.1` · Kotlin `2.2.10` · KSP `2.2.10-2.0.2` · Hilt `2.60.1` ·
Room `2.8.4` · Navigation Compose `2.9.8` · Coroutines `1.11.0` · `androidx.window` `1.5.1` ·
`androidx.compose.material3.adaptive` `1.2.0` ·
`androidx.compose.material3:material3-adaptive-navigation-suite` `1.5.0-alpha23`.

### Material 3 Expressive

Stable `material3` (`1.4.0`) does not include the Expressive APIs. The Compose BOM's `material3`
version is overridden to `1.5.0-alpha23` (the `material3Expressive` version entry in
`libs.versions.toml`), with `@ExperimentalMaterial3ExpressiveApi` opted into where required. This
is a deliberate, accepted alpha-API-churn risk.

Adopted:

- **Theme** - `RegimenTheme` uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
  instead of plain `MaterialTheme`.
- **Expressive shapes** - the Home streak tile uses `MaterialShapes.Cookie9Sided.toShape()` as a
  decorative icon-badge shape.
- **Fixed color roles** - see Theming, above.
- **Navigation transitions** - `RegimenNavHost` applies two different transition schemes,
  distinguished by whether both the departing and arriving destinations are one of the five
  bottom-tab routes:
  - **Hierarchical drill-down** (any push/pop that isn't a tab switch) - shared-axis-x (slide and
    fade, reversed on pop).
  - **Bottom-tab switch** (`navigateToTab`) - a Material "fade through" (the outgoing tab
    fades+shrinks out over 90ms, the incoming tab fades+grows in over 130ms with a 90ms stagger)
    instead of the directional slide, since tabs are parallel destinations, not a hierarchy.
    A single `SharedTransitionLayout` wraps the `NavHost` (only) and hosts every row/link-expand
    container-transform in the app: Routines row/"New routine" FAB → Routine Editor, Exercise
    Library row → Exercise Detail, Measurements row → Measurement Detail, History
    row/single-session day cell → Session Detail, Progress's "Body Measurements" link →
    Measurements, and Settings' "Exercise Library" row → Exercise Library (Settings is Library's
    only entry point, so that one is unconditional; Progress → Measurements is conditional since
    Home's "Log bodyweight" button also opens Measurements with no row to expand from). All the
    transition keys live in `core/common-ui`'s `SharedTransitionKeys.kt`, not scattered per-module,
    since some pairs (Progress/Measurements, Settings/Exercise Library) cross module boundaries
    with neither module depending on the other. The live in-progress workout's collapse/expand
    (`ActiveWorkoutSheet`) is a separate mechanism, not part of this shared-element system - see
    the Navigation section above.

Not implemented: shape morphing on press.
