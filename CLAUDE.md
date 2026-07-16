# CLAUDE.md

Context for Claude Code sessions working on this repository.

## What this is

**Regimen** — a local-only Android gym-tracking app. Template-driven: users build workout
**routines**, then record actual **sessions** (sets/reps/weight, plus cardio) against them.

## Current status

Feature-complete for v1 scope: every screen in `docs/architecture.md` (source of truth for
screen inventory, navigation, Active Workout spec, data model, all decisions — **read it
first**) is implemented, builds, and runs on the emulator.

Multi-module Gradle structure: `:app` (composition root) + `:core:{domain,data,common-ui,
designsystem,navigation-api}` + one `:feature:*` module per screen/tab (see
`docs/architecture.md`'s "Module structure" section for the full layout and what lives where).
A `build-logic` included build supplies the convention plugins each module applies.

Build/run details (toolchain paths, AGP-9 gotchas, emulator) live in the assistant's
project memory; see also `build.gradle.kts` / `gradle.properties`.

## Key decisions (see docs/architecture.md for detail)

- **Local-only**: no backend, no auth, no network. Room for persistence. Data export (JSON)
  is deferred.
- **UI**: Jetpack Compose + **Material 3 Expressive**, single-Activity, bottom-tab
  navigation (Home / Routines / History / Progress / Settings).
- **Architecture**: MVVM + UDF with a **full use-case (domain) layer**
  (`ui (feature modules) → domain → data/repository → Room DAO`); ViewModels expose `StateFlow`.
  Hilt for DI. Multi-module: `:core:domain` declares repository interfaces, `:core:data`
  implements them.
- **minSdk 26**, Kotlin + Gradle Kotlin DSL + version catalog + KSP.
- **Logging**: template-driven; freeform "Quick workout" available to established users.
- **Units**: metric/imperial preference; **store canonically** (weight in kg, distance in
  meters), convert only for display.
- **Active Workout** runs behind a **foreground service** with a persistent
  pause/end notification; the in-progress session must survive process death.

## ⚠️ Material 3 Expressive caveat

Expressive APIs are **not fully stable** as of July 2026. Stable `material3` (`1.4.0`) lacks
them; they're graduating in `1.5.0-alpha23`. Using Expressive requires pulling the
`material3:1.5.0-alpha` dependency explicitly and opting into
`@ExperimentalMaterial3ExpressiveApi` — accept alpha churn. Verify current versions before
pinning.

## Conventions

- **`docs/architecture.md` and `docs/testing.md` are living current-state snapshots, not
  changelogs.** Keep them up to date when scope, screens, the data model, or test coverage change —
  but edit in place to describe what *is*, never append a record of what changed. No "Built in #N"
  / milestone markers, no `~~strikethrough~~ **Done.**` backlog entries, no "originally X, later
  revised to Y" narration, no history of *how* something came to be this way. If a decision's
  rationale is worth keeping (e.g. *why* a value is stored canonically, *why* a column is
  reserved-but-unused), state it as a plain fact about the current design, not as a story of a past
  change. Not-yet-built/not-yet-tested items are still current-state facts — note them inline where
  relevant (e.g. "data export is not implemented" under Settings), not as a to-do/backlog list.
- **Tests:** `docs/testing.md` is the reference for what's tested, where, and what's deliberately
  skipped — read it before writing new tests. New or changed logic that falls into an
  already-covered tier (use cases with real branching, ViewModels, Room DAO joins, shared Compose
  components) should come with a corresponding test; update existing tests when the behavior they
  cover changes rather than leaving them asserting stale behavior.
- **Navigation map:** `:app`'s `ui/navigation/RegimenNavHost.kt` has an ASCII navigation-map
  comment at the top of `RegimenNavHost`. Compose navigation is code-only (no XML graph / visual
  editor in Android Studio), so this comment is the human-readable overview — **update it
  whenever destinations are added, removed, or wired up** (flip `[ ]` → `[✓]` as routes land).
  Each feature module owns its own destinations via a `NavGraphBuilder.xGraph()` extension
  function that `RegimenNavHost` calls — add new destinations there, not by inlining a
  `composable<Route>` block directly in `RegimenNavHost.kt`.
- Match the existing code style. UI lives in per-feature Gradle modules (`:feature:*`), each under
  its own `dev.gouthaman.regimen.feature.<name>` package; ViewModels expose `StateFlow` UI state
  and call use-cases (never DAOs/repositories directly from Compose).
- **Strings:** all user-facing text (labels, button text, dialog copy, content descriptions,
  Snackbar/Toast messages) goes in `res/values/strings.xml` — never a hardcoded string literal in
  a Composable. Use `stringResource()` / `pluralStringResource()`; use `<plurals>` for anything a
  count drives ("N reps", "N-week streak"), not manual singular/plural branching. Naming is
  `screen_element` (e.g. `routines_delete_dialog_title`) — each screen gets its own keys even when
  the English text is identical to another screen's, *except* enum-value display names (e.g.
  `UnitSystem`, `ThemeMode` labels), which are genuinely one canonical name shown in multiple
  places and should share a single resource.
  `stringResource`/`pluralStringResource` only work inside `@Composable` functions, so a ViewModel
  must never pre-format display text itself — if it needs to show a count, a unit, or a
  conditional fallback (e.g. "Quick workout" when a routine name is null), expose the raw or
  structured data in UI state (see `PersonalRecordValue`, `WeightValue`, `routineName: String?` for
  the pattern) and do the actual string formatting in the Composable at render time. A lambda
  parameter that calls a now-`@Composable` formatter (e.g. an enum's `.label()`) must itself be
  typed `@Composable (T) -> String`, not a plain `(T) -> String`.
  `UnitConverter`/`SessionFormat`/`MeasurementFormat`/`ExerciseLabels` are the shared formatters for
  this — `UnitConverter` (`:core:domain`) `.weightLabel`/`.distanceLabel` return a `UnitLabel`
  enum, resolved to text via `:core:common-ui`'s `UnitLabelText.kt`'s `UnitLabel.text()`. Exempt
  from all this: date/time `SimpleDateFormat` patterns, purely numeric formatters (mm:ss,
  elapsed-time), and punctuation separators ("·", "×") — none of that is translatable prose.
