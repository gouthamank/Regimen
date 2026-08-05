# Health Connect: attach heart-rate/calorie data to sessions (not started)

Regimen's Active Workout logs sets, reps, weight, and cardio, but has no biometric data at all -
no heart rate, no calories burned. Wearables that already track this (Fitbit, Samsung Galaxy
Watch, Pixel Watch, etc.) write it into Android's on-device **Health Connect** store once their
own app syncs, so Regimen can read it back out locally, with no account linking or network calls
of its own.

## The idea

Read `HeartRateRecord`, `ActiveCaloriesBurnedRecord`, and `TotalCaloriesBurnedRecord` (and possibly
`ExerciseSessionRecord` for a clean session boundary) from Health Connect via the
`androidx.health.connect.client` Jetpack API, scoped to a Regimen session's start/end timestamps,
and attach a per-session summary (avg/max BPM, calories burned) to the workout - likely a small
satellite table or a few nullable columns in `:core:data`, surfaced as a chart in Workout Summary
and/or History reusing `:core:designsystem`'s existing `chart/LineChart.kt`/`Sparkline`.

## Why this covers Fitbit (and anything else)

Fitbit devices can't run a third-party companion app anymore (Fitbit shut down their App
Gallery/SDK for developers), so there's no way to build a Fitbit-specific integration the way
there is for Wear OS (see `todo-wearos-companion.md`). But the Fitbit Android app can be configured
(user-confirmed: a setting exists in the Fitbit app itself) to write its data into Health Connect.
Regimen reading from Health Connect therefore covers Fitbit, Samsung Health, and any other source
app that syncs there - one integration point instead of one per vendor.

## Constraints

- Requires Health Connect (bundled on modern Android; older devices get a Play Store prompt) and
  the user granting read permission per record type Regimen asks for.
- **Not live, and not guaranteed present at Finish.** Data lands in Health Connect whenever the
  source app syncs (Bluetooth pairing lag, source app not opened, etc.) - anywhere from seconds to
  hours after the fact, sometimes never. A single query at session-finish time will often find
  nothing; this needs a retry/backfill mechanism, not a one-shot fetch (see "Sync mechanism"
  below). That's also the deciding trade-off against the Wear OS companion app, which is live.
- No control channel of any kind - Health Connect is read-only data, not a device link.
- Matches Regimen's local-first stance: on-device only, opt-in permission, no new backend, no
  account - reinforced by requiring an **explicit opt-in control in Settings** (see below), never
  an opportunistic permission prompt during a workout.
- **Play Store compliance**: publishing an app that requests Health Connect permissions requires
  completing Play Console's "Health apps" declaration (per-permission written justification) and
  linking a privacy policy that matches what Health Connect shows the user. Not a blocker for
  local/sideload use, but a real gate before any Play Store release once this ships.

## Record types & storage

- `HeartRateRecord` (sample series - gives both avg/max and a sparkline) and
  `ActiveCaloriesBurnedRecord` (excludes basal/resting burn, so it's the number actually
  attributable to the workout, unlike `TotalCaloriesBurnedRecord`).
- New satellite table, e.g. `WorkoutBiometrics(id, workoutId, avgBpm?, maxBpm?, activeCaloriesKcal?,
  sourcePackageName?, fetchedAt, isDirty, lastModifiedAt)`, following the same shape as
  `SetEntry`/`CardioEntry` (hangs off `Workout` by foreign key) rather than adding nullable columns
  onto `Workout` itself. `sourcePackageName` comes from the most recent matching record's
  `metadata.dataOrigin.packageName` - also what the Settings status widget displays.
- Computed and persisted once at pull time (by the retry job below), not read-through live at
  display time - Workout Summary/History just reads the stored row, same as everything else on
  those screens.

## Sync mechanism (retry/backfill job)

