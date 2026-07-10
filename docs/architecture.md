# Regimen — Architecture

Regimen is a local-only Android gym-tracking app. Users build **routines** (workout
templates) ahead of time, then **record** actual workouts against them — sets, reps, and
weight — with optional cardio, rest timing, and progress tracking. No backend, no account,
no network; all data lives on the device.

This document is the reference for what the app is made of: its screens, navigation, the
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

**Logging model:** template-driven. The primary path is: build a routine → start a workout
from it (exercises pre-filled) → record your numbers. Established users also get a secondary
freeform "Quick workout" (see [Workout entry](#workout-entry)).

---

## Navigation

Single-Activity, Jetpack Compose, Navigation Compose (type-safe routes). Top-level
navigation is a **bottom tab bar** with 5 destinations:

1. **Home** · 2. **Routines** · 3. **History** · 4. **Progress** · 5. **Settings**

**Active Workout is not a tab** — it's a full-screen destination launched from Home or
Routines and backed by a foreground service. While a workout is in progress and the user
navigates elsewhere, a persistent "workout in progress" banner returns them to it.

---

## Screen inventory

### Tab 1 — Home
- **S1. Home** — greeting, prominent **Start Workout** CTA, **quick-start routine chips**
  (tap a recent routine to jump in), a **this-week summary** (workouts / volume / time), and
  a **streak**. No recent-workouts list here (that's the History tab).
  - **Empty state (new user, no routines):** minimal — one line + a CTA that funnels into
    **Create your first routine** (guided). This is the sole cold-start path; no seeded
    routines, no demo data.
  - **Established user:** also surfaces a secondary, de-emphasized **Quick workout**
    (freeform) entry.
  - _Built in #14:_ time-of-day greeting, prominent **Start Workout** CTA, quick-start routine
    chips (ordered most-recently-used first), a **this-week** card (workouts / volume / time) +
    a weekly **streak**, and the freeform **Quick workout** entry for established users
    (≥1 completed workout). New-user empty state → **Create your first routine** (real nav).
    The workout-start actions show a "coming soon" snackbar until Active Workout (S13) lands in
    #15; the empty-state Create-routine CTA is fully wired.

### Tab 2 — Routines
- **S2. Routines List** — a flat, **reorderable** list of saved templates. No folders in v1.
- **S3. Routine Editor** — create/edit a routine: name, add/reorder exercises, per-exercise
  target sets/reps/rest. Uses the shared Exercise Picker (S16). **Strength exercises only** —
  cardio is excluded from templates.

### Tab 3 — History
- **S4. History** — **calendar view**: a month grid with workout days marked; tap a day →
  Session Detail. (A chronological list view is deferred to a later version.)
- **S5. Session Detail** — read-only view of one past workout. Actions: **Repeat workout**,
  **Save as routine**, edit / delete.
  - _Built in #12:_ read-only view + **Save as routine** (strength exercises only) + **Delete**.
  - _Built in #15 (Phase 3c):_ **Repeat** (start the same workout again — from its routine, or a
    freeform clone with prior numbers prefilled) and **Edit** (reopen the session in Active Workout;
    blocked while another workout is in progress). Both open Active Workout.

### Tab 4 — Progress
- **S6. Progress Overview** — a **PR list** (records per exercise) plus a
  **workout-frequency chart**. No estimated-1RM or volume-trend charts in v1.
  - _Built in #13:_ PR list (heaviest weight per exercise, formatted to the unit preference) +
    an 8-week workout-frequency trend (shared `LineChart`), with the Body Measurements entry
    point kept on the tab root. Empty state until the first workout is finished.
- **S8. Body Measurements** — **bodyweight** (built-in) plus **user-defined custom
  measurement types** (e.g. waist, arm, body-fat %), each with a trend chart. No fixed preset
  list — the user adds the types they want.
  - **S8a. Add Measurement Entry** — bottom sheet.

> There is no separate per-exercise progress chart screen in v1. Per-exercise history and PRs
> live in Exercise Detail (S11).

### Tab 5 — Settings

- **S9. Settings** — **units** (metric/imperial: kg/lb + km/mi), **theme**
  (light / dark / system + dynamic-color toggle), **rest-timer default duration**, **manage
  custom measurement types**, an entry point to the Exercise Library, and **data export →
  JSON** (deferred).
- **S10. Exercise Library** — all exercises (built-in + custom). Ships with a **curated
  ~50–100 built-in set** (common barbell/dumbbell/machine/bodyweight movements **plus cardio
  activities**). Filter by **type** (strength/cardio), **muscle group**, and **equipment**,
  plus **free-text search**. No favorites in v1. Doubles as the picker source (S16).
- **S11. Exercise Detail** — description, target muscles, and **per-exercise history / PRs /
  best set**.
- **S12. Add/Edit Custom Exercise** — create/edit a user-defined **strength** exercise.
  Cardio is predefined-only in v1 — no custom cardio.

### Cross-cutting / modal screens
- **S13. Active Workout** (full screen) — the core loop. See [detailed spec](#s13-active-workout--detailed-spec).
  - _Built:_ per-set logging (weight/reps/done), skip/include, add & remove exercises via the
    picker, cardio entry, session note, live session timer, discard/finish (Phase 1); rest timer
    (Phase 2); in-progress **Resume banner** + single-active resume + process-death resume +
    POST_NOTIFICATIONS request (Phase 3a); **foreground service + persistent Pause/End notification
    + Pause** (Phase 3b — session timer & recorded duration exclude paused time). **Not yet:**
      Session-Detail Repeat/Edit (Phase 3c).
- **S14. Rest Timer** — bottom sheet/overlay within Active Workout. **Started manually** (no
  auto-start); adjustable duration defaulting to the routine's per-exercise rest target. On
  finish: **vibration + audio chime + a system notification** (user's default sound).
  - _Built in #15 (Phase 2):_ per-exercise Rest button opens a bottom-sheet countdown defaulting to
    the routine's rest target (fallback: global rest default), adjustable ±15s, running alongside
    the session timer; on finish → vibration + default notification sound. The notification renders
    on Android <13 now and on 13+ once POST_NOTIFICATIONS is granted (Phase 3).
- **S15. Workout Summary** — post-finish recap: duration, total volume, sets, PRs hit. For a
  freeform Quick workout, offers **Save as routine**.
  - _Built in #15 (Phase 1)._
- **S16. Exercise Picker** — reusable bottom sheet for adding exercises; used by the Routine
  Editor (S3) and Active Workout (S13). Search + multi-select + a link to add a custom
  exercise (S12). **Context-filtered:** routines show strength only; Active Workout shows all,
  including cardio.
- **S17. Onboarding** (optional, first-run) — light: units + theme, always skippable.

---

## S13 Active Workout — detailed spec

The core loop, and the highest-risk screen. Users spend most of their time here.

- **Entry** — from a saved routine (exercises pre-filled) or a freeform **Quick workout**
  (established users only). New users are guided to create a routine first.
- **Session timer** — total elapsed workout time; starts on begin and **runs continuously**
  to finish. *Resting does not pause it.* A distinct **Pause** action (in-app and from the
  persistent notification) can pause/resume the whole session.
- **Rest mode** — started **manually** via a Rest button (no auto-start on set completion).
  The rest countdown runs alongside the session timer; default duration comes from the
  routine's per-exercise rest target and is adjustable.
- **Per-set logging** — each exercise lists its sets with freely editable **reps + weight**.
  Sets can be added/removed on the fly. Each set has a **"done" checkoff** as a progress
  indicator (not tied to rest, since rest is manual).
- **Prefill** — each exercise's sets are prefilled from **the most recent session of the
  same routine**. For a freeform workout, or a newly added exercise with no routine history,
  fall back to blank.
- **Skip** — an exercise can be marked **skipped** (greyed, labeled) and **un-skipped**
  mid-workout. If left skipped, it's saved to history as skipped (adherence signal), not
  removed.
- **Cardio** — add a cardio activity (from the library's cardio list) into the session and
  record **duration + distance**. Session-only — never part of a routine.
- **Other** — add/remove exercises mid-session (via S16), per-exercise/session notes, PR
  detection on completed sets.
- **Resilience** — **must survive process death / rotation / backgrounding.** The in-progress
  session is persisted to Room continuously, not just on finish.
- **Persistent notification (foreground service)** — while a workout is active, an ongoing
  notification exposes **Pause** and **End workout** actions and backs the always-running
  timer. Requires a foreground service and the `POST_NOTIFICATIONS` permission (Android 13+).
- **Finish** → S15 Workout Summary.

---

## Key decisions (v1 scope)

### Onboarding & empty states
- **Onboarding:** light — max 2–3 screens (units + theme, optional), always skippable.
- **Empty states:** minimal + functional everywhere — a short line of text and a single CTA,
  no illustrations.

### Workout entry
- Primary path is **routine-driven**; new users are funneled to guided create-first-routine.
- A secondary freeform **Quick workout** is available to established users. A freeform
  session can be **saved as a routine** afterward.

### Workout mechanics
- **Supersets:** deferred to v2. v1 does exercises one at a time. The data model is designed
  so supersets can be added later without a painful migration (see below).
- **Per-set checkoff:** kept, as a progress indicator.
- **Warm-up sets** and **plate calculator:** out of v1.

### Exercises & routines
- **Built-in library:** curated ~50–100 movements shipped with the app; users add custom
  strength exercises.
- **Library taxonomy:** filter by type, muscle group, and equipment, plus text search. No
  favorites in v1.
- **Routine organization:** flat, reorderable list — no folders in v1.
- **PR definition:** **heaviest weight lifted** per exercise (derived, not stored).

### System & polish
- **Theming:** **dynamic color (Material You)** on Android 12+ with a branded fallback
  palette below; light/dark follows system and is user-overridable.
- **Units:** a global **metric/imperial** preference governs weight (kg/lb) and cardio
  distance (km/mi). Values are **stored canonically** (weight in kg, distance in meters) and
  converted only at display/entry, so switching units never loses precision.
- **Rest alert:** vibration + audio chime + a system notification (user's default sound).
- **Active-workout notification:** persistent, foreground-service-backed, with Pause / End
  actions.
- **Data export:** deferred, but designed toward **JSON** (full-fidelity backup/re-import).

---

## Reusable components
- **Exercise Picker sheet (S16)** — shared by the Routine Editor and Active Workout.
- **Set-entry row** — weight/reps input + complete toggle; used in Active Workout.
- **Chart component** — used by Progress (frequency) and Body Measurements (bodyweight +
  custom-measurement trends).
- **Empty states** — first-run Routines / History / Progress.

---

## Adaptive / foldable support

Rolled out **screen-by-screen**, not all at once. Shared infra lives in
`ui/adaptive/WindowAdaptive.kt`:

- `RegimenPosture` (`Compact` / `Tabletop` / `BookOrExpanded`) — Regimen's own simplified
  layout classification, derived from `androidx.compose.material3.adaptive`'s
  `currentWindowAdaptiveInfo()` (`windowPosture` + `windowSizeClass`).
- `LocalRegimenWindowInfo` (CompositionLocal) + `ProvideRegimenWindowInfo { }` — provided
  once in `MainActivity.setContent`, wrapping both the Onboarding gate and `RegimenApp`, so
  any descendant screen can read `LocalRegimenWindowInfo.current` without nav-arg plumbing.

Dependencies (versions pinned directly in `libs.versions.toml`; the first two don't ship in
the Compose BOM, the third tracks `material3`'s own version in lockstep):
`androidx.window:window:1.5.1`, `androidx.compose.material3.adaptive:adaptive:1.2.0`,
`androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha23` (reuses the
`material3Expressive` version ref).

The app shell (`RegimenApp.kt`) uses `NavigationSuiteScaffold` for the 5-tab nav — its
`layoutType` is driven by `RegimenPosture.toNavigationSuiteType()` (Compact/Tabletop →
`NavigationBar`, BookOrExpanded → `NavigationRail`) rather than
`NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo`, to stay consistent with the rest
of the rollout; kept as its own named mapping function as a seam for a future override.
`NavigationSuiteType.NavigationDrawer` (rendered as a `PermanentDrawerSheet`) is supported by
the library but deliberately not used — out of scope: desktop-class widths aren't a target,
only foldable/book posture is, and Google's own default logic doesn't auto-select a drawer
either (only ever Bar or Rail).

Rollout checklist (update as each screen is adapted):

```
[✓] App shell (RegimenApp.kt) — NavigationSuiteScaffold: NavigationBar for Compact/Tabletop,
    NavigationRail for BookOrExpanded; WorkoutInProgressBanner docked at the bottom of the
    content pane in both, with .navigationBarsPadding() added after a screenshot showed it
    flush against the bottom edge in Rail mode. No NavigationDrawer tier (out of scope).
    RegimenNavHost content is width-capped + centered @600dp for both Compact and Tabletop
    (Tabletop keeps the bottom bar regardless of actual width, and can be genuinely wide — e.g.
    a half-opened, horizontal-hinge AVD state confirmed at ~852dp) — only BookOrExpanded is
    full-bleed. Confirmed on-device.
[✓] Onboarding (S17) — tabletop: nav controls pushed to the bottom pane, content/title in
    the top pane; book/expanded: content constrained to 600dp max width and centered;
    compact: unchanged.
[✓] Home (S1) — BookOrExpanded: week/month summary and frequency/bodyweight charts go
    side-by-side (960dp max width, centered), rather than just width-capping. Tabletop treated
    identically to Compact (scrollable dashboard, no fixed hinge-adjacent controls to protect).
    Also added empty states for the frequency/bodyweight charts instead of hiding them (text
    only for frequency, text + a "Log bodyweight" CTA into Body Measurements for bodyweight).
    Confirmed on-device.
[ ] Routines (S4) / Routine Editor
[ ] History (S5) / Session Detail
[ ] Progress (S6) / Body Measurements (S8)
[ ] Settings (S9)
[ ] Exercise Library / Exercise Detail
[ ] Active Workout (S13) / Workout Summary (S15)
```

Routines and Exercise Library/Detail are the likely candidates for a true list-detail split
via `androidx.compose.material3.adaptive:adaptive-layout`'s `ListDetailPaneScaffold` when
their turn comes — a separate artifact/decision from the shared infra above, which Onboarding
does not use (it has no list/detail shape).

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
- **PRs** are derived (`max(weightKg)` per exercise), not stored, to start.
- **Supersets (future):** add a nullable `supersetGroupId` on `RoutineExercise` /
  `WorkoutExercise` and keep ordering position-based so grouping can layer on without a
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

### ⚠️ Material 3 Expressive is not fully stable — adopted anyway
Stable `material3` is `1.4.0`, which does **not** include the Expressive APIs. **Done:** the app
now overrides the Compose BOM's `material3` version to `1.5.0-alpha23` (`material3Expressive`
version entry in `libs.versions.toml`, applied to the `androidx-compose-material3` library alias)
and opts into `@ExperimentalMaterial3ExpressiveApi` where needed. This accepts alpha API churn
(APIs can shift release to release) — a **known, accepted risk**.

Adopted so far:
- **Theme**: `RegimenTheme` uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
  (`ui/theme/Theme.kt`) instead of plain `MaterialTheme`.
- **Expressive shapes**: the Home streak tile uses `MaterialShapes.Cookie9Sided.toShape()` as a
  decorative icon-badge shape (`ui/home/HomeScreen.kt`).
- **Navigation transitions**: `RegimenNavHost` now applies shared-axis-x transitions (slide + fade,
  reversed on pop) via `NavHost`'s `enterTransition`/`exitTransition`/`popEnterTransition`/
  `popExitTransition`, replacing the platform default cross-fade.

Not yet done: shape morphing on press, and a true container-transform (vs. the shared-axis
slide/fade above) — revisit if the visual payoff justifies the extra complexity.

---

## Deferred / backlog (post-milestone, not blocking)
Cross-cutting enhancements captured for later; none block the numbered build order.

- **Externalize strings to `strings.xml`.** User-facing text is currently hardcoded in Composables
  throughout. Migrate all of it to `res/values/strings.xml` (enables localization + consistency),
  and use **`<plurals>`** (quantity strings) wherever a count drives wording — e.g. "N workouts",
  "N-week streak", "N exercises", "N reps", "in the last N weeks". Prefer parameterized resources
  over string concatenation.
- ~~**Remove emoji everywhere.**~~ **Done.** Replaced the "🔥 N-week streak" line on Home with a
  `MaterialShapes`-badged `Icons.Filled.Whatshot` icon (see the new streak tile below), and the
  "🏆 Personal records" header on the Workout Summary with `Icons.Filled.EmojiEvents`. No emoji
  remain in user-facing strings.
- ~~**Lean into Material 3 Expressive (design discussion).**~~ **Done** (see "Material 3 Expressive
  is not fully stable — adopted anyway" above for what landed: expressive theme/motion scheme,
  expressive shapes, shared-axis-x nav transitions). Shape morphing and container-transform remain
  open if a future pass wants to go further.
- ~~**Home screen: split the "This week" card into smaller expressive cards.**~~ **Done.** Replaced
  the single card with a `WeekSummarySection`: three per-stat `StatTile`s (Workouts / Volume /
  Time) in a row, plus a dedicated `StreakTile` styled with the primary container color and an
  expressive-shape icon badge (`ui/home/HomeScreen.kt`). A frequency sparkline was considered but
  left out of this pass — still open if wanted later.
- ~~**Historical-data cutoff in graphs (discussion).**~~ **Done.** Added a shared
  `HistoryRange` enum (4w / 3m / 1y / All, `domain/model/Enums.kt`) and a `HistoryRangeSelector`
  segmented-button component (`ui/components/`), wired into both charts:
  - **Progress frequency chart**: `GetWorkoutFrequencyUseCase` now takes a `HistoryRange` (default
    3m) instead of a fixed 8-week count; `ALL` spans back to the first logged workout.
  - **Measurement trend**: `MeasurementDetailViewModel` filters the trend chart's entries by the
    selected range's cutoff (`HistoryRange.cutoffMillis()`); the entries list below stays
    unfiltered (full history). Downsampling for long series was not added — still open if a range
    proves too dense to render well.
- ~~**Edit re-timestamps a past session.**~~ **Done**, plus follow-on UX/safety fixes found while
  verifying it:
  - `Workout.preEditEndTime` (new column, DB bumped to v3) stashes the original `endTime` when
    `ReopenWorkoutUseCase` clears it for editing; `FinishWorkoutUseCase` restores it instead of
    stamping "now", so re-finishing an edited session no longer inflates its duration.
  - The discard (✕) button in Active Workout, while editing a past session, no longer routes
    through `CancelWorkoutUseCase` (which deleted the whole session — found during verification).
    It now shows a distinct "Cancel edit?" dialog that restores the finished state (the same
    non-destructive restore Finish does) and pops back to Session Detail instead of Workout
    Summary. Edits already made during the edit session are kept (consistent with the rest of the
    app: every write autosaves immediately; nothing else supports undo either).
  - Editing mode no longer shows a live ticking session timer, Pause/Resume, or the per-exercise
    "Rest" timer button (`ActiveWorkoutUiState.isEditingPastSession`) — none of those make sense
    against a static past session. The bottom toolbar (see below) shows a static "Editing session"
    label instead.
- **Active Workout bottom toolbar (redesign).** Pulled the elapsed timer, Pause/Resume, and
  Finish out of the top bar (now just the title + close/cancel-edit icon) into a dedicated
  floating pill toolbar anchored above the bottom edge, over the scrolling content
  (`ActiveWorkoutToolbar` in `ActiveWorkoutScreen.kt`):
  - Built from plain `Surface`-style primitives (shadow + clip + fill), not the alpha
    `HorizontalFloatingToolbar` API — that component expanded to fill the whole available height
    when placed in `Scaffold`'s `bottomBar` slot, and its default shadow elevation read as
    invisible against a dark background.
  - Tinted with the theme's primary color (a lighter darken while paused, as a status cue,
    not a fixed color).
  - Pausing/resuming animates a circular color-reveal (wiping from the old to the new color,
    roughly originating from the Pause/Resume button) plus a small scale "pop" (a floaty,
    low-stiffness spring) — driven by a continuous `animateColorAsState` for the base fill (not a
    discrete cutover) to avoid a one-frame flicker of the old color.
  - Pause/Resume and Finish are `FilledIconButton`s (Finish is icon-only, a checkmark) with
    inverted colors (light container against the primary-tinted pill) so they read as distinct
    controls. Tapping anywhere on the pill (outside the Finish button) also triggers Pause/Resume
    — a mini-player-style affordance — except while editing a past session.
- ~~**Code structure: non-UI classes under `ui/`.**~~ **Done.** `ActiveWorkoutService`,
  `ActiveWorkoutServiceController`, and `RestAlerts` moved out of `ui/active/` into a dedicated
  `dev.gouthaman.regimen.service` package (manifest, `RegimenApplication`, and
  `ActiveWorkoutViewModel` updated accordingly). `ui/active/` now holds only Compose UI
  (`ActiveWorkoutScreen`, `ActiveWorkoutViewModel`, `WorkoutSummaryScreen`,
  `WorkoutSummaryViewModel`).
- ~~**Rest-alert sound toggle (Settings).**~~ **Done.** `UserPreferences.restChimeEnabled`
  (default on) + a Settings switch ("Rest timer sound"); `RestAlerts.fire(chimeEnabled)` skips
  `playChime()` when off. Also fixed a related bug found during verification: on Android 8+,
  notification sound is a *channel* property, not per-notification — gating just `playChime()`
  wasn't enough, since the notification itself still played the channel's default sound
  regardless. `RestAlerts` now creates two channels (`rest_timer` with sound,
  `rest_timer_silent` with `setSound(null, null)`) and `notifyDone()` posts to whichever matches
  the preference. Vibration and the notification itself always fire either way.

- ~~**Separate weight vs. distance units.**~~ **Done.** `UserPreferences` now has independent
  `weightUnit`/`distanceUnit` (each a `UnitSystem`), backed by separate DataStore keys with a
  fallback to the old single `unit_system` key for existing installs. Settings and Onboarding
  each show two selectors; `SessionFormat`, `MeasurementFormat`, and Active Workout's
  weight/cardio rows take the appropriate unit independently.
- ~~**Bottom-tab navigation correctness.**~~ **Done.** Two related gaps in the single-NavHost setup
  (top-level routes are siblings of pushed detail routes), fixed in `RegimenApp.kt` without moving
  to per-tab nested graphs:
  1. **Re-tapping the active tab** now pops that tab back to its root
     (`navController.popBackStack(dest.route, inclusive = false)`) instead of being a no-op.
  2. **A pushed detail screen now keeps its parent tab highlighted.** Rather than matching only the
     exact top-level route, the bottom bar walks the live back stack
     (`navController.currentBackStack`) for the most recent top-level entry and highlights that tab
     underneath any detail screens pushed on top of it (e.g. Session Detail stays under History,
     Exercise Detail stays under Settings).

---

## Status
Documentation only — no code scaffold exists yet. The next step is a separate implementation
plan for the project scaffold (Compose + Room + Navigation + Hilt) and the core loop,
starting with **S13 Active Workout** as the highest-risk screen.
