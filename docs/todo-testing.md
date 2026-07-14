# Testing strategy

The app has **zero** tests today — no populated `src/test/`/`src/androidTest/` source set in any
module, and no test-related Gradle wiring in any convention plugin or the version catalog. The
multi-module migration (`docs/todo-multi-module-migration.md`) that this work was gated on is
complete, so nothing blocks starting. Test-writing itself has not started yet; this doc is the
plan to pick up when it does.

## Decisions

- **§1 (pure unit tests):** skip testing one-line repository pass-through use cases individually;
  reserve unit tests for use cases with actual branching/logic.
- **§3 (fakes vs. MockK):** fakes-first. See "Reference: the fake pattern" below. Shared fakes live
  in a new `:core:testing` module (pure Kotlin, applies `regimen.jvm.library` +
  `regimen.jvm.test`), depended on via `testImplementation` from each `:feature:*` module that
  needs them — resolves the "where do fakes live" question the migration was originally deferring.
- **JUnit4** over JUnit5 (Android's own tooling — Compose test rules, `AndroidJUnit4` runner —
  is built around it most smoothly; nothing here needs JUnit5's extension model badly).
- **Turbine**: yes — standard tool for asserting `StateFlow`/`Flow` emission sequences, cheap
  dependency, avoids hand-rolled `flow.take(n).toList()` assertions.
- **`kotlinx-coroutines-test`**: yes, for `TestDispatcher`/`runTest` control of `viewModelScope`
  timing.
- **§2 (Room DAO test scope):** resolved with a concrete per-DAO breakdown, see below.
  `androidTest` (real device/AVD) over Robolectric for both Room DAO tests and Compose UI tests —
  this repo already has a working AVD workflow and no CI, so Robolectric's main selling point
  (no device needed, fast/parallel in CI) doesn't pay for its integration risk right now
  (Robolectric-on-AGP-9 compatibility for this repo's compileSdk 37 is unproven). Revisit if CI is
  ever introduced.
- **§4 (screenshot testing): deferred.** Paparazzi has no released version confirmed compatible
  with this repo's AGP 9.2.1 — stable `1.3.5` targets AGP `8.4.2`; the latest alpha
  (`2.0.0-alpha05`) explicitly states "supports pre-AGP 9.0 consumers" only. Screenshot testing for
  the adaptive/posture variants (`RegimenPosture.Compact/BookOrExpanded/Tabletop`) and
  `LineChart`/`Sparkline` is dropped from the active plan; revisit once a Paparazzi release
  explicitly supports AGP 9.x. No substitute screenshot mechanism is planned for these in the
  meantime — they stay hand-verified on the AVD as today.

## Gradle infrastructure

None of this exists yet; build incrementally as each tier below is actually written, not all at
once.

- **Version catalog additions** (`gradle/libs.versions.toml`): `turbine = "1.2.1"`,
  `mockk = "1.14.11"`, `kotlinx-coroutines-test` (reuses the existing `coroutines = "1.11.0"`
  version ref), `androidx-room-testing` (reuses the existing `room = "2.8.4"` version ref),
  `androidx-test-core = "1.7.0"`.
- **`regimen.jvm.test.gradle.kts`** (new convention plugin) — adds
  `testImplementation(junit, kotlinx-coroutines-test, turbine)`. Applied by `:core:domain`,
  `:core:testing`.
- **`regimen.android.library.gradle.kts`** (modify existing) — add the same JVM test deps directly
  here so every Android module (`:core:data`, every `:feature:*` via `regimen.android.feature`)
  gets ViewModel-test deps for free, matching this plugin's existing role as the shared Android
  baseline.
- **`regimen.android.instrumented-test.gradle.kts`** (new, small fragment) — adds
  `androidTestImplementation(androidx-test-core, androidx-junit, androidx-room-testing)`. Applied
  explicitly only by `:core:data` (Room DAO tests) and `:core:designsystem` (Compose UI tests) —
  not blanket-applied via `regimen.android.feature`, since most feature modules only need JVM
  ViewModel tests.
- **`:core:testing`** (new module) — `include(":core:testing")` in `settings.gradle.kts`. Applies
  `regimen.jvm.library` + `regimen.jvm.test`, `api(project(":core:domain"))`. Houses
  `FakeRoutineRepository`, `FakeWorkoutRepository`, `FakeExerciseRepository`,
  `FakeMeasurementRepository`, `FakePreferencesRepository` — all implementing `:core:domain`'s
  repository interfaces, which already exist as proper interfaces with `*Impl` implementations in
  `:core:data` (no interface-extraction work needed first).
- **Gotchas to watch**: `:core:designsystem`'s new `androidTest` Compose tests need
  `androidTestImplementation(platform(libs.androidx.compose.bom))` added explicitly (the compose
  convention plugin only wires the BOM for `implementation` today); `core/data/schemas/` needs
  committed schema JSONs for both v4 and v5 before writing the `MIGRATION_4_5`
  `MigrationTestHelper` test.

## Reference: the fake pattern

```kotlin
// core/testing — implements the domain interface with real in-memory behavior
class FakeRoutineRepository : RoutineRepository {
    private val routines = MutableStateFlow<List<RoutineWithExercises>>(emptyList())
    override fun observeAll(): Flow<List<RoutineWithExercises>> = routines
    override suspend fun isExerciseUsed(exerciseId: Long) =
        routines.value.any { r -> r.exercises.any { it.exercise.id == exerciseId } }

    // ...
    fun seed(vararg seeded: RoutineWithExercises) {
        routines.value = seeded.toList()
    } // test-only
}
```

The distinguishing feature versus a mock: `isExerciseUsed` actually searches the in-memory list —
a mock would need `every { mock.isExerciseUsed(any()) } returns true` stubbed per test scenario,
so it only covers cases you thought to stub.

## The testing pyramid, mapped to this codebase

```
        ▲  Compose UI tests (screen-level)         — slow, few
       ╱ ╲ ViewModel tests (StateFlow + fakes)      — medium
      ╱   ╲ Room DAO tests (in-memory DB)           — medium
     ╱─────╲ Pure unit tests (use cases, formatters) — fast, many
```

## Module-by-module plan

### `:core:domain` — pure JUnit, no Android. Write first.

- **`UnitConverter`** (`util/UnitConverter.kt`) — all 6 conversion/format functions, pure
  `Double`/`UnitSystem` math, no setup required. First thing written, since it needs no fakes.
- **High priority** (real branching): `GetHomeSummaryUseCase` (date/streak math),
  `StartWorkoutUseCase` (session-init branching + prefill), `RepeatWorkoutUseCase` (clone
  branching), `SaveWorkoutAsRoutineUseCase` (spec derivation), `AddExercisesToWorkoutUseCase`
  (per-type prefill), `GetPersonalRecordsUseCase` (weight/reps PR merge),
  `GetWorkoutFrequencyUseCase`
  (week grouping), `DeleteExerciseUseCase` (cross-repo guard logic).
- **Medium**: `HasRoutinesUseCase`, `FinishWorkoutUseCase`, `ResumeWorkoutUseCase`,
  `ObserveExercisesUseCase` (filter chain).
- **Low**: `AddSetUseCase`, `UpdateWorkoutNoteUseCase`.
- **Skip** (one-line repository pass-throughs): everything else in `RoutineUseCases`,
  `MeasurementUseCases`, `PreferenceUseCases`, plus the simple observe/delete one-liners in
  `WorkoutUseCases`/`ExerciseUseCases`.

### `:core:testing` — stand up alongside the first use-case test that needs a fake

Start with `FakeRoutineRepository` (the worked example above) — needed for
`RoutineEditorViewModel`'s test in `:feature:routines` and any `RoutineUseCases` test. Add the
other fakes on demand as each module's tests need them, not all five upfront.

### `:core:data` — Room DAO tests, `androidTest`

- **Highest priority**: `WorkoutDao`'s raw-SQL JOIN queries — `observeBestWeight`,
  `observePersonalRecords`, `observeBestReps`, `getMostRecentSetForExercise`,
  `observeExerciseHistory`, `getMostRecentCompletedForRoutine` — these directly drive PR/history
  correctness with zero compile-time signal if wrong.
- **Also test**: `WorkoutDao`'s other `@Transaction`/`@Relation` queries
  (`observeCompletedWithDetails`, `observeWorkout`, `getWorkoutWithDetails`,
  `getInProgressWorkout`); `RoutineDao`'s relation queries (`observeRoutinesWithExercises`,
  `observeRoutine`, `getRoutineWithExercises`) and hand-rolled `@Transaction` methods
  (`applyOrder`, `replaceRoutineExercises`).
- **Migration test**: `MIGRATION_4_5` via `MigrationTestHelper` — establishes the pattern for
  future migrations.
- **Skip**: `ExerciseDao`, `MeasurementDao` — pure single-table CRUD, no `@Relation`/`@Transaction`/
  hand-written joins; low enough bug surface that testing mostly re-verifies Room itself.

### `:feature:routines` — first ViewModel tests, JVM-only

- **`RoutineEditorViewModel.setExercises()`** reconciliation (keep customized entries for
  still-checked ids, drop unchecked, append new) — pure in-memory logic, no repository call. Write
  this right after `:core:testing`'s first fake lands.
- **`RoutinesListViewModel`**'s optimistic drag-reorder overlay — medium priority.

### `:feature:home`

- **`HomeViewModel`**'s quick-start ordering (most-recently-used + manual position) and
  multi-source aggregation — high priority.

### `:feature:progress`

- **`ProgressViewModel`**'s PR value resolution (weight vs. reps branching) — high priority.

### `:feature:exercise`

- **`ExerciseDetailViewModel`**'s PR branching (same shape as Progress's) — medium.
- **`ExerciseLibraryViewModel`**'s filter-toggle chain — medium.
- **Skip**: `EditExerciseViewModel` (simple edit-vs-new branching, no reconciliation).

