# Testing

Reference for this repo's test suite: what's tested, where each tier lives, and what's
deliberately left untested.

## Approach

- **Fakes-first.** Shared fakes (in-memory, real-behavior implementations of `:core:domain`'s
  repository interfaces) are the default over mocking; MockK is the fallback for cases fakes can't
  reasonably cover (e.g. `Bundle` in JVM unit tests - see `:core:testing-android` below).
- **JUnit4** everywhere (Compose test rules and `AndroidJUnit4` are built around it).
- **Turbine** for asserting `StateFlow`/`Flow` emission sequences.
- **`kotlinx-coroutines-test`** for `TestDispatcher`/virtual-time control of `viewModelScope`.
- **`androidTest` (real device/AVD), not Robolectric**, for Room DAO tests and Compose UI tests -
  this repo has a working AVD workflow and no CI, so Robolectric's main selling point (no device
  needed) doesn't pay for its integration risk. Revisit if CI is ever introduced.
- **Screenshot testing (Paparazzi) is not used.** No released version confirms compatibility with
  this repo's AGP 9.2.1. The adaptive/posture variants (`RegimenPosture.Compact/BookOrExpanded/
  Tabletop`) and `LineChart`/`Sparkline` Canvas rendering are hand-verified on the AVD instead.
- **Coverage-percentage targets are not a goal.** Tests are picked for specific logic worth
  protecting (branching use cases, ViewModels with reconciliation/derived state, DAO joins, shared
  UI components), not to hit a number.
- **AVD note:** instrumented (`connectedAndroidTest`) runs need a stable, released API level -
  bleeding-edge/preview system images can break Espresso's legacy `InputManager` reflection used
  for gesture injection, unrelated to anything in this repo's code.

## Shared test-support modules

- **`:core:testing`** (pure Kotlin, `regimen.jvm.library` + `regimen.jvm.test`) - depended on via
  `testImplementation` by `:core:domain` and every `:feature:*` module that needs it. Houses:
    - `FakeRoutineRepository`, `FakeWorkoutRepository`, `FakeExerciseRepository`,
      `FakeMeasurementRepository`, `FakePreferencesRepository` - real in-memory implementations of
      `:core:domain`'s repository interfaces, each with a `seed(...)` helper.
      `FakeWorkoutRepository`'s
      "finished workout" queries (`observeCompleted`, `getMostRecentForRoutine`,
      `getMostRecentSetForExercise`, `observePersonalRecords`, `observeBestReps`,
      `observeBestWeight`,
      `observeExerciseHistory`) key off `workoutStatus IN (COMPLETE, EDITING)`, mirroring the real
      Room DAO's `WHERE workoutStatus IN ('COMPLETE', 'EDITING')` - not `endTime IS NOT NULL`,
      since an `EDITING`-status workout still has `endTime` set but a workout is only "finished"
      once its status says so.
    - `FakeClock` - settable `Clock` (see below) for deterministic time-dependent tests.
    - `FakeRestAlerts` - fake for the rest-timer alert side-effect.
    - `MainDispatcherRule` - JUnit `TestWatcher` that sets `Dispatchers.Main` to a `TestDispatcher`
      (defaults to `UnconfinedTestDispatcher`, override with `StandardTestDispatcher` when a test
      needs virtual-time control shared with `runTest`).
- **`:core:testing-android`** (`regimen.android.library`) - Android-only counterpart, since
  `android.os.Bundle` isn't on `:core:testing`'s classpath (that module is deliberately pure
  Kotlin because `:core:domain`, a plain JVM module, also depends on it - a JVM module can't
  cleanly depend on an Android-library project). Houses `FakeBundleRule`: JVM unit tests that
  construct `SavedStateHandle(mapOf(...))` and call `.toRoute<Route>()` on it hit a real `Bundle`
  internally (Navigation's `NavType` decoding bridges through one), which throws "not mocked"
  against the unmocked Android SDK stub jar. `FakeBundleRule` uses MockK's
  `mockkConstructor(Bundle::class)` to back every `Bundle` instance with a real in-memory map, so
  `putLong`/`getLong` (and Int/String/Boolean) actually round-trip instead of throwing or (as
  `testOptions.unitTests.isReturnDefaultValues` would) silently returning wrong defaults. Any
  ViewModel test whose ViewModel extracts a route arg via `SavedStateHandle.toRoute()` needs
  `@get:Rule val fakeBundleRule = FakeBundleRule()`.
- **`Clock`** (`:core:domain/util/Clock.kt`) - abstraction over `System.currentTimeMillis()` for
  every use case that reads the current time (`StartWorkoutUseCase`, `FinishWorkoutUseCase`,
  `PauseWorkoutUseCase`, `ResumeWorkoutUseCase`, `StartRestUseCase`, `AdjustRestUseCase`,
  `RepeatWorkoutUseCase`) and `ActiveWorkoutViewModel`'s rest-timer loop. `SystemClock`
  (`:core:data/util`) is the real Hilt-bound implementation (`ClockModule`); `FakeClock` is the
  test double. This is what makes exact-value assertions possible for things like rest-timer
  clamping, instead of tolerance-window assertions against real wall-clock time.

## `:core:domain` - JVM unit tests

`src/test/kotlin/.../domain/usecase/` is split into subpackages mirroring how the *production*
code itself groups use cases (`WorkoutUseCases.kt`, `ExerciseUseCases.kt`, `HomeUseCases.kt`,
`ProgressUseCases.kt`, `RoutineUseCases.kt`) - `usecase/workout/`, `usecase/exercise/`,
`usecase/home/`, `usecase/progress/`, `usecase/routine/`. `util/` holds `UnitConverterTest`.

Tested: every use case with real branching or derived-state logic - `UnitConverter`,
`GetHomeSummaryUseCase`, `StartWorkoutUseCase`, `RepeatWorkoutUseCase`,
`SaveWorkoutAsRoutineUseCase`, `AddExercisesToWorkoutUseCase`, `GetPersonalRecordsUseCase`,
`GetWorkoutFrequencyUseCase`, `DeleteExerciseUseCase`, `HasRoutinesUseCase`,
`FinishWorkoutUseCase`, `ResumeWorkoutUseCase`, `AdjustRestUseCase`, `ObserveExercisesUseCase`,
`AddSetUseCase`, `UpdateWorkoutNoteUseCase`.

Skipped: one-line repository pass-throughs (the rest of `RoutineUseCases`, `MeasurementUseCases`,
`PreferenceUseCases`, and the simple observe/delete one-liners in `WorkoutUseCases`/
`ExerciseUseCases`) - reserve unit tests for use cases with actual logic.

## `:core:data` - `androidTest` (Room DAO + migrations)

- `WorkoutDaoTest` - the raw-SQL JOIN queries (`observeBestWeight`, `observePersonalRecords`,
  `observeBestReps`, `getMostRecentSetForExercise`, `observeExerciseHistory`,
  `getMostRecentCompletedForRoutine`) plus the other `@Transaction`/`@Relation` queries
  (`observeCompletedWithDetails`, `observeWorkout`, `getWorkoutWithDetails`,
  `getInProgressWorkout`).
- `RoutineDaoTest` - relation queries (`observeRoutinesWithExercises`, `observeRoutine`,
  `getRoutineWithExercises`) and hand-rolled `@Transaction` methods (`applyOrder`,
  `replaceRoutineExercises`).
- `MigrationTest` - covers `MIGRATION_5_6` and `MIGRATION_6_7` via `MigrationTestHelper` against
  the real committed schema JSONs. `MIGRATION_4_5` is not covered: schema `4.json` was never
  committed to `core/data/schemas/` (only `5.json`/`6.json`/`7.json` exist), so there's no "from"
  schema to construct that migration's starting DB.
- Skipped: `ExerciseDao`, `MeasurementDao` - pure single-table CRUD, no `@Relation`/`@Transaction`/
  hand-written joins.

## `:core:designsystem` - `androidTest` (Compose UI)

Covered: `ConfirmDialog`, `ExercisePickerSheet`, `SaveAsRoutineDialog`, `Stat`, `EmptyState`,
`SectionHeader`, `UnitSystemSelector`, `ThemeModeSelector`, and the
drag-reorder primitives in `dragdrop/` (`DragDropState`/`Modifier.dragHandle` - there's no
standalone `ReorderableList` composable; `RoutinesScreen.kt` wires the primitives directly, and
`ReorderableListTest` tests them the same way via a small host composable).

Drag-gesture tests need moves comfortably past the platform's touch-slop threshold (`~18dp`) or
`detectDragGestures` never recognizes them as a drag at all - a move needs to be near-instantly
distinguishable from a tap.

Not covered: adaptive posture variants, `LineChart`/`Sparkline` (Paparazzi deferred, see Approach).

## `:app` - `androidTest` (Hilt-driven Compose UI)

The only module with Hilt wired into its instrumentation tests. `HiltTestRunner` (swaps in
`HiltTestApplication`) plus `TestDatabaseModule` (`@TestInstallIn`, replaces `DatabaseModule` with
an in-memory Room instance - a clean slate every run instead of whatever's on the test device) are
the reusable pieces; a test seeds whatever data it needs directly through the injected
`RegimenDatabase`'s DAOs, then drives the real app via `createAndroidComposeRule<MainActivity>()`.

- `ActiveWorkoutSheetBehaviorTest` - the persistent `ActiveWorkoutSheet`'s mount → expand →
  collapse → tab-switch → re-expand → tab-switch-while-expanded flow, end to end: no workout in
  progress (no banner) → Home's Start Workout → sheet auto-expands → back-press collapses it →
  the banner persists across tab switches → tapping the banner re-expands it → switching tabs
  while expanded collapses it first rather than leaving it stuck on top. Runs against the real
  Hilt graph (real ViewModels, real `StartWorkoutUseCase`), not fakes - this is the one place in
  the suite that exercises Home → the sheet → tab navigation as a real, wired-together flow rather
  than each piece in isolation. Onboarding is skipped via its always-present "Skip" button rather
  than an additional DataStore override, since its completion is a separate persisted preference
  `TestDatabaseModule` doesn't touch.

## `:feature:*` - ViewModel JVM unit tests

Tested: `RoutineEditorViewModel` (`setExercises()` reconciliation), `RoutinesListViewModel`
(optimistic drag-reorder overlay), `HomeViewModel` (quick-start ordering + aggregation),
`ProgressViewModel` (PR value resolution, muscle-group ordering), `ExerciseDetailViewModel` (PR
branching), `ExerciseLibraryViewModel` (filter-toggle chain), `ActiveWorkoutViewModel` (rest-timer
countdown/auto-complete/clamping - uses `StandardTestDispatcher` shared between `MainDispatcherRule`
and `runTest` so `runCurrent()`/`advanceTimeBy()` actually drive `viewModelScope`),
`WorkoutSummaryViewModel` (volume summation + PR comparison), `SessionDetailViewModel`
(save-as-routine eligibility), `MeasurementsViewModel` (type-driven aggregation).

Skipped entirely: `:feature:onboarding`, `:feature:settings` (pure preference pass-through
setters), `EditExerciseViewModel` (simple edit-vs-new branching), `HistoryViewModel`
(grouping/sorting only), `MeasurementDetailViewModel` (flow mapping + unit conversion only),
`EditWorkoutViewModel` (same set/cardio/note pass-through calls `ActiveWorkoutViewModel` already
covers, minus the rest-timer branching that's actually tested).

Combine()-based `StateFlow`s built from several independently-mutating fakes can legitimately
emit more intermediate states than a test consumes (each fake mutation can trigger its own
recombination pass) - tests that poll incrementally toward a target state should call
`cancelAndIgnoreRemainingEvents()` after asserting, rather than assume exactly one emission.

## Not tested, by design

- The foreground service's *own* behavior (persistent notification content, Pause/Resume from the
  notification, rest-complete alerts) - high setup cost for the value delivered.
  `ActiveWorkoutSheetBehaviorTest` does trigger the real service as a side effect of starting a
  workout for real (not stubbed), but doesn't assert anything about it.
- Anything covered by the "not tested" notes above (adaptive posture, Paparazzi-deferred
  rendering, pure pass-through modules).
