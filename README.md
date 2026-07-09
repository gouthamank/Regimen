# Regimen

A local-only Android gym tracker. Build your workout **routines**, then record what you
actually lift — sets, reps, and weight — with cardio, rest timing, and progress tracking.
Your data stays on your device.

## Features

- **Template-driven logging** — create routines ahead of time and start workouts from them
  with your last session's numbers pre-filled.
- **Active workout** — a live session with a running timer, manual rest timer, per-set
  reps/weight, skip/un-skip, and a persistent notification (pause / end) so it survives
  backgrounding.
- **Cardio** — log cardio activities (treadmill, running, cycling…) with duration + distance
  into any session.
- **History** — a calendar of past workouts; repeat a workout or save it as a routine.
- **Progress** — personal records and a workout-frequency chart.
- **Body measurements** — track bodyweight and your own custom measurement types over time.
- **Custom exercises** on top of a curated ~50–100 built-in exercise library.
- **Material 3 Expressive** UI with dynamic color, light/dark, and kg/lb + km/mi units.

No backend, no account, no network — everything is stored locally. Data export (JSON) is
planned for a later version.

## Tech stack

- **Kotlin** · Gradle Kotlin DSL · version catalog · KSP
- **Jetpack Compose** + **Material 3 Expressive**, single-Activity
- **Navigation Compose** (type-safe routes)
- **MVVM + UDF** with a full use-case / domain layer
- **Hilt** for dependency injection
- **Room** for local persistence (Coroutines / Flow)
- **minSdk 26** (Android 8)

## Documentation

See **[docs/architecture.md](docs/architecture.md)** for the full screen inventory,
navigation, the Active Workout spec, data model, and the key product/technical decisions.

## Building

> ⚠️ **No code scaffold yet.** This repository currently contains documentation only. Build
> and run instructions will be added once the Android/Gradle project is scaffolded.
