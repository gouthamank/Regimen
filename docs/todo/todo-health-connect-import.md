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
and attach a per-session summary (avg/max BPM, calories burned) to the workout in a small satellite
table in `:core:data`. Surfaced on Session Detail as stats plus an on-demand chart, and as a
persisted avg-BPM trend (across many workouts, per routine and combined) in its own Progress
sub-flow - both reusing `:core:designsystem`'s existing `chart/LineChart.kt`/`Sparkline` (see Phase
1e for the split and why).

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
  sourcePackageName?, fetchedAt, isDirty, lastModifiedAt, heartRateSeries?)`, following the same
  shape as `SetEntry`/`CardioEntry` (hangs off `Workout` by foreign key) rather than adding nullable
  columns onto `Workout` itself. `sourcePackageName` comes from the most recent matching record's
  `metadata.dataOrigin.packageName` - also what the Settings status widget displays.
  `heartRateSeries` (added in `MIGRATION_13_14`) is a comma-separated downsampled BPM cache for the
  on-demand chart, populated lazily on first view rather than by the backfill job - see Phase 1e.
- Computed and persisted once at pull time (by the retry job below), not read-through live at
  display time - Workout Summary/History just reads the stored row, same as everything else on
  those screens.

## Sync mechanism (retry/backfill job)

- A periodic `CoroutineWorker`, following the exact same shape `:core:sync`'s `SyncPushWorker`/
  `SyncSchedulerImpl` already use for the Firestore push job: on each run, find `COMPLETE`
  workouts within a fixed 30-day window that don't yet have a `WorkoutBiometrics` row (or have one
  still missing data), query Health Connect for each one's `[startTime, endTime]`, and
  write/update the summary for whatever's found. The window isn't user-configurable - 30 days is
  generous enough that shrinking it would only narrow the net for no real benefit, since
  already-fetched workouts are always excluded from candidates regardless of window size.
- Scheduling is reconciled, not driven by a single stored toggle: the job runs only while all four
  of the following hold - the feature is enabled, background sync is separately enabled (its own
  toggle, off by default even once permission is granted), connection state is `ACTIVE` (both core
  read permissions granted), and the background-read permission is also granted (required for a
  `WorkManager` job's `readRecords()` calls to succeed at all when the app isn't foregrounded -
  without it they throw `SecurityException` every cycle). `ReconcileHealthConnectScheduleUseCase`
  is the single source of truth for this - it's re-run whenever any of those inputs could have
  changed (enabling/disabling the feature, the background-sync toggle, changing frequency, and
  every status refresh), so the schedule can never drift from live permission state, e.g. after
  permission gets revoked via Health Connect's own Settings app while Regimen was backgrounded.
- Turning the background-sync toggle off only stops scheduling - it can't also revoke just the
  background permission. `PermissionController` (decompiled from the actual 1.1.0 jar) exposes
  only `revokeAllPermissions()`, which revokes every permission Regimen holds, not a subset; using
  it here would also strip heart rate/calories, which this one toggle has no business doing. A
  user who wants the background permission itself gone has to revoke it manually via Health
  Connect's own app.
- A manual "Check now" action lets a user force an immediate attempt without waiting for the next
  scheduled run. It's only shown once the screen's active-state content is visible (heart rate and
  calorie permissions granted), runs in the foreground, and so works regardless of whether
  background-read permission has been granted - unlike the periodic job. It enqueues a one-shot run
  of the same backfill sweep the periodic job does (global, not scoped to one workout); busy state
  shown via `ButtonProgressIndicator`, and the status block's last-checked timestamp/detected-source
  line update on completion.

## Settings UI

A **new dedicated sub-page**, launched from Settings (not an inline toggle), following
`feature/account/AccountScreen.kt`'s existing shell/shape (`MediumTopAppBar` + back nav, a status
block, sections divided by `HorizontalDivider`, a manual action button alongside the status row).

Three independent things, deliberately kept separate rather than conflated under one boolean:
opting in to the feature at all, Android permission being granted, and the periodic job actually
being scheduled (see "Sync mechanism" above for the third). The screen surfaces this as four
mutually exclusive states, top to bottom:

- **Switch row** ("Enable Health Connect") - always at the top. A plain feature opt-in, unrelated
  to permission or scheduling. Disabled only when Health Connect isn't installed or the OS/provider
  is incompatible (`HealthConnectConnectionState.UNAVAILABLE`) - **not** disabled merely for
  missing permission, since granting permission is a separate, later step.
- **Unavailable**: an `EmptyState` (`:core:designsystem`) explains Health Connect isn't installed
  or supported.
- **Switch off** (and available): an `EmptyState` explains what the feature does and invites
  opt-in.
- **Switch on, permission not (fully) granted** (`connectionState != ACTIVE`, i.e. heart rate and
  calories not both granted - background-only-granted still counts as not-yet-granted for this
  purpose): an `EmptyState` explains what permission is needed and why, with a "Grant permission"
  action that launches the permission request for `HealthConnectRepository.coreReadPermissions()`
  only. Same message/action regardless of *which* permission(s) specifically are missing - no
  per-combination copy.
- **Switch on, `ACTIVE`**: the active content block - a status readout (title, detected source
  app label or "No data yet", last-checked timestamp, a "Check now" button), then a
  divider-separated **"Background sync"** section that shows exactly one of two things depending
  on whether the background-read permission is granted:
  - **Not granted**: an `EmptyState` card explaining background checking can't function at all
    without it, with a "Turn on" action (requests `requiredPermissions() - coreReadPermissions()`,
    i.e. just the background permission) - the "Check every" picker doesn't render at all in this
    state, not merely disabled.
  - **Granted**: a third, independent toggle - **"Enable background sync"** - plus the **"Check
    every"** picker (retry frequency; `SingleChoiceSegmentedButtonRow`, same component
    `UnitSystemSelector`/`ThemeModeSelector` already use): **1 hour / 6 hours / Daily**, defaulting
    to **6 hours**. Granting the permission doesn't imply this toggle is on - it defaults off, same
    explicit-opt-in philosophy as the top-level switch. The picker renders but is disabled while
    the toggle is off, since it configures a job that isn't running. Turning the toggle off stops
    the periodic job but leaves the Android permission grant itself untouched - see "Sync
    mechanism" for why a partial revoke isn't possible. No 15-minute frequency option - Health
    Connect data usually isn't available that fast anyway, and a tighter floor would just encourage
    a battery-hungry setting for no real benefit.
- Three settings now (feature enabled, background sync enabled, retry frequency) persisted via
  `:core:healthconnect`'s own DataStore file, kept separate from `:core:data`'s
  `PreferencesRepositoryImpl` since these must never enter that repository's Firestore push scope.
- The core-permission request and the background-permission request are never launched together
  in one call. If the background permission has previously been denied enough times that Android
  marks it `USER_FIXED`, Health Connect's permission activity aborts the *entire* request
  immediately - including permissions that were never denied - rather than prompting for whatever
  isn't user-fixed. Requesting `coreReadPermissions()` and the background permission separately
  means a user-fixed background permission can never block granting heart rate/calories.
- A `USER_FIXED` permission also means the request activity shows no system UI at all - from the
  user's perspective, tapping "Grant permission"/"Turn on" does nothing. The screen detects this
  by comparing what was requested against what the launcher's result actually granted (the
  `PermissionController` contract's result type is the granted-permission `Set<String>`) and shows
  a Snackbar directing the user to the device's app-settings screen
  (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) instead of leaving the tap looking like a no-op.

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

The periodic job's `readRecords()` calls require
`android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` to succeed at all while Regimen is
backgrounded (which is most of the time for a periodic job) - without it, calls throw
`SecurityException`, not degraded results. `ReconcileHealthConnectScheduleUseCase` treats this as
a hard prerequisite: the job is only scheduled while the feature is enabled, connection state is
`ACTIVE`, and this permission is also granted - re-evaluated on every prefs change and status
refresh, so it self-corrects if permission is revoked later. "Check now" (foregrounded) always
works regardless of this permission, since it doesn't run in a background execution context.

- [x] `HealthConnectPrefs` domain model (`healthConnectEnabled: Boolean` default `false`,
  `backgroundSyncEnabled: Boolean` default `false`, `retryFrequency:
  HealthConnectRetryFrequency` default `SIX_HOURS`) in `:core:domain`'s `HealthConnect.kt`; the
  `HealthConnectRetryFrequency` enum (`ONE_HOUR`/`SIX_HOURS`/`DAILY`) lives in `Enums.kt` alongside
  `MaxWorkoutDuration`/`HistoryRange`. The backfill window is not a stored preference - it's a
  fixed 30-day constant in `HealthConnectUseCases.kt`, applying uniformly to both the periodic job
  and "Check now".
- [x] `HealthConnectPrefsRepository` interface (`:core:domain`) + `HealthConnectPrefsRepositoryImpl`
  (`:core:healthconnect`, its own DataStore file `health_connect_settings` - deliberately separate
  from `:core:data`'s `PreferencesRepositoryImpl`, which is pushed to Firestore wholesale; these
  must never end up in that scope).
- [x] `RunBiometricsBackfillUseCase`: finds `COMPLETE` workouts in the fixed 30-day window missing
  a `WorkoutBiometrics` row, calls `PullBiometricsForWorkoutUseCase` for each. Composed directly
  from `WorkoutRepository.observeCompletedBetween(...)` (completed ids) filtered by
  `WorkoutBiometricsRepository.get(id) == null` (missing ones) - per Phase 1a's open design note,
  `getCompletedWorkoutIdsMissingBiometrics` was removed from `WorkoutBiometricsRepository`/its DAO
  entirely rather than kept as a dedicated cross-table query, since this composed form is
  exercised by ordinary fakes with no special-case testing hook needed.
- [x] `HealthConnectScheduleRepository` interface (`:core:domain`) + `HealthConnectSchedulerImpl`
  (`:core:healthconnect`) mirroring `SyncSchedulerImpl` - schedules/cancels the periodic
  `WorkManager`
  request. Uses `REPLACE` (not sync's `KEEP`) since a frequency change must take effect on its
  next run rather than waiting out whatever interval was already in force.
- [x] `ReconcileHealthConnectScheduleUseCase` (`:core:domain`): the single source of truth for
  whether the job should be running - computed from `healthConnectEnabled &&
  connectionState == ACTIVE && getGrantedPermissions().containsAll(requiredPermissions())`, called
  by `SetHealthConnectPrefsUseCase` after every prefs change and by
  `HealthConnectSettingsViewModel.refreshStatus()` after every status fetch (init, resume, and
  after the permission launcher returns).
- [x] `HealthConnectBiometricsWorker` (`CoroutineWorker`), mirroring `SyncPushWorker`'s shape -
  reads current prefs, self-cancels if the feature has been turned off since this run was queued,
  otherwise runs `RunBiometricsBackfillUseCase`. A `SecurityException` (permission revoked since
  this job was scheduled) self-cancels the recurring job immediately rather than retrying forever
  with no way to succeed until the app itself reconciles the schedule; any other failure retries
  normally.
- [x] `FakeHealthConnectPrefsRepository`/`FakeHealthConnectScheduleRepository` in the shared
  test-support module + unit tests for `RunBiometricsBackfillUseCase`'s candidate selection and
  `ReconcileHealthConnectScheduleUseCase`'s permission-revocation self-correction.
  `HealthConnectBiometricsWorker`/`HealthConnectSchedulerImpl` themselves are thin WorkManager glue
  and aren't separately tested, same as `SyncPushWorker`/`SyncSchedulerImpl` having no tests of
  their own - the logic they delegate to is what's actually tested.
- **Checkpoint**: still no UI - nothing to manually verify yet beyond the unit tests above. The
  worker's actual on-device behavior gets exercised for real once Phase 1d's Settings toggle can
  schedule it.

### Phase 1d - Settings UI

- [x] New `:feature:healthconnect` module (`regimen.android.feature` convention plugin), mirroring
  `:feature:account`'s precedent of a pushed settings sub-page getting its own module.
- [x] Added `getGrantedPermissions(): Set<String>` and `resolveAppLabel(packageName): String?` to
  `HealthConnectRepository`/`HealthConnectRepositoryImpl` (the label resolution is a plain
  `PackageManager` lookup, used for the "Data from: ..." attribution - falls back to the raw
  package name if the source app has since been uninstalled and its label can't be resolved).
- [x] `GetHealthConnectStatusUseCase` / `SetHealthConnectPrefsUseCase` / a plain-passthrough
  `ObserveHealthConnectPrefsUseCase` in `:core:domain`. No permission-*requesting* use case - that
  launch only happens from the Composable below. `GetHealthConnectStatusUseCase` bundles
  everything the status widget needs into one `HealthConnectStatus`, including
  `requiredPermissions` itself - so the ViewModel/Composable never need to depend on
  `HealthConnectRepository` directly, matching this codebase's ViewModels-call-use-cases-only rule.
  - It also exposes whether an *optional* permission has become available but isn't granted yet
    (`requiredPermissions() - getGrantedPermissions()` non-empty while `ACTIVE`) - happens when a
    user granted the core permissions before Health Connect's own app updated to add
    background-read support. Not part of `HealthConnectConnectionState` itself (still `ACTIVE`,
    not blocking) - a separate flag the status widget surfaces as a small secondary "Background
    access available" affordance, with an action that re-launches the same permission-request
    launcher with the current `requiredPermissions()` set (idempotent for already-granted
    permissions, so safe to just re-request the full set rather than diffing).
- [x] `HealthConnectSettingsViewModel`: status widget state (connection state, detected source,
  last-checked time, the background-permission-available flag above) + current prefs; handles
  enabled/frequency changes and "Check now" (only meaningful while `ACTIVE` - the
  permission-required and background-permission-nudge cases are handled entirely by the screen's
  own permission launcher, never routed through the ViewModel); `refreshStatus()` is called on
  init, after the permission launcher's result callback, and on resume (permission may have been
  revoked externally via Health Connect's own Settings UI, or newly available after a Health
  Connect update) - same pattern `AccountViewModel.refreshOnResume` already uses, and also
  re-runs `ReconcileHealthConnectScheduleUseCase` each time.
- [x] `HealthConnectSettingsScreen`: shell/shape copied from `feature/account/AccountScreen.kt`
  (`MediumTopAppBar` + back, status block, `HorizontalDivider`-separated sections) - an
  always-visible switch row at the top ("Enable Health Connect", disabled only when
  `UNAVAILABLE`), then exactly one of: an `EmptyState` explaining why when unavailable, an
  `EmptyState` explaining the feature when the switch is off, an `EmptyState` explaining what
  permission is needed (with a "Grant permission" action) when the switch is on but
  `connectionState != ACTIVE`, or - once both the switch is on and `ACTIVE` - the active block: a
  status readout ("Check now" button with `ButtonProgressIndicator` busy state), then a
  divider-separated "Background sync" section that shows either an `EmptyState` card (background
  permission missing, with a "Turn on" action requesting just that permission) or - once granted -
  an "Enable background sync" toggle (its own opt-in, off by default, independent of the
  permission grant) alongside the "Check every" `SingleChoiceSegmentedButtonRow` picker (same
  pattern `UnitSystemSelector`/`ThemeModeSelector` use, as a screen-local private composable rather
  than a shared one - `HealthConnectRetryFrequency` is specific to this one screen), disabled while
  the toggle is off since the job it configures isn't running. A failed permission request (e.g. a
  `USER_FIXED` permission that silently aborts with no system UI) is detected by diffing the
  launcher's result against what was requested, surfaced via a Snackbar pointing at the device's
  app-settings screen. Owns the
  `rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract())`
  call - the actual permission-request launch, same shape as `RegimenApp.kt`'s existing
  `POST_NOTIFICATIONS` request. A completed pull is never silent - a Snackbar reports the outcome
  (nothing to check / checked N, found nothing / pulled N) every time, driven by
  `RunBiometricsBackfillUseCase` returning a `BiometricsBackfillResult` rather than `Unit`, and
  `HealthConnectSettingsViewModel.pullResultEvents` (a `SharedFlow`) carrying it to the screen.
- [x] `HealthConnectNavigation.kt`: `NavGraphBuilder.healthConnectGraph()` extension, per module
  convention. Matches `AccountNavigation.kt`'s shared-element container-transform treatment - the
  Settings row visually grows into the full screen, with the destination's own
  `enterTransition`/`popExitTransition` suppressed (`EnterTransition.None`/`ExitTransition.None`)
  so it doesn't fight that growth/shrink animation.
- [x] `feature/settings/SettingsScreen.kt`: new `NavRow` entry ("Health Connect") in the
  library/data section, alongside the existing Exercise Library / Account rows, wired with its own
  shared-transition key (`healthConnectFromSettingsTransitionKey`, alongside `:core:common-ui`'s
  existing `accountFromSettingsTransitionKey`).
- [x] `:app`'s `RegimenNavHost.kt`: wired the new destination, updated the ASCII navigation-map
  comment (also fixed a pre-existing omission - `accountGraph` was missing from the "each feature
  module owns its own destinations" list even before this).
- [x] `:feature:healthconnect` ViewModel JVM unit tests. All new strings in
  `res/values/strings.xml`.
- [ ] **Checkpoint**: first fully manual walk-through through the real app UI - open Settings →
  Health Connect, grant permission, tap "Check now", watch the status widget update. Not yet done -
  needs a build/run pass, not something a JVM unit test covers.

### Phase 1e - surfacing in Session Detail and a Progress trends sub-flow

`WorkoutBiometrics` only ever stores `avgBpm`/`maxBpm`/`activeCaloriesKcal` - no raw heart-rate
sample series (Phase 1a never persisted one). A single workout's summary is therefore three
numbers, not a chart; a chart only earns its complexity as a trend *across* workouts. Phase 1e
splits accordingly into two independent pieces, both gated on
`HealthConnectPrefs.healthConnectEnabled`

- nothing in either renders, not even entry points, while the feature is switched off:

**Session Detail (`:feature:history`)** - a per-workout card, hidden entirely unless Health Connect
is enabled:

- [x] Persisted avg/max BPM + calories stats, always visible when a `WorkoutBiometrics` row exists
  - a cheap reactive Room read (`WorkoutBiometricsRepository.observe(workoutId)`), no different from
    any other stat on the screen.
- [x] A separate, on-demand "Show heart-rate chart" button. `GetHeartRateSeriesForWorkoutUseCase`
  checks `WorkoutBiometrics.heartRateSeries` (a cache column added in `MIGRATION_13_14`) first; on a
  miss it queries Health Connect's raw `HeartRateRecord` samples live for that workout's
  `[startTime, endTime]`, downsamples to a fixed ~60-point chronological average
  (`bucketAverages`), and caches the result onto the row - but only if a row already exists (never
  creates a bare one just for the chart, which would skew `GetHealthConnectStatusUseCase`'s "last
  pulled" reads). Renders `LineChart`, or a Snackbar ("No heart-rate data found for this workout.")
  on a miss with nothing live either.
- [x] Unit tests for the bucketing/caching use case (via fakes) and the migration; ViewModel tests
  for the enable/disable gating and the found/not-found chart paths.

**Progress - "Heart Rate Trends" sub-flow (`:feature:progress`)** - a dedicated list + detail flow
using the *persisted* avg BPM across many workouts, reached via its own link row on the Progress
tab (hidden when the feature is disabled, same as the row above):

- [x] A list screen (`HeartRateTrendsScreen`) showing a synthetic "All routines combined" row plus
  one row per routine that has at least one completed workout, each with a `Sparkline` preview of
  its full, unfiltered avg-BPM history (`GetHeartRateTrendRowsUseCase`) - mirrors
  `:feature:measurements`' list-row-with-sparkline convention. A freeform (no-routine) workout only
  ever contributes to the combined row.
- [x] Tapping a row opens a detail screen (`HeartRateTrendDetailScreen`) with a
  `HistoryRangeSelector`,
  a range-filtered `LineChart` trend, and the individual contributing workouts below it (date,
  duration, avg BPM) via `GetHeartRateTrendDetailUseCase`. Only workouts with both a pulled avg BPM
  and a known end time are included.
- [x] New strings for both screens; navigation wired into `progressGraph` with container-transform
  shared-element keys (`heartRateTrendsFromProgressTransitionKey`,
  `heartRateTrendRowTransitionKey`), matching `AccountNavigation.kt`'s single-entry-point pattern.
- [x] Unit test for `HeartRateTrendsViewModel`. `HeartRateTrendDetailViewModel` has no test -
  `:feature:progress` has no `:core:testing-android` dependency for `SavedStateHandle.toRoute`
  tests, the same gap `MeasurementDetailViewModel` already has for the identical reason.
- **Checkpoint**: the full feature loop end-to-end - finish a real (or Toolbox-seeded) workout, run
  a pull, see the stats card and on-demand chart on Session Detail, and the trend show up under
  Progress's Heart Rate Trends. Not yet done - needs a build/run pass, not something a JVM unit
  test covers. This is "done" for phase 1 once verified.

## Status

- [x] Phase 1a done (local storage foundation).
- [x] Phase 1b done (Health Connect read integration), verified end-to-end on-emulator.
- [x] Phase 1c done (prefs + background backfill job).
- [~] Phase 1d done except its manual on-device checkpoint (Settings UI) - deliberately deferred to
  be verified together with Phase 1e below, rather than in isolation on the emulator.
- [~] Phase 1e implemented (Session Detail stats/chart, Progress Heart Rate Trends sub-flow); its
  on-device checkpoint (together with Phase 1d's) is not yet done.
