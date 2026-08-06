# Health Connect: attach heart-rate/calorie data to sessions (in progress)

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
there is for Wear OS (see `todo-wearos-companion.md`). The phone-side app a Fitbit device pairs
with is now called **Google Health**, not Fitbit - Google replaced the Fitbit app outright with
Google Health on May 19, 2026, with existing Fitbit accounts rolled over automatically. Google
Health syncs heart rate, calories, and other metrics into Health Connect (permission-gated, same
as any other source app), so Regimen reading from Health Connect covers Fitbit hardware via
Google Health, plus Samsung Health and any other source app that syncs there - one integration
point instead of one per vendor.

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
  - *Unavailable* (not installed, or installed but needs updating) → button isn't shown at all.

## Settings UI

A **new dedicated sub-page**, launched from Settings (not an inline toggle), following
`feature/account/AccountScreen.kt`'s existing shell/shape (`MediumTopAppBar` + back nav, a status
block, sections divided by `HorizontalDivider`, a manual action button alongside the status row):

- **Status widget**: three-state connection status - *Active* / *Needs permission* / *Unavailable*
  (Health Connect not installed, or installed but needs updating - a user can also revoke the
  permission from Health Connect's own settings outside Regimen entirely, so this can't just be a
  boolean); detected source app (from
  `WorkoutBiometrics.sourcePackageName` on the most recent successful pull, e.g. "Currently syncing
  from: Google Health", or "No data seen yet"); last successful pull timestamp.
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

## Cloud sync - not planned

`WorkoutBiometrics` stays local-only, permanently - it is not, and will not be, added to Firestore
sync scope (per "Remote sync" in `docs/architecture.md`).

Health Connect itself never syncs across a user's own devices - it's a per-device, on-device
store. Without Regimen syncing `WorkoutBiometrics` too, a workout pulled on one device stays
invisible on any other device signed into the same account. That gap is real (it disproportionately
affects anyone trending heart-rate/calorie data over time in Progress across more than one device),
but it's a narrow, opt-in-feature-specific cost, not one worth taking on the compliance overhead
for: health data is GDPR "special category data" (Article 9), which raises the bar past what the
rest of Regimen's synced data needs - specific, granular consent naming this exact data type
(distinct from the existing sync opt-in), and cloud-deletion coverage extended to this entity.
None of that requires end-to-end encryption (GDPR's Article 32 asks for measures proportional to
risk, not literal client-side encryption specifically - Firestore's existing per-user security
rules plus encryption in transit/at rest already qualify), and the sync mechanism itself would be
the same bounded, known pattern as any other entity added to the push job. The actual cost is the
consent/deletion-copy work and the ongoing compliance surface of having health data in the cloud
at all - accepted here as not worth it for what's ultimately a nice-to-have cross-device view.

## Testing during development

- **Business logic** (backfill-window matching, retry scheduling, permission-state branching):
  fakes-first, same as everywhere else in the repo (`docs/testing.md`) - a `HealthConnectRepository`
  interface in `:core:domain`, real implementation in `:core:healthconnect`,
  `FakeHealthConnectRepository` in the shared test-support module for ViewModel/use-case JVM tests.
  No real Health Connect needed for this tier.
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
      exercise *Unavailable* with an API <34 emulator image that skips installing the Health
      Connect app.
  - A real device paired with an actual Fitbit (via the Google Health app) or Wear OS watch is
    worth one end-to-end pass before shipping, but it's a slow feedback loop - not for iterative
    development.

## Implementation checklist (phase 1 - local pull only, no cloud sync)

Broken into checkpointed sub-phases - each one builds on its own, ends in something concretely
verifiable, and is a natural commit/PR boundary. Later sub-phases assume everything in the earlier
ones exists.

### Phase 1a - local storage foundation (no Health Connect dependency yet)

- [x] `WorkoutBiometrics` domain model (`avgBpm?`, `maxBpm?`, `activeCaloriesKcal?`,
  `sourcePackageName?`, `fetchedAt`) in `:core:domain`.
- [x] Room: `WorkoutBiometrics` entity + DAO + migration in `:core:data` (bumps database version
  past 12, to 13).
- [x] `WorkoutBiometricsRepository` interface (`:core:domain`) + impl (`:core:data`) backed by the
  new DAO: read/write rows only - candidate selection for the backfill job is composed in
  `RunBiometricsBackfillUseCase` from `WorkoutRepository`/`WorkoutBiometricsRepository` directly
  (see Phase 1c) rather than a dedicated cross-table query, so it's exercised by ordinary fakes.
- [x] `FakeWorkoutBiometricsRepository` in the shared test-support module + `:core:data`
  `androidTest`
  for the DAO (`WorkoutBiometricsDaoTest`), the repository (`WorkoutBiometricsRepositoryImplTest`),
  and the migration itself (`MigrationTest.migrate12To13_addsWorkoutBiometricsTable`, which is the
  one that actually re-validates `MIGRATION_12_13`'s SQL against Room's expected schema - the
  DAO/repo tests use a fresh in-memory schema and never execute the migration's SQL at all).
- **Checkpoint**: builds and runs completely unmodified from the user's perspective - the new
  table exists and is only exercised by tests (an `androidTest` inserting/reading a row). Nothing
  user-visible yet; this just proves the storage layer in isolation before anything depends on it.

### Phase 1b - Health Connect read integration (no scheduling, no UI)

`androidx.health.connect:connect-client:1.1.0` has been stable since October 2025 - pin it
directly, no alpha/RC churn to accept (unlike Material3 Expressive elsewhere in this codebase).

All Health Connect-touching code (the `HealthConnectClient` wrapper, its DataStore-backed prefs,
and its `WorkManager` scheduler/worker) lives in a new `:core:healthconnect` module, not
`:core:data` - the same reasoning `:core:sync` already follows for Firebase/its own push job:
wrapping an external SDK and orchestrating a feature-specific background job aren't "Room
DAOs/DataStore," which is what `:core:data` is actually scoped to. Depends only on `:core:domain`

- unlike `:core:sync`, nothing here needs a `:core:data` DAO directly.

**Manifest** (`:app`):

- [x] `android.permission.health.READ_HEART_RATE` and
  `android.permission.health.READ_ACTIVE_CALORIES_BURNED` `<uses-permission>` declarations.
- [x] `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` `<uses-permission>` - without it,
  the Phase 1c periodic backfill job can only read Health Connect data while Regimen is actually
  in the foreground; reads attempted while backgrounded silently return nothing. Not every Health
  Connect version supports background reads, and the app must keep working with whatever subset of
  permissions is actually granted - see `HealthConnectRepositoryImpl.requiredPermissions()`'s
  `HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND` availability check, and
  `getConnectionState()`'s `ACTIVE` gating only on the two core (non-background) permissions.
- [x] `<queries><package android:name="com.google.android.apps.healthdata" /></queries>` -
  without this, Android's package-visibility restrictions block Regimen from even detecting
  whether Health Connect is installed. A second `<queries><intent>` entry for
  `ACTION_SHOW_PERMISSIONS_RATIONALE` lets Regimen resolve other apps' friendly name/icon for the
  "currently syncing from: ..." attribution.
- [x] A real `PermissionsRationaleActivity` that Health Connect launches from its own Settings UI
  to show why Regimen wants this data. Hands off to Regimen's actual hosted privacy policy
  (`https://regimen.gouthaman.dev/privacy-policy/`, source in `pages/privacy-policy/`) via a plain
  `ACTION_VIEW` intent rather than duplicating an explanation in-app. One activity with two intent
  filters - `ACTION_SHOW_PERMISSIONS_RATIONALE` for pre-Android-14, plus
  `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` for Android 14+ - matching Google's own
  `health-samples` reference manifest exactly: no `activity-alias`, no `android:permission`
  attribute on either intent filter.

**`:core:domain`**:

- [x] `HealthConnectConnectionState` enum: `ACTIVE` / `NEEDS_PERMISSION` / `UNAVAILABLE` (the last
  one covers both `HealthConnectClient.getSdkStatus()`'s `SDK_UNAVAILABLE` - not installed - and
  `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` - installed but needs updating - since both resolve
  the same way from the user's side: go to the Play Store).
- [x] `HealthConnectRepository` interface: connection-state check, the required permission set
  (a plain `Set<String>` the UI layer passes to its launcher - see below, this repository never
  launches anything itself), a granted-permissions check, and query-and-summarize for a given
  `[startTime, endTime]`.
- [x] `PullBiometricsForWorkoutUseCase` (single workout) - the one place that turns a Health
  Connect query result into a `WorkoutBiometrics` row via Phase 1a's repository.

**`:core:healthconnect`**:

- [x] Add the `androidx.health.connect:connect-client:1.1.0` dependency.
- [x] `HealthConnectRepositoryImpl` wrapping `HealthConnectClient` - `getSdkStatus()` for
  install/update state, `PermissionController.getGrantedPermissions()` for the granted-permission
  check, record queries (`HeartRateRecord`, `ActiveCaloriesBurnedRecord`) scoped by time range,
  `metadata.dataOrigin.packageName` extraction for detected source.

**No permission-requesting use case.** Requesting Health Connect permissions needs
`PermissionController.createRequestPermissionResultContract()` launched via
`rememberLauncherForActivityResult` - an `ActivityResultLauncher`, which only exists at the
Compose/Activity layer, the same way `:app`'s `RegimenApp.kt:125-134` already requests
`POST_NOTIFICATIONS` (
`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`
from a top-level `LaunchedEffect`). So there's no `RequestHealthConnectPermissionUseCase` - the
actual `launcher.launch(...)` call lives in `HealthConnectSettingsScreen` (Phase 1d), with the
launcher's result callback feeding back into `HealthConnectSettingsViewModel` to re-check
connection state, the same shape `AccountViewModel.refreshOnResume()` already uses.

- [x] `FakeHealthConnectRepository` + unit test for `PullBiometricsForWorkoutUseCase`.
- [x] **Checkpoint**: verified on-emulator against the real `HealthConnectClient`, seeded via
  Health Connect Toolbox (see `HealthConnectManualVerificationTest` in `:app`'s androidTest, kept
  `@Ignore`d since it needs manual setup right before each run) - `PullBiometricsForWorkoutUseCase`
  correctly pulled avg/max BPM, calories, and source-app attribution matching the seeded data.

### Phase 1c - prefs + background backfill job

Reliability here depends on `android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` (Phase
1b) - without it granted, this job's periodic runs return nothing while Regimen is backgrounded,
which is most of the time for a periodic job. It's still a graceful degrade, not a broken state:
runs still execute, "Pull now" (foregrounded) still works, and a later run picks up anything that
becomes available once the app's opened again - just worth setting expectations that the
backfill job's real-world hit rate depends on this optional permission.

- [x] `HealthConnectPrefs` domain model (`autoPullEnabled: Boolean` default `false`,
  `retryFrequency: HealthConnectRetryFrequency` default `SIX_HOURS`,
  `backfillWindow: HealthConnectBackfillWindow` default `SEVEN`) in `:core:domain`'s
  `HealthConnect.kt`;
  the two enums (`ONE_HOUR`/`SIX_HOURS`/`DAILY` and `ONE`/`THREE`/`SEVEN`/`THIRTY`) live in
  `Enums.kt` alongside `MaxWorkoutDuration`/`HistoryRange`.
- [x] `HealthConnectPrefsRepository` interface (`:core:domain`) + `HealthConnectPrefsRepositoryImpl`
  (`:core:healthconnect`, its own DataStore file `health_connect_settings` - deliberately separate
  from `:core:data`'s `PreferencesRepositoryImpl`, which is pushed to Firestore wholesale; these
  must never end up in that scope).
- [x] `RunBiometricsBackfillUseCase`: finds `COMPLETE` workouts in the configured backfill window
  missing a `WorkoutBiometrics` row, calls `PullBiometricsForWorkoutUseCase` for each. Composed
  directly from `WorkoutRepository.observeCompletedBetween(...)` (completed ids) filtered by
  `WorkoutBiometricsRepository.get(id) == null` (missing ones) - per Phase 1a's open design note,
  `getCompletedWorkoutIdsMissingBiometrics` was removed from `WorkoutBiometricsRepository`/its DAO
  entirely rather than kept as a dedicated cross-table query, since this composed form is
  exercised by ordinary fakes with no special-case testing hook needed.
- [x] `HealthConnectScheduleRepository` interface (`:core:domain`) + `HealthConnectSchedulerImpl`
  (`:core:healthconnect`) mirroring `SyncSchedulerImpl` - schedules/cancels the periodic
  `WorkManager`
  request. Uses `REPLACE` (not sync's `KEEP`) since a frequency change must take effect on its
  next run rather than waiting out whatever interval was already in force.
- [x] `HealthConnectBiometricsWorker` (`CoroutineWorker`), mirroring `SyncPushWorker`'s shape -
  reads current prefs, self-cancels if auto-pull has been turned off since this run was queued,
  otherwise runs `RunBiometricsBackfillUseCase` and retries on failure.
- [x] `FakeHealthConnectPrefsRepository`/`FakeHealthConnectScheduleRepository` in the shared
  test-support module + unit tests for `RunBiometricsBackfillUseCase`'s candidate selection.
  `HealthConnectBiometricsWorker`/`HealthConnectSchedulerImpl` themselves are thin WorkManager glue
  and aren't separately tested, same as `SyncPushWorker`/`SyncSchedulerImpl` having no tests of
  their own - the logic they delegate to is what's actually tested.
- **Checkpoint**: still no UI - nothing to manually verify yet beyond the unit tests above. The
  worker's actual on-device behavior gets exercised for real once Phase 1d's Settings toggle can
  schedule it.

### Phase 1d - Settings UI

- [ ] New `:feature:healthconnect` module (`regimen.android.feature` convention plugin), mirroring
  `:feature:account`'s precedent of a pushed settings sub-page getting its own module.
- [ ] Add `getGrantedPermissions(): Set<String>` to `HealthConnectRepository`/
  `HealthConnectRepositoryImpl`
  (the impl already computes this internally for `getConnectionState()` - just needs exposing).
- [ ] `GetHealthConnectStatusUseCase` / `SetHealthConnectPrefsUseCase` in `:core:domain`, wiring
  together everything from 1b/1c for the ViewModel to call. No permission-*requesting* use case -
  per Phase 1b, that launch only happens from the Composable below.
  - `GetHealthConnectStatusUseCase` also exposes whether an *optional* permission has become
    available but isn't granted yet (`requiredPermissions() - getGrantedPermissions()` non-empty
    while `ACTIVE`) - happens when a user granted the core permissions before Health Connect's own
    app updated to add background-read support. Not part of `HealthConnectConnectionState` itself
    (still `ACTIVE`, not blocking) - a separate flag the status widget surfaces as a small
    secondary "Background access available" affordance, with an action that re-launches the same
    permission-request launcher with the current `requiredPermissions()` set (idempotent for
    already-granted permissions, so safe to just re-request the full set rather than diffing).
- [ ] `HealthConnectSettingsViewModel`: status widget state (connection state, detected source,
  last pull time, the optional-permission-available flag above) + current prefs; exposes the
  required Health Connect permission set for the screen's launcher to request; handles
  toggle/frequency/backfill-window changes and "Pull now" (state-dependent: nothing to do itself
  in the *Needs permission* case - the screen's launcher handles that - vs. triggering a one-shot
  backfill run when *Active*); re-checks connection state both on the permission launcher's result
  callback and on resume (permission may have been revoked externally via Health Connect's own
  Settings UI, or newly available after a Health Connect update), same pattern
  `AccountViewModel.refreshOnResume` already uses.
- [ ] `HealthConnectSettingsScreen`: shell/shape copied from `feature/account/AccountScreen.kt`
  (`MediumTopAppBar` + back, status block, `HorizontalDivider`-separated sections) - status
  widget, master switch, two `SingleChoiceSegmentedButtonRow` pickers (reusing the same component
  `UnitSystemSelector`/`ThemeModeSelector` use), "Pull now" button with `ButtonProgressIndicator`
  busy state. Owns the
  `rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract())`
  call - the actual permission-request launch, same shape as `RegimenApp.kt`'s existing
  `POST_NOTIFICATIONS` request - triggered by "Pull now" when connection state is
  *Needs permission*, with the result callback telling the ViewModel to re-check status.
- [ ] `HealthConnectNavigation.kt`: `NavGraphBuilder.healthConnectGraph()` extension, per module
  convention.
- [ ] `feature/settings/SettingsScreen.kt`: new `NavRow` entry ("Health Connect") in the
  library/data section, alongside the existing Exercise Library / Account rows.
- [ ] `:app`'s `RegimenNavHost.kt`: wire the new destination, update the ASCII navigation-map
  comment (flip `[ ]` → `[✓]`).
- [ ] `:feature:healthconnect` ViewModel JVM unit tests. All new strings in
  `res/values/strings.xml`.
- **Checkpoint**: first fully manual walk-through through the real app UI - open Settings → Health
  Connect, grant permission, tap "Pull now", watch the status widget update.

### Phase 1e - surfacing in Workout Summary / History

- [ ] Workout Summary / History: display the pulled `WorkoutBiometrics` summary (avg/max BPM,
  calories) when present, reusing `:core:designsystem`'s `chart/LineChart.kt`/`Sparkline` for the
  heart-rate series.
- [ ] New strings for the above.
- **Checkpoint**: the full feature loop end-to-end - finish a real (or Toolbox-seeded) workout, run
  a pull, see biometrics show up on Workout Summary/History. This is "done" for phase 1.

## Status

- [x] Phase 1a done (local storage foundation).
- [x] Phase 1b done (Health Connect read integration), verified end-to-end on-emulator.
- [x] Phase 1c done (prefs + background backfill job).
- [ ] Phase 1d-1e not started.