- A periodic `CoroutineWorker`, following the exact same shape `:core:sync`'s `SyncPushWorker`/
  `SyncSchedulerImpl` already use for the Firestore push job: on each run, find `COMPLETE`
  workouts within the configured backfill window that don't yet have a `WorkoutBiometrics` row (or
  have one still missing data), query Health Connect for each one's `[startTime, endTime]`, and
  write/update the summary for whatever's found.
- Gated entirely behind the Settings toggle below - the job isn't scheduled at all while the
  master switch is off.
- A manual "Pull now" action (same role as Account screen's "Sync now") lets a user force an
  immediate attempt without waiting for the next scheduled run. Its behavior is state-dependent,
  mirroring how Account's own primary button does double duty depending on sign-in state:
    - *Needs permission* → tapping launches the Health Connect permission request flow; nothing is
      pulled yet, it just tries to reach *Active*.
    - *Active* → tapping enqueues a one-shot run of the same backfill sweep the periodic job does
      (global, not scoped to one workout, since this button lives on the Settings page); busy state
      shown via the same `ButtonProgressIndicator` Account uses, and the status widget's last-pull
      timestamp/detected-source line updates on completion.
    - *Health Connect not installed* → button isn't shown at all.

## Settings UI

A **new dedicated sub-page**, launched from Settings (not an inline toggle), following
`feature/account/AccountScreen.kt`'s existing shell/shape (`MediumTopAppBar` + back nav, a status
block, sections divided by `HorizontalDivider`, a manual action button alongside the status row):

