# Testing strategy (discussion draft)

Not an actionable plan yet — this lays out what could be tested, with which tools, and the real
tradeoffs of each, so we can decide together what's worth doing. The app currently has **zero**
tests (no `app/src/test/` or `app/src/androidTest/` content beyond the default template, if that).

## Sequencing

**Actual test-writing is deferred until after `docs/todo-multi-module-migration.md` completes.**
This doc stays a discussion/decisions record in the meantime — everything below is agreed
direction, not yet-written code. One consequence: the "where do fakes live" question resolves
itself for free — by the time any fake gets written, `:core:testing` (or equivalent) already
exists as part of the migration, so there's no separate location to pick now.

## Decisions made so far

- **§1 (pure unit tests):** skip testing one-line repository pass-through use cases individually;
  reserve unit tests for use cases with actual branching/logic.
- **§3 (fakes vs. MockK):** fakes-first. See "Reference: the fake pattern" below for a worked
  example against `RoutineRepository`/`RoutineEditorViewModel`.
- **JUnit4** over JUnit5 (Android's own tooling — Compose test rules, `AndroidJUnit4` runner —
  is built around it most smoothly; nothing here needs JUnit5's extension model badly).
- **§4 (screenshot testing):** yes, Paparazzi — given `docs/todo-foldable-rollout.md` already
  tracks a lot of hand-verified-on-AVD posture-specific layout decisions that screenshot tests
  would catch automatically.
- **Turbine**: yes — standard tool for asserting `StateFlow`/`Flow` emission sequences, cheap
  dependency, avoids hand-rolled `flow.take(n).toList()` assertions.
- **§2 (Room DAO test scope):** see below — resolved with a concrete per-DAO breakdown.

## Reference: the fake pattern

Fakes need a repository *interface* to implement — that's Phase 2/3 of
`docs/todo-multi-module-migration.md`, but introducing one interface at a time is low-risk and
doesn't require waiting for the full migration:

```kotlin
// domain/repository/RoutineRepository.kt — interface, owned by domain
interface RoutineRepository {
    fun observeAll(): Flow<List<RoutineWithExercises>>
    fun observeRoutine(id: Long): Flow<RoutineWithExercises?>
    suspend fun getRoutine(id: Long): RoutineWithExercises?
    suspend fun isExerciseUsed(exerciseId: Long): Boolean
    suspend fun saveRoutine(routineId: Long?, name: String, specs: List<ExerciseSpec>): Long
    suspend fun delete(routine: Routine)
    suspend fun reorder(orderedIds: List<Long>)
}

// data/repository/RoutineRepositoryImpl.kt — today's class, renamed, implements it
@Singleton
class RoutineRepositoryImpl @Inject constructor(private val dao: RoutineDao) : RoutineRepository {
    // same bodies as today, just `override`
}

// test fixtures — real in-memory behavior, not stubbed returns
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
so it only covers cases you thought to stub. `RoutineEditorViewModel.setExercises()`'s
reconciliation logic (keep customized entries for still-checked ids, drop unchecked, append new)
is a good first real target — it's pure in-memory state logic with no repository call at all,
exactly the kind of use-case-adjacent logic §1 said is worth testing directly.

---

## What exists today, and what it implies

- No test source sets are populated. Every use case, ViewModel, formatter, and Composable in the
  app is currently unverified except by manual/emulator checking.
- The just-finished string-externalization pass changed the *testability shape* of several
  functions worth flagging up front:
    - `UnitConverter.formatValue/kgToDisplay/displayToKg/metersToDisplay/displayToMeters` are still
      plain pure functions (`Double -> Double`, `Double -> String`) — trivially unit-testable with
      no framework at all.
    - `SessionFormat.duration/setLabel/cardioLabel`, `MeasurementFormat.unitLabel/format`,
      `ExerciseLabels`' `.label()` extensions, and `UnitConverter.weightLabel/distanceLabel` are now
      all `@Composable` (they call `stringResource`/`pluralStringResource`). **This means testing
      them requires a Compose test rule (`createComposeRule`), not a plain JUnit test** — a
      reasonable tradeoff for correct localization, but worth naming explicitly: these went from
      "test in milliseconds with zero Android dependency" to "test through Compose's test harness."
      Worth discussing whether that tradeoff is accepted as-is, or whether it changes your appetite
      for testing these specific functions.

## The testing pyramid, mapped to this codebase

```
        ▲  Compose UI tests (screen-level)         — slow, few
       ╱ ╲ ViewModel tests (StateFlow + fakes)      — medium
      ╱   ╲ Room DAO tests (in-memory DB)           — medium
     ╱─────╲ Pure unit tests (use cases, formatters) — fast, many
```

### 1. Pure unit tests — no framework beyond JUnit

Candidates: `UnitConverter`'s math functions, any use case that's pure transformation logic (not
all of them are — most call through to a repository). Fast, zero setup, highest value-per-effort.
**Decided:** skip the one-line repository pass-throughs (e.g.
`ObserveRoutinesUseCase(): Flow<...> = repo.observeAll()`) — no logic in them to regress. Reserve
unit tests for use cases/ViewModel logic that actually branches (`RoutineEditorViewModel`'s
reconciliation, `HomeViewModel`'s quick-start ordering, `ProgressViewModel`'s PR-hit detection).

### 2. Room DAO tests — in-memory database

`Room.inMemoryDatabaseBuilder(...)` gives a real (if ephemeral) SQLite instance — these are the
highest-confidence tests for query correctness since they exercise real SQL, not a fake.
**Decided, per-DAO:**

- **Skip:** `ExerciseDao`, `MeasurementDao` — pure single-table CRUD, no `@Relation`/`@Transaction`,
  no hand-written joins. Room's generated code for `@Insert`/`@Update`/`@Delete`/simple
  `@Query`-by-id has a low enough bug surface that testing it mostly re-verifies Room itself.
- **Test:** `RoutineDao`'s `@Transaction` relation queries (`observeRoutinesWithExercises`,
  `observeRoutine`, `getRoutineWithExercises` — assemble `RoutineWithExercises` across tables) and
  its two hand-rolled `@Transaction` methods (`applyOrder`, `replaceRoutineExercises` — multi-step
  writes where a partial-failure or ordering bug can't be caught by inspection or by a fake).
- **Test, highest priority:** `WorkoutDao`'s hand-written raw-SQL queries —
  `observeBestWeight`/`observePersonalRecords`/`observeBestReps` (multi-table `JOIN` +
  `GROUP BY`, gated on `w.endTime IS NOT NULL AND se.isComplete = 1`, and splitting weight-PRs
  from bodyweight-rep-PRs on `se.weightKg IS NULL` vs. `IS NOT NULL`), plus
  `getMostRecentSetForExercise`, `observeExerciseHistory`, `getMostRecentCompletedForRoutine`.
  These directly implement the app's personal-record and history logic across Home/Progress/
  Exercise Detail/Workout Summary — a wrong `JOIN` condition or a dropped filter here silently
  corrupts a user's PRs with zero compile-time signal. This is the single highest-value DAO
  testing target in the app. Also test `WorkoutDao`'s other `@Transaction`/`@Relation` queries
  (`observeCompletedWithDetails`, `observeWorkout`, `getWorkoutWithDetails`,
  `getInProgressWorkout`) for the same reason as `RoutineDao`'s relation queries.
- **Migration test suite: yes.** Only one migration exists today (`MIGRATION_4_5`), and it's not
  a simple Room-generated `ALTER TABLE` — it's hand-written raw SQL doing a full table rebuild
  (`CREATE workouts_new` → copy rows → `DROP` → `RENAME` → recreate the index), specifically
  because `ALTER TABLE ... DROP COLUMN` isn't reliable across Android's SQLite versions. That's
  exactly the shape of migration most likely to have a copy-paste column-list bug or a forgotten
  index. Worth a `MigrationTestHelper`-based test now, while it's the only migration — establishes
  the pattern so every future migration gets the same treatment as a matter of course rather than
  re-litigating "should we test this one."

### 3. ViewModel tests — the highest-value tier for this app specifically

Given the architecture (ViewModels combine multiple `Flow`s from use cases into one
`StateFlow<UiState>`),
this is where most of the app's actual logic lives — reconciliation, formatting decisions,
conditional UI state (e.g. `ExerciseDetailUiState.pr`, `HomeViewModel`'s quick-start ordering,
`ActiveWorkoutViewModel`'s rest-timer countdown logic). Needs:

- **Fakes vs. mocks for use cases/repositories. Decided: fakes-first** (see "Reference: the fake
  pattern" above) — reach for MockK only where a fake would be disproportionate work (e.g. a use
  case with many methods you'd only stub one of for a single test).
- **Turbine** (`app.cash.turbine`) for asserting `StateFlow`/`Flow` emissions in tests — the
  de facto standard for this in Kotlin coroutines codebases, avoids hand-rolled
  `runTest { flow.take(n).toList() }` boilerplate.
- **`kotlinx-coroutines-test`**'s `TestDispatcher`/`runTest` for controlling `viewModelScope`
  timing (several ViewModels use `delay()` — `ActiveWorkoutViewModel`'s rest timer,
  `RestAlerts`) — needs a virtual-time-aware dispatcher or these tests will be flaky/slow.
- **Rest-timer specifically**: `ActiveWorkoutViewModel.startRest`'s
  `while (isActive) { delay(...) }`
  loop is a good candidate for demonstrating `TestDispatcher`'s virtual time (advance time by 15s
  instantly instead of actually waiting), but is also inherently one of the fussier things to test
  correctly — worth calling out as a "do this one carefully, maybe last" item rather than a first
  example.

### 4. Compose UI tests — screen-level, semantics-based

`createComposeRule()` (or `createAndroidComposeRule<ComponentActivity>()`) + `onNodeWithText`/
`onNodeWithContentDescription` + `performClick()`. High confidence, slow to write and run relative
to the tiers above. **Open questions:**

- Full screens (e.g. does tapping "Delete" in Exercise Detail's dialog actually call `onDelete`?)
  or just the newly-extracted shared components from the multi-module plan (`ConfirmDialog`,
  `StatTile`, `EmptyState`) once those exist — testing the shared component once instead of every
  screen that uses it is much higher leverage.
- Golden/screenshot tests — **decided: Paparazzi** (no emulator dependency, runs as a plain JVM
  test) — for the adaptive/foldable posture variants
  (`RegimenPosture.Compact/BookOrExpanded/Tabletop`). `docs/todo-foldable-rollout.md` describes a
  lot of layout-shape decisions per posture that are currently only verified by hand on an AVD;
  screenshot tests catch layout regressions automatically instead.

### 5. What's explicitly NOT proposed (unless you want it)

- End-to-end instrumented tests driving the real foreground service (`ActiveWorkoutService`) —
  high value but high setup cost (needs a running emulator, real notification permission flow);
  flagging as "probably last, if ever."
- Full coverage-percentage targets — coverage numbers as a goal tend to produce low-value tests
  written to hit a number. Better to pick specific logic worth protecting (per tier above) and
  stop there.

---

## Multi-module implications (if the migration plan is also undertaken)

Each module gets its own test source set, which is itself one of the real benefits of
modularization worth experiencing:

- `:core:domain` — pure JUnit, no Android/Robolectric needed at all (fast).
- `:core:data` — Room in-memory DB tests, needs `androidTest` or Robolectric.
- `:core:common-ui` / `:core:designsystem` — Compose UI tests for the newly-shared components
  (this is arguably the best ROI in the whole plan: test `ConfirmDialog`/`StatTile` once, get
  coverage for every screen that uses them).
- `:feature:*` — ViewModel tests (fast, JVM-only) + a handful of Compose UI tests for
  screen-specific interaction the shared components don't cover.

## Tooling decisions

- [x] **JUnit4** over JUnit5.
- [x] **Fakes-first**, MockK as the exception, not the default.
- [x] **Turbine**, yes.
- [x] **Paparazzi** for screenshot testing.
- [x] Where do fakes live — resolved by sequencing: no test code is written until after the
  module migration, so fakes land directly in whatever `:core:testing`-equivalent module the
  migration plan establishes.
- [x] §2 (Room DAO test scope) — resolved per-DAO above; migration test suite: yes.

Nothing left open in this doc. Next real step is the multi-module migration
(`docs/todo-multi-module-migration.md`); this doc gets revisited once that's done.