### `:feature:active` — last, deliberately

- **`ActiveWorkoutViewModel`**'s rest-timer countdown (`while (isActive) { delay(...) }` loop,
  auto-complete, time-add clamping) — needs `kotlinx-coroutines-test`'s `TestDispatcher`/virtual
  time. Fussier than the rest of the tier; do this after there's practice with fakes/Turbine on
  simpler ViewModels above.
- **`WorkoutSummaryViewModel`**'s volume summation + PR comparison — medium, can go earlier if
  convenient.

### `:feature:history`

- **`SessionDetailViewModel`**'s save-as-routine eligibility check — medium.
- **Skip**: `HistoryViewModel` (grouping/sorting only, no branching).

### `:feature:measurements`

- **`MeasurementsViewModel`**'s type-driven aggregation — medium.
- **Skip**: `MeasurementDetailViewModel` (flow mapping + unit conversion only).

### `:feature:onboarding`, `:feature:settings` — skip entirely

Pure preference pass-through setters, no branching logic to protect.

### `:core:designsystem` / `:core:common-ui` — Compose UI tests, `androidTest`

Best ROI in the plan — test each shared component once, get coverage for every screen that uses
it. Independent of the ViewModel tier; can be written in parallel with it.

- **Test**: `ConfirmDialog`, `ExercisePickerSheet`, `SaveAsRoutineDialog`, `Stat`, `EmptyState`,
  `SectionHeader`, `UnitSystemSelector`, `ThemeModeSelector`, `WorkoutInProgressBanner`,
  `ReorderableList`/`DragDropState`.
