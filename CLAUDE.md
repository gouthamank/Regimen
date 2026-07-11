# CLAUDE.md

Context for Claude Code sessions working on this repository.

## What this is

**Regimen** — a local-only Android gym-tracking app. Template-driven: users build workout
**routines**, then record actual **sessions** (sets/reps/weight, plus cardio) against them.

## Current status

Under active development. `docs/architecture.md` is the source of truth (screen inventory,
navigation, Active Workout spec, data model, all decisions) — **read it first.**

Done so far: project scaffold, full data + domain layer (Room entities/DAOs/DB + seed,
Hilt DI, repositories, DataStore prefs, use-cases), and the navigation shell (5-tab bottom
bar, type-safe Compose routes, theme wired to prefs). App builds and runs on the emulator.

Build order (Active Workout is built LAST): Settings → Exercise library/detail/custom →
Routines list/editor/picker → Body measurements → Onboarding → History/Session detail →
Progress → Home → Active Workout. **Next: the Settings screen (S9).**

Build/run details (toolchain paths, AGP-9 gotchas, emulator) live in the assistant's
project memory; see also `build.gradle.kts` / `gradle.properties`.

## Key decisions (see docs/architecture.md for detail)

- **Local-only**: no backend, no auth, no network. Room for persistence. Data export (JSON)
  is deferred.
- **UI**: Jetpack Compose + **Material 3 Expressive**, single-Activity, bottom-tab
  navigation (Home / Routines / History / Progress / Settings).
- **Architecture**: MVVM + UDF with a **full use-case (domain) layer**
  (`ui → domain → data/repository → Room DAO`); ViewModels expose `StateFlow`. Hilt for DI.
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

- Keep `docs/architecture.md` up to date when scope or the data model changes.
- **Navigation map:** `ui/navigation/RegimenNavHost.kt` has an ASCII navigation-map comment
  at the top of `RegimenNavHost`. Compose navigation is code-only (no XML graph / visual
  editor in Android Studio), so this comment is the human-readable overview — **update it
  whenever destinations are added, removed, or wired up** (flip `[ ]` → `[✓]` as routes land).
- Match the existing code style. UI lives in feature packages under `ui/`; ViewModels expose
  `StateFlow` UI state and call use-cases (never DAOs/repositories directly from Compose).
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
  this — `UnitConverter.weightLabel`/`distanceLabel` return a `UnitLabel` enum, resolved to text via
  `ui/util/UnitLabelText.kt`'s `UnitLabel.text()`. Exempt from all this: date/time
  `SimpleDateFormat` patterns, purely numeric formatters (mm:ss, elapsed-time), and punctuation
  separators ("·", "×") — none of that is translatable prose.
