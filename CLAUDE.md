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
  navigation (Home / Routines / History / Progress / Profile).
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