- **Skip**: pure theme composition (`Color.kt`/`Type.kt`/`Theme.kt`), `core/navigation-api`'s
  `Routes.kt` (pure `@Serializable` data classes, no logic).
- **Not covered this round** (Paparazzi deferred, see above): adaptive posture variants
  (`RegimenPosture.Compact/BookOrExpanded/Tabletop`), `LineChart`/`Sparkline` Canvas rendering —
  stay hand-verified on the AVD for now.

## What's explicitly NOT proposed (unless wanted)

- End-to-end instrumented tests driving the real foreground service (`ActiveWorkoutService`) —
  high value but high setup cost (needs a running emulator, real notification permission flow).
- Full coverage-percentage targets — coverage numbers as a goal tend to produce low-value tests
  written to hit a number. Better to pick specific logic worth protecting (per tier above) and
  stop there.

## Sequencing

1. `:core:domain` — `UnitConverter` (no deps needed yet).
2. Stand up `:core:testing` with `FakeRoutineRepository`; return to `:core:domain` for
   `RoutineUseCases`/high-priority use-case tests using it.
3. `:core:data` — `WorkoutDao` JOIN queries (highest value), then `RoutineDao` relation queries,
   then `MIGRATION_4_5`.
4. `:feature:routines` — `RoutineEditorViewModel` reconciliation (add fakes to `:core:testing` as
   needed).
5. `:feature:home`, `:feature:progress`, `:feature:exercise` — remaining high/medium ViewModels,
   adding fakes to `:core:testing` incrementally.
6. `:feature:active` — rest-timer virtual-time test, last, deliberately.
7. `:core:designsystem`/`:core:common-ui` — Compose UI tests for shared components (can run in
   parallel with 4–6).

Nothing left open in this doc's decisions. Next real step is executing step 1 of the sequencing
above, whenever that's picked up.
