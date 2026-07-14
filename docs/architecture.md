# Regimen — Architecture

Regimen is a local-only Android gym-tracking application. Users define **routines** (workout
templates) in advance, then **record** actual workouts against them — sets, reps, and weight —
with optional cardio, rest timing, and progress tracking. There is no backend, account system, or
network dependency; all data resides on the device.

This document is the architectural reference for the application: its screens, navigation, the
core workout loop, key product decisions, and the data model.

---

## Core concepts

| Concept                | What it is                                                                                                                                                                             |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Exercise**           | A movement definition (e.g. Barbell Bench Press). Has a **type**: `strength` or `cardio`, a muscle group, equipment, and a built-in vs. custom flag.                                   |
| **Cardio activity**    | A cardio-type exercise (Treadmill, Running, Cycling, Rowing…). Logged into a session with **duration + distance** instead of sets/reps/weight. Session-only — never part of a routine. |
| **Routine (Template)** | A named, ordered list of **strength** exercises with target sets/reps/rest. Built ahead of time; workouts start from it.                                                               |
| **Session (Workout)**  | An actual workout performed on a date — the log of sets × reps × weight per exercise, plus any cardio. Usually created from a routine.                                                 |
| **Body metric**        | Bodyweight and user-defined body measurements tracked over time.                                                                                                                       |

**Logging model:** template-driven. The primary flow is: define a routine → start a workout from
it (exercises pre-filled) → record set data. Established users (at least one completed workout)
also get a secondary, freeform **Quick workout** entry point.

---

## Module structure

- **`:app`** — composition root: `MainActivity`, `RegimenApplication`, `RegimenApp`/
  `RegimenAppViewModel`/`MainViewModel`, `ui/navigation/{RegimenNavHost,Destinations}.kt`,
  `di/CoroutinesModule.kt` (the app-wide `@ApplicationScope` coroutine-scope provider), and
  `service/*` (the Active Workout foreground service, its controller, and `RestAlertsImpl`).
- **`:core:domain`** — pure-Kotlin use-case/model/repository-interface layer
  (`domain/{model,usecase,repository,util,di,service}/`). Zero Android dependency.
- **`:core:data`** — Room DAOs/database/migrations, repository implementations, DataStore
  preferences (`data/{local,repository,prefs}/`).
- **`:core:common-ui`** — shared `@Composable` formatters consumed by multiple features:
  `SessionFormat`, `MeasurementFormat`, `ExerciseLabels`, and `UnitLabelText.kt`'s
  `UnitLabel.text()`.
- **`:core:designsystem`** — themed visual building blocks and adaptive-layout infrastructure, in
  subpackages: `theme/` (`RegimenTheme`, `Color`, `Type`), `dialog/` (`ConfirmDialog`,
  `SaveAsRoutineDialog`, `ExercisePickerSheet`), `chart/` (`LineChart`/`Sparkline`,
  `HistoryRangeSelector`), `component/` (`Stat`, `EmptyState`, `SectionHeader`,
  `UnitSystemSelector`, `ThemeModeSelector`, `WorkoutInProgressBanner`), `adaptive/`
  (`WindowAdaptive.kt`), `dragdrop/` (`ReorderableList.kt`'s drag-to-reorder state/gesture
  helpers).
- **`:core:navigation-api`** — the `@Serializable` route types only; no composables, pure Kotlin.
- **`:feature:{settings,onboarding,exercise,measurements,progress,routines,history,home,active}`**
  — one module per bottom-tab/major screen. Each exposes a `NavGraphBuilder.xGraph()` extension
  that `RegimenNavHost` wires together, except Onboarding, which `MainActivity` shows directly as
  a first-launch gate rather than routing it through `RegimenNavHost`.