- **Status widget**: three-state connection status - *Active* / *Needs permission* / *Health
  Connect not installed* (a user can revoke the permission from Health Connect's own settings
  outside Regimen entirely, so this can't just be a boolean); detected source app (from
  `WorkoutBiometrics.sourcePackageName` on the most recent successful pull, e.g. "Currently syncing
  from: Fitbit", or "No data seen yet"); last successful pull timestamp.
- **Master switch**: "Automatically pull biometrics" - off by default (explicit opt-in, per
  above). Gates every control below and whether the retry job is scheduled at all.
- **Retry frequency** picker (`SingleChoiceSegmentedButtonRow`, same component
  `UnitSystemSelector`/`ThemeModeSelector` already use): **1 hour / 6 hours / Daily**, defaulting
  to **6 hours**. No 15-minute option - Health Connect data usually isn't available that fast
  anyway, and a tighter floor would just encourage a battery-hungry setting for no real benefit.
- **Backfill window** picker (same segmented-row component): **1 / 3 / 7 / 30 days**, defaulting to
  **7 days**, controlling how far back the retry job keeps looking for workouts still missing data
  before giving up on them for good.
- **Manual "Pull now" button**, next to the status row.
- All four settings (toggle, frequency, backfill window, plus derived scheduling state) persisted
  via DataStore prefs (`:core:data`'s `data/prefs/`), alongside the existing unit-system/theme-mode
  preferences.

## Cloud sync (phase 2)

`WorkoutBiometrics` is in scope for Firestore sync (per "Remote sync" in `docs/architecture.md`),
but sequenced as a fast-follow, not part of the initial build:

- **Why it's in scope at all**: Health Connect itself never syncs across a user's own devices -
  it's a per-device, on-device store. Without Regimen syncing `WorkoutBiometrics` too, a workout
  pulled on one device stays permanently invisible on any other device signed into the same
  account, with no error or indication - just a silent gap. That gap disproportionately hurts the
  exact users who'd enable this feature at all: people trending heart-rate/calorie data over time
  in Progress, where a multi-device gap quietly corrupts the aggregate rather than just being a
  one-off missing number.
- **Why it's still phase 2, not phase 1**: adding a new synced entity type is real, bounded work -
  a `SyncPushRunner` write lambda, a Firestore DTO + mapping, tombstone handling for deletions, and
  the same manual-verification bar `docs/architecture.md`'s schema-evolution section requires for
  every synced entity - and it's more sensitive data than sets/reps/weight, worth being deliberate
  about rather than inheriting scope just because the push job already exists. Ship the Health
  Connect pull itself first (get it correctly working and verified, local-only), then layer sync on
  top the same additive way `CardioEntry`/per-exercise notes were layered onto the existing push
  job - a known, bounded pattern, not a redesign.

## Testing during development

- **Business logic** (backfill-window matching, retry scheduling, permission-state branching):
  fakes-first, same as everywhere else in the repo (`docs/testing.md`) - a `HealthConnectRepository`
  interface in `:core:domain`, real implementation in `:core:data`, `FakeHealthConnectRepository`
  in the shared test-support module for ViewModel/use-case JVM tests. No real Health Connect
  needed for this tier.
- **Real integration (does data actually flow in)** needs an emulator with Health Connect present
    - use an API 34+ Google Play system image (Health Connect is built into the OS from 34 on;
      older API levels need it installed separately from Play Store).
    - Health Connect's own Settings UI only manages permissions - it has **no way to manually add a
      record**, so it can't be used to seed test data. Use Google's **Health Connect Toolbox**
      (open-source sample app in the `androidx/health-samples` GitHub repo), installed alongside
      Regimen on the same emulator/device, to insert arbitrary `HeartRateRecord`/calorie records for
      a chosen time range - the legitimate way to seed test data here, equivalent to making test
      data
      through a real UI rather than hand-editing storage directly.
    - Exercise the *Needs permission* state by denying the permission prompt instead of granting it;
      exercise *Health Connect not installed* with an API <34 emulator image that skips installing
      the Health Connect app.
    - A real device paired with an actual Fitbit/watch is worth one end-to-end pass before shipping,
      but it's a slow feedback loop - not for iterative development.

## Implementation checklist (phase 1 - local pull only, no cloud sync)

Roughly dependency-ordered - each layer below assumes the previous one exists.

**Module**

- [ ] New `:feature:healthconnect` module (`regimen.android.feature` convention plugin), mirroring
  `:feature:account`'s precedent of a pushed settings sub-page getting its own module rather than
  living inside `:feature:settings` itself.

**`:core:domain`**

- [ ] `WorkoutBiometrics` domain model (`avgBpm?`, `maxBpm?`, `activeCaloriesKcal?`,
  `sourcePackageName?`, `fetchedAt`).
- [ ] `HealthConnectConnectionState` domain model/enum: `ACTIVE` / `NEEDS_PERMISSION` /
  `NOT_INSTALLED`.
- [ ] `HealthConnectPrefs` domain model: `autoPullEnabled: Boolean`, `retryFrequency` enum
  (`ONE_HOUR` / `SIX_HOURS` / `DAILY`, default `SIX_HOURS`), `backfillWindowDays` enum (`ONE` /
  `THREE` / `SEVEN` / `THIRTY`, default `SEVEN`).
- [ ] `HealthConnectRepository` interface: connection-state check, permission request trigger,
  query-and-summarize for a given `[startTime, endTime]`, read/write `HealthConnectPrefs`, read
  latest pulled summary/status for the widget (detected source, last pull time).
- [ ] `WorkoutBiometricsRepository` interface (or fold into `WorkoutRepository` - decide at
  implementation time): read/write `WorkoutBiometrics` rows, list `COMPLETE` workouts within the
  backfill window missing a row.
- [ ] Use cases: `GetHealthConnectStatusUseCase`, `SetHealthConnectPrefsUseCase`,
  `RequestHealthConnectPermissionUseCase`, `PullBiometricsForWorkoutUseCase` (single workout - the
  unit the backfill sweep and "Pull now" both call), `RunBiometricsBackfillUseCase` (the sweep
  itself: find candidates in-window, call the single-workout use case for each).

**`:core:data`**

- [ ] Room: `WorkoutBiometrics` entity + DAO + migration (bumps database version past 11).
- [ ] `HealthConnectRepositoryImpl` wrapping `androidx.health.connect.client.HealthConnectClient` -
  `getSdkStatus()` for install/update state, `PermissionController` for granted-permission check
  and request, record queries (`HeartRateRecord`, `ActiveCaloriesBurnedRecord`) scoped by time
  range, `metadata.dataOrigin.packageName` extraction for detected source.
- [ ] `WorkoutBiometricsRepositoryImpl` backed by the new DAO.
- [ ] DataStore: `HealthConnectPrefs` keys alongside the existing unit-system/theme-mode
  preferences in `data/prefs/`.
- [ ] `HealthConnectBiometricsWorker` (`CoroutineWorker`), mirroring `SyncPushWorker`'s shape -
  runs `RunBiometricsBackfillUseCase`.
- [ ] `HealthConnectSchedulerImpl` mirroring `SyncSchedulerImpl` - (re)schedules the periodic
  `WorkManager` request on prefs change (frequency change, or toggling auto-pull on/off cancels/
  reschedules), exposed via a `HealthConnectScheduleRepository` interface in `:core:domain`.

**Manifest / permissions**

- [ ] Declare `android.permission.health.READ_HEART_RATE` and
  `android.permission.health.READ_ACTIVE_CALORIES_BURNED` in `:app`'s manifest, plus the
  permissions-rationale intent filter Health Connect requires for the app to show up as a
  connected app in its own Settings UI.

**`:feature:healthconnect`**

- [ ] `HealthConnectSettingsViewModel`: exposes status widget state (connection state, detected
  source, last pull time) + current prefs; handles toggle/frequency/backfill-window changes,
  "Pull now" (state-dependent: request permission vs. trigger one-shot backfill run), and
  refresh-on-resume (permission may have been revoked externally, same pattern
  `AccountViewModel.refreshOnResume` already uses).
- [ ] `HealthConnectSettingsScreen`: shell/shape copied from `feature/account/AccountScreen.kt`
  (`MediumTopAppBar` + back, status block, `HorizontalDivider`-separated sections) - status
  widget, master switch, two `SingleChoiceSegmentedButtonRow` pickers (reusing the same component
  `UnitSystemSelector`/`ThemeModeSelector` use), "Pull now" button with `ButtonProgressIndicator`
  busy state.
- [ ] `HealthConnectNavigation.kt`: `NavGraphBuilder.healthConnectGraph()` extension, per module
  convention.

**Wiring**

- [ ] `feature/settings/SettingsScreen.kt`: new `NavRow` entry ("Health Connect") in the
  library/data section, alongside the existing Exercise Library / Account rows.
- [ ] `:app`'s `RegimenNavHost.kt`: wire the new destination, update the ASCII navigation-map
  comment (flip `[ ]` → `[✓]`).
- [ ] Workout Summary / History: display the pulled `WorkoutBiometrics` summary (avg/max BPM,
  calories) when present, reusing `:core:designsystem`'s `chart/LineChart.kt`/`Sparkline` for the
  heart-rate series.
- [ ] All new user-facing strings added to `res/values/strings.xml` (never hardcoded), per
  `docs/conventions.md`.

**Tests** (per `docs/testing.md`'s fakes-first approach)

- [ ] `FakeHealthConnectRepository` / `FakeWorkoutBiometricsRepository` in the shared test-support
  module.
- [ ] `:core:domain` JVM unit tests for each new use case (especially the backfill-window
  candidate-selection logic and the "Pull now" state-dependent branching).
- [ ] `:feature:healthconnect` ViewModel JVM unit tests.
- [ ] `:core:data` `androidTest` for the `WorkoutBiometrics` DAO/migration.
- [ ] Manual verification pass per the "Testing during development" section above (Health Connect
  Toolbox-seeded data on an emulator) - real `HealthConnectClient` wiring isn't covered by any
  automated test tier.

## Status

- [ ] Not started.