- **`build-logic`** — an included build with convention plugins (`regimen.android.library`,
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

**Active Workout is not a tab.** It is a full-screen destination launched from Home or Routines,
backed by a foreground service. While a workout is in progress, navigating elsewhere surfaces a
persistent "workout in progress" banner (`WorkoutInProgressBanner`) docked above the tab bar/rail
that returns the user to the active session.

Re-tapping the already-active tab pops that tab back to its own root rather than being a no-op.
A pushed detail screen (e.g. Session Detail) keeps its parent tab highlighted in the bar/rail.

---

## Screen inventory

### Tab 1 — Home

- **Home** — greeting (time-of-day, or a generic fallback before any workout has been logged),
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

### Tab 2 — Routines

- **Routines List** — a flat, **reorderable** list of saved templates. No folders.
- **Routine Editor** — create or edit a routine: name, add/reorder exercises, per-exercise target
  sets/reps/rest. Uses the shared Exercise Picker sheet. **Strength exercises only** — cardio is
  excluded from templates.

### Tab 3 — History

- **History** — **calendar view**: a month grid with workout days marked; tapping a day opens
  Session Detail, or a picker dialog if multiple sessions happened that day. No chronological
  list view.
- **Session Detail** — read-only view of a past workout (date, duration, per-exercise sets/cardio,
  notes). Actions: **Repeat workout** (starts the same workout again, resuming an in-progress one
  if there already is one), **Edit** (reopens the session in Active Workout without touching its
  original timestamps), **Save as routine** (strength exercises only), **Delete**.

### Tab 4 — Progress

- **Progress** — a **personal-records list** (heaviest weight per exercise, or best reps for
  bodyweight-only exercises) and a **workout-frequency chart** with a selectable range (4 weeks /
  3 months / 1 year / all time). A Body Measurements entry point sits at the top of this tab.
- **Body Measurements** — **bodyweight** (built-in) plus **user-defined custom measurement types**
  (e.g. waist, arm, body-fat %), each with a trend chart (same selectable-range control as
  Progress's frequency chart). No fixed preset list beyond bodyweight.
  - **Add Measurement Entry** — bottom sheet.

Per-exercise progress (history and PRs) lives on Exercise Detail, not on a separate screen.

### Tab 5 — Settings

- **Settings** — units (metric/imperial: kg/lb + km/mi, weight and distance selected
  independently), theme (light/dark/system plus a dynamic-color toggle), rest-timer default
  duration, a rest-alert sound toggle, custom measurement type management, and an entry point to
  the Exercise Library. Data export to JSON is not implemented.
- **Exercise Library** — every exercise (built-in and custom), filterable by type
  (strength/cardio), muscle group, and equipment, with free-text search. Ships with a curated set
  of built-in strength and cardio movements. No favorites. Also serves as the exercise-picker
  source.
- **Exercise Detail** — description, target muscles, and per-exercise history, PRs, and best set.
- **Add/Edit Custom Exercise** — create or edit a user-defined **strength** exercise. Cardio is
  predefined-only; no custom cardio activities.

### Cross-cutting / modal screens

- **Active Workout** (full screen) — the core logging loop. See the
  [detailed spec](#active-workout--detailed-spec) below.
- **Rest Timer** — bottom sheet within Active Workout, started manually (no auto-start on set
  completion). Defaults to the routine's per-exercise rest target (or the global default),
  adjustable in ±15s increments, running alongside the session timer. On completion: vibration,
  an optional audio chime (per the rest-alert sound setting), and a system notification.
- **Workout Summary** — post-finish recap: duration, total volume, sets completed, and any PRs
  achieved. For a freeform Quick workout, offers **Save as routine**.
- **Exercise Picker** — reusable bottom sheet for adding exercises, shared by the Routine Editor
  and Active Workout. Search, multi-select, and a link to add a custom exercise.
  **Context-filtered:** the Routine Editor shows strength exercises only; Active Workout shows all
  exercises, including cardio.
- **Onboarding** (first-run only) — units and theme selection, always skippable.

---

## Active Workout — detailed spec

The core loop; users spend the majority of session time here.

- **Entry** — from a saved routine (exercises pre-filled) or a freeform **Quick workout**
  (established users only). New users are guided to create a routine first.
- **Session timer** — total elapsed workout time; starts on session begin and runs continuously
  until finish. Resting does not pause it. A distinct **Pause** action (available in-app and from
  the persistent notification) can pause or resume the whole session; the recorded duration
  excludes paused time.
- **Rest mode** — started manually via a Rest button; no auto-start on set completion. Runs
  alongside the session timer.
- **Per-set logging** — each exercise lists its sets with editable reps and weight. Sets can be
  added or removed at any point. Each set has a completion checkoff, independent of the rest
  timer.
- **Prefill** — each exercise's sets are prefilled from the most recent session of the same
  routine. For a freeform workout, or a newly added exercise with no history, fields start blank.
- **Skip** — an exercise can be marked skipped (greyed out, labeled) and un-skipped mid-workout. A
  left-skipped exercise is recorded in history as skipped (an adherence signal), not removed.
- **Cardio** — a cardio activity can be added to the session, recording duration and distance.
  Cardio entries are session-only; never part of a routine.
- **Other** — exercises can be added or removed mid-session via the picker; per-set completion is
  checked against personal records as it happens; per-session notes are supported.
- **Resilience** — the screen survives process death, rotation, and backgrounding. The
  in-progress session is persisted to Room continuously, not only on finish.
- **Persistent notification (foreground service)** — while a workout is active, an ongoing
  notification exposes **Pause** and **End workout** actions and backs the continuously running
  timer. Requires a foreground service and the `POST_NOTIFICATIONS` permission (Android 13+).
- **Editing a past session** (via Session Detail's Edit) reopens Active Workout without a live
  timer, Pause/Resume, or rest-timer button — the bottom toolbar instead shows a static "Editing
  session" label. Editing never changes the session's original timestamps and does not conflict
  with a genuinely in-progress workout running elsewhere.
- **Bottom toolbar** — a floating pill anchored above the bottom edge (not the top bar), showing
  the elapsed timer, Pause/Resume, and Finish. Tinted with the theme's primary color, darkening
  while paused as a status indicator; pausing/resuming animates a circular color reveal
  originating near the Pause/Resume button.
- **Finish** → navigates to Workout Summary.

---

## Key decisions

### Onboarding and empty states

- Onboarding is minimal — units and theme only, always skippable.
- Empty states are minimal and functional throughout — a short line of text and (where there's a
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
  Expressive's `MaterialExpressiveTheme`/`MotionScheme.expressive()`.
- **Units:** independent metric/imperial preferences for weight (kg/lb) and cardio distance
  (km/mi). Values are stored canonically (weight in kg, distance in meters) and converted only at
  display/entry time, so switching units never loses precision.
- **Rest alert:** vibration, an optional audio chime (user-toggleable), and a system notification.
- **Active-workout notification:** persistent, foreground-service-backed, with Pause and End
  actions.
- **Data export:** not implemented.

---

## Reusable components (`:core:designsystem`, `:core:common-ui`)

- **Exercise Picker sheet** — shared by the Routine Editor and Active Workout.
- **`Stat`** — a labeled value (e.g. "12" over "Workouts"); the building block for stat
  rows/grids on Home and Workout Summary.
- **`ConfirmDialog`** — confirm/dismiss dialog shared by every delete/discard/finish confirmation;
  `destructive = true` colors the confirm button with the error color.
- **`SaveAsRoutineDialog`** — prompts for a routine name; shared by Workout Summary and Session
  Detail.
- **`EmptyState`** — a centered message with an optional icon and action button, shared by every
  screen's empty list/search-result state.
- **`SectionHeader`**, **`UnitSystemSelector`**, **`ThemeModeSelector`** — shared section-label
  text and unit/theme segmented-button pickers (the latter two used by both Onboarding and
  Settings).
- **`LineChart`/`Sparkline`** — a self-contained Canvas-based chart, used by Progress (frequency),
  Body Measurements (bodyweight and custom-measurement trends), and Home (frequency +
  bodyweight).
- **`HistoryRangeSelector`** — the 4w/3m/1y/All segmented range selector, shared by the Progress
  frequency chart and the Measurement trend chart.
- **`ReorderableList`** — drag-to-reorder gesture/state helpers (`DragDropState`,
  `rememberDragDropState`, `Modifier.dragHandle`), used by the Routines list and Routine Editor's
  exercise list.
- **`SessionFormat`/`MeasurementFormat`/`ExerciseLabels`** (`:core:common-ui`) — shared
  `@Composable` formatters for durations, weights/reps, distances, and exercise-taxonomy display
  labels.

---

## Adaptive / foldable support

Shared infrastructure lives in `:core:designsystem`'s `adaptive/WindowAdaptive.kt`:

- `RegimenPosture` (`Compact` / `Tabletop` / `BookOrExpanded`) — Regimen's own simplified layout
  classification, derived from `androidx.compose.material3.adaptive`'s
  `currentWindowAdaptiveInfo()` (`windowPosture` and `windowSizeClass`).
- `LocalRegimenWindowInfo` (a `CompositionLocal`) and `ProvideRegimenWindowInfo { }` — provided
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
`NavigationDrawer` tier — desktop-class widths aren't a target). `navigationSuiteColors` pins
`navigationRailContainerColor` to `MaterialTheme.colorScheme.surfaceContainer` so the rail matches
the tone of the bottom bar and collapsed `MediumTopAppBar`s elsewhere.

Per-screen adaptive behavior:

- **App shell** — `NavigationBar` (Compact/Tabletop) or `NavigationRail` (BookOrExpanded); content
  is width-capped and centered at 600dp for Compact and Tabletop (Tabletop keeps the bottom bar
  even when the window is genuinely wide), full-bleed only for BookOrExpanded.
  `WorkoutInProgressBanner` docks at the bottom of the content pane in every posture.
- **Onboarding** — Tabletop splits navigation controls into the bottom pane, content/title in the
  top pane; BookOrExpanded constrains content to 600dp, centered; Compact is unchanged.
- **Home** — BookOrExpanded arranges the week/month summary and frequency/bodyweight charts side
  by side (960dp max width, centered); Tabletop behaves like Compact (a scrollable dashboard, no
  hinge-adjacent controls to protect).
- **Routines / Routine Editor** — both capped at 600dp and centered on BookOrExpanded;
  Compact/Tabletop unchanged. The Sets/Reps/Rest steppers measure per-card width via
  `BoxWithConstraints` (a single row at ≥420dp, otherwise a two-row split) rather than keying off
  posture.
- **History / Session Detail** — both capped at 600dp and centered on BookOrExpanded (History's
  seven-column month grid needs this most, to keep day cells at a reasonable tap-target size).
- **Progress / Body Measurements** — capped at 600dp and centered on BookOrExpanded; the empty
  state uses a 480dp cap, matching Home/Routines.
- **Settings** — capped at 600dp and centered on BookOrExpanded; the top app bar's
  collapse-on-scroll behavior is unaffected, since it lives outside the capped content.
- **Exercise Library / Exercise Detail** — both capped at 600dp and centered on BookOrExpanded.
- **Active Workout** — the exercise list and the floating bottom toolbar are capped together at
  600dp and centered on BookOrExpanded, so the toolbar never exceeds the content's width. No
  Tabletop hinge split is needed — the toolbar is already anchored to the bottom edge, the same
  reasoning that applies to the bottom navigation bar.
- **Workout Summary** — capped at 600dp and centered on BookOrExpanded.
- **Rest Timer** — not independently adapted (a short, fixed-content sheet — low risk).
- **Exercise Picker** — no width cap; already unaffected by the modal-sheet compact-landscape
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

Workout(id, startTime, endTime, note, routineId?, pausedAt?, accumulatedPausedMs)
WorkoutExercise(id, workoutId, exerciseId, position, isSkipped, supersetGroupId?)

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
| Architecture                    | MVVM + UDF with a full use-case (domain) layer — `ui → domain/use-cases → data/repository → Room DAO`; ViewModels expose immutable `StateFlow` UI state |
| DI                              | Hilt                                                                                                                                                    |
| Persistence                     | Room + Coroutines/Flow; DAOs return `Flow`                                                                                                              |
| Active Workout runtime          | Foreground service (persistent notification, Pause/End). Needs `FOREGROUND_SERVICE` (+ type) and `POST_NOTIFICATIONS` (Android 13+)                     |
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

- **Theme** — `RegimenTheme` uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
  instead of plain `MaterialTheme`.
- **Expressive shapes** — the Home streak tile uses `MaterialShapes.Cookie9Sided.toShape()` as a
  decorative icon-badge shape.
- **Navigation transitions** — `RegimenNavHost` applies shared-axis-x transitions (slide and
  fade, reversed on pop) via `NavHost`'s `enterTransition`/`exitTransition`/
  `popEnterTransition`/`popExitTransition`, replacing the platform default cross-fade.

Not implemented: shape morphing on press, and a true container-transform (as opposed to the
shared-axis slide/fade above).
