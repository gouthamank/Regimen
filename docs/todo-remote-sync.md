# Remote sync (future - not started)

Regimen is local-only today (see `docs/architecture.md`); this doc tracks groundwork for
eventual multi-device sync, kept separate until picked up. Not scheduled - revisit before
starting.

**Single-writer model: exactly one device per account is ever allowed to push automatically.**
There is no merge, no per-document conflict resolution, and no automatic pull, anywhere in this
design - every operation that moves data is either the one designated **primary** device's
ongoing incremental push, or an explicit, user-confirmed, one-directional full replace (pull the
cloud down, destroying local state; or claim primary, destroying the cloud's state). This is a
deliberate simplification over an earlier, considerably more complex draft that tried to support
automatic multi-device merging (per-row change-tracking across every device, last-write-wins
conflict resolution, an always-on account-mismatch guard) - that version kept surfacing genuine
correctness gaps precisely because "silently reconcile two devices' data automatically" is a hard
problem. Restricting to one automatic writer at a time, with every other device requiring an
explicit destructive confirmation to participate at all, sidesteps that whole problem class by
construction rather than solving it.

- **Phase 0** is a one-shot, irreversible local schema migration - isolating it means if
  something's wrong, it surfaces from real usage before any sync code ever touches that data.
- **Phase 1** is the primary device's ongoing incremental sync (push, plus the delete-propagation
  needed to keep it correct) - fully useful and safe on its own for single-device use, no other
  device involved at all.
- **Phase 2** adds multi-device support: the explicit "Pull cloud data" / "Claim primary" actions
  a secondary device uses to participate. Purely additive scope, not a risk-mitigation stage -
  Phase 1 alone is already a complete, correct single-device backup. Becoming primary in the first
  place is silent and automatic at sign-in when no one else has claimed it yet - this phase's
  actual UI only ever appears once a *second* device is genuinely in the picture.

## Status legend

- `[x]` done and verified
- `[~]` in progress
- `[ ]` not started

---

## Phase 0: UUID primary-key migration

Prerequisite for *any* real backend sync (custom server or Firestore) - autoincrement `Long`
IDs collide across devices, since two offline devices can independently generate the same next
ID. Worth doing before a backend exists, since it's a local-only schema change either way.

- [x] Swap every entity's `@PrimaryKey`/FK column from `Long` to a client-generated `String`
  UUID (`java.util.UUID.randomUUID().toString()`), generated at creation time rather than
  relying on Room's autoincrement-and-read-back.
    - Entities: `Exercise`, `Routine`, `RoutineExercise`, `Workout`, `WorkoutExercise`,
      `SetEntry`, `CardioEntry`, `MeasurementType`, `BodyMetric`.
- [x] Update `:core:domain` models and repository interfaces to match (`id: String` instead of
  `id: Long`).
- [x] Update nav route args (`:app`'s `Destinations`, each feature's route types) and any
  ViewModel taking an ID param.
- [x] Write the Room `Migration` for existing installs:
    - Create new UUID-keyed tables.
    - Walk existing rows in FK dependency order (parents first: `Exercise`/`Routine`/
      `MeasurementType`, then `RoutineExercise`/`Workout`/`BodyMetric`, then `WorkoutExercise`,
      then `SetEntry`/`CardioEntry`).
    - Generate a UUID per old row, maintain an old-`Long`-ID → new-UUID map per entity type, and
      remap every FK column using that map before dropping the old tables. `Workout.routineId`
      is a nullable `SET_NULL` FK - remap only non-null values, leave nulls as null.
  - Test against a real pre-migration DB snapshot, not just a fresh install - remap-order bugs
      silently corrupt relationships rather than crashing.
      - The existing `MigrationTest` convention (`:core:data` `androidTest`) uses
        `MigrationTestHelper` against committed schema JSONs in `core/data/schemas/` - confirm the
        current version's schema JSON is committed before relying on this, since `MIGRATION_4_5` has
        no test today precisely because `4.json` was never committed.
- [x] Bump `Database version` past the current 8 and note it in `docs/architecture.md`'s data
  model section once done.
- [x] Exception for built-in (seed) data: `Exercise` rows are seeded via `BuiltInData.kt` with no
  explicit ID today, relying on Room's autoincrement assigning the same sequential IDs on every
  fresh install (consistent only because every install seeds the identical built-in list in the
  same order). If seeding also switches to a random UUID per row, the same built-in exercise
  (e.g. "Bench Press") gets a *different* ID on every install - and since Phase 1/2 still sync
  `WorkoutExercise` rows that FK-reference built-in exercises (even though built-ins themselves
  aren't synced), a pulled-down workout's exercise reference would dangle or resolve to the
  wrong exercise on another device. Built-ins need a **stable, deterministic seed ID** (fixed in
  `BuiltInData.kt`, identical across every install), not `UUID.randomUUID()` - this is the one
  exception to "random UUID at creation time."

**Scope estimate:** multi-day, wide-but-mechanical (touches every module in the ID path) but
low design risk - the only genuinely delicate part is the migration's FK remapping.

---

## Phase 1: primary device sync (push + delete propagation)

The "no backend to build or run" path to cloud backup, evaluated as a much lower-effort
alternative to a custom server + hand-rolled sync protocol. Assumes Phase 0 (UUID keys) is done
first, since Firestore document IDs need the same global-uniqueness property. Describes the one
device designated **primary** for a given account - the only device that ever writes to
Firestore automatically. A device only becomes primary via Phase 2's explicit "Claim primary"
action; until then, this phase doesn't apply to it at all.

- [x] One-time Firebase console setup (manual, done once before any code lands):
    1. Create/link a Firebase project at the Firebase console.
    2. Register the Android app with `applicationId` `dev.gouthaman.regimen` plus **both** the
       release-signing SHA-1/SHA-256 fingerprint (from `release.jks`, wired via `signingConfigs`
       in `app/build.gradle.kts`) **and** the debug keystore's (`~/.android/debug.keystore`,
       alias `androiddebugkey`) - Google Sign-In needs both registered, or sign-in fails on every
       debug build during development. Both fingerprints go on the same app registration -
       debug and release builds share a single Firebase project (decided below), not split
       across two. Download the resulting `google-services.json` into `:app/`.
    3. Enable **Authentication → Google** as a sign-in provider.
    4. Create the Firestore database in **Native mode**, confirm it's on the Spark (free) tier.
    5. Write and deploy security rules scoping each user to only `users/{uid}/**` - Firestore
       denies all reads/writes by default until rules explicitly allow them, so this isn't
       optional hardening, it's required for the app to function at all. Commit the rules to
       the repo (e.g. `firestore.rules` at root).

    - Debug and release builds share the single Firebase project - no dev/prod split, despite
      there being a single `applicationId` and no build-type/flavor split today
      (`app/build.gradle.kts`) to begin with. Test data from debug builds mixes with real workout
      data and shares the same quota, but that's an acceptable tradeoff at personal-use scale
      against the extra setup (second project, second `google-services.json`, separate
      `applicationId` suffix, separate OAuth client) a dev-project split would require.
- [x] Write and host a short privacy policy - live at **https://regimen.gouthaman.dev** (personal
  domain, not a Play Store listing concern - no Play distribution is planned). Needed for a reason
  independent of Play: Firebase Auth's Google Sign-In is backed by a Google Cloud OAuth client,
  and while its consent screen sits in **"Testing" status**, refresh tokens expire after
  **7 days** - the periodic/unattended sync job would silently stop working weekly until manual
  re-auth. Moving the consent screen to **"Production" status** avoids that expiry and doesn't
  require Play distribution or a Google review (non-sensitive scopes only: email/profile) - but
  "External" user type (required; "Internal" is Workspace-org-only, not available for a personal
  Gmail account) requires a privacy policy URL in the consent screen config before Google allows
  publishing to Production. Confirmed via the GCP console's Data Access screen that no
  verification review is needed either way (only sensitive/restricted scopes trigger that, and
  this app requests neither) - the privacy policy requirement is solely the Testing→Production
  gate, unaffected by that. Scoped to what's actually collected: Google account email (auth
  only), workout/routine/measurement data synced to Firestore, no third-party sharing, no
  ads/analytics. **Still pending**: flipping the consent screen's publishing status itself in the
  GCP console (Testing → Production) and pasting this URL into its Privacy Policy field.
- [x] New Gradle dependencies: Firebase Auth + Firestore (via the Firebase BoM, applied as `api`
  in `:core:sync` so its version constraints propagate to `:app`'s transitive resolution - the
  BoM dropped the separate `-ktx` artifacts as of v34, so these are the plain `firebase-auth`/
  `firebase-firestore` coordinates, not `-ktx`), the `google-services` Gradle plugin, and
  WorkManager (for the periodic sync job) - plus the `google-services.json` from the Firebase
  console. For "Sign in with Google" specifically, uses **Credential Manager**
  (`androidx.credentials:credentials` + `androidx.credentials:credentials-play-services-auth` +
  `com.google.android.libraries.identity.googleid:googleid`, plus `play-services-auth` for the
  Play-Services-availability check and `kotlinx-coroutines-play-services` for `Task.await()`),
  not the legacy `GoogleSignInClient`/Play Services Auth API, which Google has deprecated.
- [x] Graceful degradation without Google Play Services: this app has zero GMS dependency
  otherwise (pure AndroidX, `minSdk 26`); Credential Manager's Google ID option requires Play
  Services to be present/up to date. Since sign-in must stay "optional, skippable, fully usable
  local-only" (decided below), absence of Play Services shows as a normal disabled sign-in
  option, not an unhandled exception - `AuthRepositoryImpl.isSignInAvailable` checks
  `GoogleApiAvailability.isGooglePlayServicesAvailable(context) == SUCCESS`, and `AccountScreen`
  disables the sign-in button (and shows an explanatory message) whenever it's `false`.
- [x] Add the `INTERNET` permission to `AndroidManifest.xml` - the app previously declared none
  (`VIBRATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` only).
  This is the point where "local-only" stopped being literally true even for users who never
  sign in.
- [x] New **`:core:sync`** module for auth/sync, not folded into `:core:data` - the surface area
  (Firebase Auth + Firestore clients, the primary device's WorkManager push job, tombstone
  tracking, the `syncConfig` primary/secondary claim logic) is substantial and shared across
  `:feature:account`, `:feature:settings`, and the sync worker alike, mirroring `:core:data`'s role
  for the Room-backed repositories rather than mixing local-persistence DI with cloud-sync DI in
  one module. Follows the existing `di/<Name>Module.kt` convention for its Hilt bindings.
- [x] Sign-in entry points: a two-tier auth/sync UI rather than a single Settings section. Both
  pieces (Settings' summary row and the dedicated `:feature:account` screen) are built. Onboarding's
  3rd page is purely informational - it names cloud sign-in/backup as a Settings feature to check
  out later, with no sign-in action of its own (see below):
    - **Settings' "Account" summary row** (built): the signed-in account's email, or "Signed out",
      navigating to the dedicated Account screen via a container-transform shared-element
      transition (`accountFromSettingsTransitionKey` in `:core:common-ui`'s
      `SharedTransitionKeys.kt`), mirroring the existing Settings → Exercise Library row
      exactly. `SettingsViewModel` combines its existing preferences `StateFlow` with a second one
      from `ObserveAccountStatusUseCase` - the same `:core:domain` use case the Account screen's
      own ViewModel reads, rather than sharing a ViewModel instance across module boundaries. A
      last-successful-sync timestamp and "Sync now" action are not shown yet - there's no sync job
      to report on until the sync-trigger item below lands.
    - **Dedicated Account screen** (built, `:feature:account`, its own Gradle module per the
      one-module-per-screen convention, also sharing that same transition key on its root
      container): sign-in with Google (disabled with an explanatory caption when Google Play
      Services isn't available), or - once signed in - the account's name/email plus exactly two
      destructive actions, each behind the shared `ConfirmDialog` (`:core:designsystem`'s
      `dialog/ConfirmDialog.kt`, `destructive = true`) - **not** a "delete account" concept, since
      there's no separate Regimen account to delete, only a signed-in Google identity and a
      Firestore backup:
        - **Sign out** - stops syncing, keeps local data, keeps the cloud backup as-is.
      - **Delete cloud data** - deletes the signed-in user's known Firestore subcollections
        (`workouts`, `routines`, `exercises`, `measurementTypes`, `bodyMetrics`, `syncConfig`) and
        `preferences` document under `users/{uid}`, then the user document itself and the
        now-pointless Firebase Auth user record. The subcollection list is hardcoded rather than
        derived from the entity-mapping/sync-device code, since the Firestore client SDK can't
        enumerate a document's subcollections at runtime, and Firestore doesn't cascade-delete
        subcollections when a parent document is deleted - so every subcollection any part of the
        sync feature writes to (including `syncConfig`, written by the primary-device claim, not
        by a sync job) must be listed here explicitly or it's orphaned. Deleting that Firebase Auth
        record ends the local Firebase session as a side effect (`FirebaseAuth`'s `currentUser`
        goes null, `AuthRepositoryImpl`'s auth-state listener picks it up, the Account screen
        reflects signed-out immediately) - so this action does sign the user out of **Regimen**,
        even though it does **not** touch the Google OAuth grant itself, which the user can only
        revoke via their own Google Account settings. The row description and confirmation
        dialog's copy both say so explicitly, in plain terms (e.g. "cloud backup," never
        "Firestore," which isn't a concept a user should need to know) - both are user-facing
        copy per the string convention below.
        Each button (Sign in / Sign out / Delete cloud data) shows an inline spinner in place of its
        own label while its specific action is in flight (`AccountViewModel`'s `busyAction:
      AccountAction?`, one of `SIGN_IN`/`SIGN_OUT`/`DELETE_CLOUD_DATA`) and all three buttons
        disable while any one of them is busy - there's no separate standalone loading indicator.
        Failures are classified into a small `AuthErrorReason` enum (`NO_CREDENTIALS`/`CANCELLED`/
        `NETWORK`/`UNKNOWN`) via a domain-level `AuthException` wrapper (`:core:domain`'s
        `model/AuthError.kt`) rather than displaying a raw platform exception message - e.g.
        Credential Manager's `NoCredentialException` (thrown when no Google account exists on the
        device/emulator) maps to a specific, actionable string ("No Google account found on this
        device. Add one in your device's account settings, then try again.") instead of leaking
        "No credentials available" verbatim. A shared `@Composable AuthErrorReason.text()`
        (`:core:common-ui`'s `AuthErrorText.kt`, backed by its own `strings.xml` entries) resolves
        the reason to copy at render time, matching the `UnitLabelText.text()` pattern - both
        `AccountScreen` calls this formatter; onboarding's informational page below has no
        sign-in action and so no error states of its own.
        `AccountViewModelTest` (`:feature:account`, using a `FakeAuthRepository` from
        `:core:testing`) covers sign-in success/failure (both typed and untyped exceptions) and
        sign-out/delete-cloud-data dispatch, per the ViewModel testing tier in `docs/testing.md`.
    - `HomeViewModel` already consumes `ObserveAccountStatusUseCase` too, independent of this
      sync work: the Home tab's greeting is personalized with the signed-in account's first name
      (parsed from `AuthAccount.displayName`) when signed in, falling back to the existing unnamed
      greeting otherwise - see `docs/architecture.md`'s Home entry. Proof that account data is
      already usable by non-sync features once Phase 1's sign-in plumbing exists.
  - **Onboarding's 3rd page** (built, `:feature:onboarding`, `PAGE_COUNT` bumped from 2 to 3): a
    title + subtitle only, no sign-in button, no account status, no error states - it exists
    purely to tell the user that cloud backup/sync is available later from Settings. Always
    skippable, like every onboarding page. `OnboardingViewModel` carries no auth dependencies at
    all - just its existing preferences use cases plus `finish()`; sign-in and the primary-device
    claim it triggers live exclusively in `:feature:account`'s `AccountViewModel`.
    `:feature:onboarding` has no ViewModel test file (per `docs/testing.md`'s "skipped entirely"
    list) since there's no branching logic left to cover, only preference pass-through.
    - Per `CLAUDE.md`'s string/formatting conventions: all new user-facing copy (Settings Account
      summary, the dedicated Account screen, confirmation modal copy, sign-in error states) goes in
      `strings.xml` via `stringResource()`, not
      hardcoded. The "last synced at" instant must be exposed as a raw timestamp in UI state and
      formatted by the Composable at render time (matching the `SessionFormat`/`MeasurementFormat`
      pattern) - the ViewModel must not pre-format it into a display string itself.
- [x] Map Room entities to Firestore collections, scoped per-user, using **subcollections nested
  to match the Room FK chain exactly** (not embedded arrays, and not flattened siblings). Built as
  a pure mapping layer in `:core:sync`'s `firestore/` package (`ExerciseMapping.kt`/
  `RoutineMapping.kt`/`WorkoutMapping.kt`/`MeasurementMapping.kt`/`PreferencesMapping.kt`, one
  `<Entity>Dto` + `toDto()` extension per entity, mirroring the entity exactly minus `id`
  (implied by the document's own path) and `isDirty` (local-only) - covered by
  `FirestoreMappingTest`. `:core:sync` gained a new dependency on `:core:data` for this, since
  `isDirty`/`lastModifiedAt` live only on Room entities, not domain models. This is the mapping
  layer only - nothing calls it yet, that's the separate, still-unbuilt push job below:
  ```
  users/{uid}/
    exercises/{exerciseId}                              (isCustom == true only)
    measurementTypes/{typeId}                            (isBuiltIn == false only)
    bodyMetrics/{metricId}
    routines/{routineId}
      routineExercises/{id}
    workouts/{workoutId}                                  (workoutStatus == COMPLETE only)
      workoutExercises/{weId}
        setEntries/{id}          -or-  cardioEntries/{id}
    preferences                                           (single document, not a collection)
  ```
  `SetEntry`/`CardioEntry` nest under `WorkoutExercise` (not directly under `Workout`), since
  that's the actual Room FK chain (`Workout` → `WorkoutExercise` → `SetEntry`/`CardioEntry`) -
  subcollection depth mirrors relational depth exactly, so no extra field is needed to
  reconstruct the parent link. Chosen over embedding because it sidesteps Firestore's 1 MiB
  document-size limit entirely (no realistic ceiling on workout size) and lets the incremental
  push touch only the specific rows that are actually dirty (a single edited set doesn't require
  re-serializing and rewriting its entire parent workout as one blob). Costs more reads/writes per
  sync than one document per workout, but quota isn't the binding constraint at this app's data
  volume (numeric/short-string fields only, no attachments). `Exercise`/`MeasurementType`/
  `BodyMetric` sit flat at the top level (no natural parent to nest under, in sync scope).
- [x] Sync scope: user-authored Room data - `Exercise` rows where `isCustom = true` (built-ins
  ship with the APK and shouldn't be uploaded at all, excluded via `ExerciseDao.getDirty`'s
  `isCustom = 1` filter), custom `MeasurementType`s (same idea, `MeasurementDao.getDirtyTypes`'s
  `isBuiltIn = 0` filter), `Routine`/`Workout`/etc. (no built-in/custom distinction needed - every
  row is user-authored, so their dirty queries filter on nothing but `isDirty = 1`) - **plus app
  preferences** (units, theme, rest-timer defaults): sign-in carries both over to a new device,
  not just workout/routine data.
    - Preferences live in DataStore, not Room, and aren't in Phase 0's entity list (no FK
      references, no UUID needed) - they got their own sync path rather than riding the
      Room-entity mechanism: `PreferencesRepositoryImpl` stamps its own `is_dirty`/
      `last_modified_at` DataStore keys (mirroring Room's `isDirty`/`lastModifiedAt` columns) on
      every write, and `getDirtyPreferences()`/`clearPreferencesDirty()` give `SyncPushRunner`'s
      `pushPreferences()` the same read-write-clear shape every Room entity type gets, writing to
      a single `users/{uid}/preferences/current` document (a one-row "collection," the standard
      Firestore idiom for "exactly one document," not many) rather than a genuine multi-row
      collection.
- [x] Only push completed workouts: `Workout.workoutStatus` (`IN_PROGRESS`/`IN_REST_TIME`/
  `PAUSED`/`EDITING`/`COMPLETE`) means a session can be mid-flight, backed by the Active Workout
  foreground service and its live pause/resume state - anything short of `COMPLETE` is excluded
  from the sync job, since even in a push-only phase there's no reason to expose a live
  in-progress session's internal state remotely. Every dirty-row query the push job reads from
  (`WorkoutDao.getDirtyWorkouts`/`getDirtyWorkoutExercises`/`getDirtySetEntries`/
  `getDirtyCardioEntries`) filters `workoutStatus = 'COMPLETE'` at the query level (not a
  post-filter in `SyncPushRunner`), so a `WorkoutExercise`/`SetEntry`/`CardioEntry` under an
  in-progress or paused workout is invisible to the push job even if its own `isDirty` flag is
  set - it only becomes eligible once its parent workout finishes. Covered by
  `WorkoutDaoTest`'s `getDirtySetEntries_onlyIncludesRowsUnderACompleteWorkout`/
  `getDirtyWorkoutExercises_excludesRowsUnderAnEditingWorkout`/
  `getDirtyCardioEntries_onlyIncludesRowsUnderACompleteWorkout`.
- [x] Change-tracking mechanism: the schema half is done and verified - **`isDirty: Boolean`**
  and **`lastModifiedAt: Long`** columns exist on every synced Room entity (`Routine`,
  `RoutineExercise`, `Workout`, `WorkoutExercise`, `SetEntry`, `CardioEntry`, `Exercise`,
  `MeasurementType`, `BodyMetric` - `MIGRATION_9_10`, a plain `ADD COLUMN` migration) plus the
  `preferences` DataStore document (`is_dirty`/`last_modified_at` keys, stamped by the single
  shared `edit()` helper every setter already funnels through). Verified via a real in-place
  upgrade with pre-existing data (not just `MigrationTest`'s synthetic inserts), per the same
  workflow that caught a genuine bug in `MIGRATION_8_9` before. Every existing DAO write path gets
  fresh values "for free" via entity constructor defaults (`isDirty: Boolean = true,
  lastModifiedAt: Long = System.currentTimeMillis()`), since every `@Insert`/`@Update` takes a
  full entity object - `RoutineDao.updatePosition` (the drag-reorder path, a raw `UPDATE` bypassing
  the entity object) was the one write site needing an explicit code change, confirmed via a
  codebase-wide audit for other bypasses (none found).
    - **Read/clear plumbing**: every synced DAO (`ExerciseDao.getDirty`/`clearDirty`,
      `RoutineDao.getDirtyRoutines`/`getDirtyRoutineExercises` + their `clearDirty*` counterparts,
      `WorkoutDao.getDirtyWorkouts`/`getDirtyWorkoutExercises`/`getDirtySetEntries`/
      `getDirtyCardioEntries` + their `clearDirty*` counterparts, `MeasurementDao.getDirtyTypes`/
      `getDirtyMetrics` + their `clearDirty*` counterparts) exposes a `getDirty*(limit: Int)` query
      (oldest-`lastModifiedAt`-first, scoped to sync-eligible rows only - e.g. `isCustom = 1` for
      exercises, `workoutStatus = 'COMPLETE'` for anything workout-shaped) and a `clearDirty*(ids)`
      update. `PreferencesRepositoryImpl` gained the equivalent single-document
      `getDirtyPreferences()`/`clearPreferencesDirty()` pair.
    - **The push job itself is built**: `:core:sync`'s `push/SyncPushRunner.kt`. `push()` checks
      `isPrimary()` first (a no-op, not an error, if this device isn't primary - see the
      Primary-status check item below), then walks every entity type in a fixed order plus the
      preferences document, then tombstones, sharing a single 1,000-row write budget across the
      entity-type walk (tombstone deletes get their own separate 1,000-row budget, since Firestore
      bills deletes against a different daily quota than writes). Each row's `isDirty` clears (or
      its tombstone gets removed) immediately after its own Firestore write/delete is confirmed -
      not batched at the end - via a shared `pushDirtyBatch` helper (`SyncPushRunner.kt`, `internal`
      so it's unit-testable with fake lambdas, no real Firestore/Room needed) that any exception
      partway through a batch propagates out of immediately, aborting the whole run: rows already
      confirmed stay clear, everything from the failure point on stays dirty for next run. Document
      paths (including the ancestor-id chain `RoutineExercise`/`WorkoutExercise`/`SetEntry`/
      `CardioEntry` need) are built by `FirestoreSyncPaths.kt`, the same class used for both writes
      and tombstone deletes so the two can never disagree about where a row lives. On any
      non-exception completion (full or capped-partial), `syncConfig.lastPushedAt` is stamped with
      the current time - the write side of the freshness watermark (Phase 2, since its read/compare
      side depends on that phase's Pull flow to fall back to on a mismatch). Not exercised against
      a real Firestore project via the periodic/manual triggers' full range of paths yet - per the
      Test strategy item's existing plan, that's manual verification on the AVD,
      not an automated round-trip test.
    - **`isDirty`** clears to `false` only once *that specific row's* Firestore write is
      confirmed - chosen over a single global "last successful push" timestamp watermark
      specifically because it survives a partial-batch failure precisely: if a batch of N dirty
      rows only gets M < N written before a network drop, the M already-confirmed rows have
      `isDirty` cleared individually and won't be redundantly re-pushed next run, while the
      remaining N-M stay dirty and get retried - a timestamp watermark would either have to
      redundantly re-push the whole batch (if the watermark only advances after 100% success) or
      risk silently skipping unconfirmed rows (if advanced optimistically mid-batch).
    - **`lastModifiedAt`** is not load-bearing for sync itself in this single-writer design
      (there's no merge or cross-device conflict comparison anywhere in this doc anymore) - kept
      purely as a plain, informational "when was this last edited" field, in case something else
      wants it later.
    - **Batch cap per sync-job run: 1,000 rows.** An established user's first-ever backfill
      (`isDirty = true` on everything) could plausibly be tens of thousands of Firestore writes in
      one shot, given the nested-subcollection structure above (a single workout can be 10-20+
      documents) - risking the Spark tier's 20K writes/day free cap in a single run. 1,000 is
      comfortably under that cap even across a few runs a day, while still clearing a typical
      active day's worth of real usage (~50 rows/day, ~1,000/month at a realistic training
      cadence) in a single run. The push job caps how many dirty rows it pushes per invocation,
      letting a large backfill spread across several periodic runs instead of one. The same
      cap-and-continue mechanism also handles the full-resync a device does the moment it claims
      primary (Phase 2) - no special-casing needed, it's the same "push whatever's dirty, up to
      the cap" loop either way.
    - **Oldest-dirty-first ordering** (`ORDER BY lastModifiedAt ASC` per entity type, free since
      every synced row already carries that column) - whenever there's more dirty data than one
      run's cap, older changes drain before newer ones, so the backlog is guaranteed to make
      progress over time rather than risking newer edits perpetually cutting the line ahead of an
      old backfill that never finishes catching up.
    - **Sync status is four states, not a binary success/fail**, since a run can complete
      successfully while still leaving a capped backlog for next time - that's normal
      progress, not a failure. `SyncStatus(lastSyncedAt: Long?, isFullyUpToDate: Boolean,
      lastError: AuthErrorReason?)` (`:core:domain`'s `model/SyncStatus.kt`) is `push()`'s return
      value, rendered in priority order: `lastError != null` → "Sync failed"; `isFullyUpToDate` →
      "Synced"; `lastSyncedAt != null && !isFullyUpToDate` → "Backing up..." (capped batch still
      working through a backlog); `lastSyncedAt == null` → "Not yet synced." `isFullyUpToDate`
      reflects only *this run's* budget usage (did every entity type's read come back short of its
      requested limit). `SyncStatus` now persists across app restarts (`:core:sync`'s
      `push/SyncStatusStore.kt`, see the Sync trigger item below) - the not-primary case is
      deliberately excluded from that persistence rather than left as an open question:
      `SyncPushRunner.push()` skips the save when the result is specifically the not-primary
      no-op shape, since that means this device isn't the one syncing right now, not that its
      last real sync stopped being true - persisting it would otherwise overwrite a genuinely
      persisted "Synced at 2:14 PM" with "Not yet synced" the instant this device loses primary
      status.
- [x] Primary-status check: the push job's first step, every run, is confirming this device is
  still the account's designated primary (`users/{uid}/syncConfig`'s `primaryDeviceId` field, see
  Phase 2 - a device only ever becomes primary via the explicit "Claim primary" action there).
  `SyncDeviceRepository.isPrimary()`/`IsPrimaryUseCase` (a plain read of the same `syncConfig`
  document `ensurePrimaryClaimed()` checks, no claim attempt) is built and is
  `SyncPushRunner.push()`'s
  first check - not primary means an immediate no-op return, no reads/writes attempted. The
  periodic job doesn't just no-op silently forever once superseded, either:
  `SyncPushWorker.doWork()`
  cancels its own `WorkManager` schedule (`cancelUniqueWork`) the moment `isPrimary()` comes back
  `false`, rather than firing and no-opping on every future run.
- [x] Delete propagation: the write side (tombstoning) is done and verified; the push job now
  reads tombstones and clears each one once its Firestore delete is confirmed (`SyncPushRunner`'s
  `processTombstones`, see the Change-tracking mechanism item above) - not yet exercised against a
  real Firestore project (manual verification, still pending, like the rest of the push job).
    - **`sync_tombstones`** (`MIGRATION_10_11`, `SyncTombstoneEntity`): entity type + old id, plus
      `parentId`/`grandparentId` for entity types that nest under a parent collection in Firestore
      (`RoutineExercise` needs its routine id; `WorkoutExercise` needs its workout id;
      `SetEntry`/`CardioEntry` need both their workout exercise id and its workout id) - null for
      types that sit flat at Firestore's top level (`Exercise`, `MeasurementType`, `BodyMetric`,
      `Routine`, `Workout`). `SyncTombstoneDao` owns all reads/writes to this table.
    - **Cascade-victim enumeration and the tombstone write live in the repository layer, not the
      entity DAOs** - each entity DAO (`ExerciseDao`/`RoutineDao`/`WorkoutDao`/`MeasurementDao`)
      only exposes plain child-id `@Query` methods (e.g. `WorkoutDao.workoutExerciseIdsFor`); the
      corresponding repository (`ExerciseRepositoryImpl`/`RoutineRepositoryImpl`/
      `WorkoutRepositoryImpl`/`MeasurementRepositoryImpl`) composes those queries with
      `SyncTombstoneDao`'s writes and the actual delete inside one `RoomDatabase.withTransaction`
      block, injecting `RegimenDatabase` alongside its own DAO for that. Deciding what counts as a
      cascade for a given entity is business logic, not raw persistence, so it belongs one layer up
      from the DAO - the DAO-level `@Transaction` default-method version this started as (composing
      calls on a single DAO interface, the same pattern `applyOrder`/`replaceRoutineExercises`
      already used) worked, but left every entity DAO also carrying sync-domain knowledge
      (`SyncEntityType`, tombstone-list construction) duplicated across four DAOs.
    - **Every cascade-deleted descendant gets tombstoned too, not just the row the repository call
      touched** - `Routine`→`RoutineExercise`, `Workout`→`WorkoutExercise`→(`SetEntry`,
      `CardioEntry`), `MeasurementType`→`BodyMetric` (`RoutineRepositoryImpl.delete`,
      `WorkoutRepositoryImpl.deleteWorkout`, `MeasurementRepositoryImpl.deleteType`). `Exercise`'s
      cascades (`RoutineExercise`/`WorkoutExercise`) never actually fire in practice -
      `DeleteExerciseUseCase` blocks deleting an exercise that's still referenced anywhere - so
      `ExerciseRepositoryImpl.delete` tombstones only the exercise itself, no enumeration needed.
    - **`RoutineRepositoryImpl.saveRoutine`** (the routine editor's save flow) also deletes
      `RoutineExercise` rows outside `.delete()`'s cascade - `RoutineDao.replaceRoutineExercises`
      clears and re-inserts the whole exercise list on every edit, not a diff, so `saveRoutine`
      has to tombstone whatever's genuinely absent from the new list itself, before calling it. A
      routine can never contain the same exercise twice (enforced in the editor's UI state - see
      `RoutineEditorViewModel`), so `saveRoutine` matches old rows to new specs by `exerciseId` and
      preserves the surviving row's id, minting a fresh one only for a genuinely new exerciseId -
      an exercise that's kept (even if its target sets/reps/rest changed) keeps its row and its
      eventual Firestore document, rather than every save tombstoning and recreating the routine's
      entire exercise list regardless of what actually changed.
    - Covered by `ExerciseRepositoryImplTest`/`RoutineRepositoryImplTest`/
      `WorkoutRepositoryImplTest`/`MeasurementRepositoryImplTest` (`:core:data`'s repository
      `androidTest` tier, per `docs/testing.md`) and `MigrationTest`'s `migrate10To11` case.
- [~] Sync trigger: periodic, not write-through-per-mutation - a background job (WorkManager)
  batches unsynced local changes on a schedule rather than pushing to Firestore on every Room
  write, since batching is cheaper on the daily quota. **Runs once every 24 hours**
  (`:core:sync`'s `push/SyncPushWorker.kt` + `push/SyncSchedulerImpl.kt`) - WorkManager's
  `PeriodicWorkRequest` only *requires* a 15-minute minimum interval, but this app's actual usage
  pattern (per the batch-cap reasoning above, ~50 rows on an active day) has no need to run
  anywhere near that often; daily is enough to keep the cloud backup current without draining
  battery on frequent wakeups for no real benefit. Manual "Sync now" (below) covers the rare case
  of wanting a sync sooner than the next scheduled run. Constrained with `NetworkType.CONNECTED`
  (don't even attempt a run offline) and `BackoffPolicy.EXPONENTIAL` on failure (WorkManager's
  built-in retry, not custom retry logic - `SyncPushWorker.doWork()` returns `Result.retry()` on
  any `lastError`, `Result.success()` otherwise). Scheduled (idempotently, `KEEP` not `REPLACE`)
  on every successful sign-in in `AccountViewModel`, alongside the existing primary-device claim;
  cancelled outright on sign-out, since there's no signed-in account left to sync for. Hilt +
  WorkManager integration required `RegimenApplication` to implement `Configuration.Provider`
  (injecting `HiltWorkerFactory`) and removing WorkManager's default `androidx-startup`
  initializer from the manifest in favor of that on-demand one. The push job's core logic (via
  the manual "Sync now" path, which calls the exact same `SyncPushRunner.push()`) has been
  exercised against a real Firestore project/device and confirmed working - data visible in the
  Firestore console, `isDirty` clearing correctly, tombstones clearing correctly. The upfront
  `ConnectivityManager` check is also confirmed working manually (airplane mode → "Sync now"
  fails fast with "Sync failed" instead of hanging; back online → succeeds normally). **Still not
  manually verified**: the periodic `WorkManager` trigger actually firing on its real 24h schedule
  (being verified passively - user is running the app normally for about a week rather than
  forcing it via `adb shell cmd jobscheduler run -f`) and the 5-minute timeout backstop
  specifically (impractical to deliberately force a hang that lasts past 5 minutes without
  degraded-but-not-fully-off network conditions - relying on code review confidence, not manual
  verification, for this one).
    - **Manual "Sync now" on the primary device's Account screen** - built. Calls
      `SyncNowUseCase`, an out-of-band run of the exact same `SyncPushRunner.push()` the periodic
      job calls (not a separate code path) - unlike Pull/Claim, this isn't destructive or a full
      replace, so it needs no count-based confirmation, just the same
      busy-spinner/disabled-while-in-flight treatment (`AccountAction.SYNC_NOW`) every other
      action on this screen already uses. The row below it renders `SyncStatus` via
      `:core:common-ui`'s `SyncStatus.text()` (mirroring `AuthErrorReason.text()`'s pattern) in
      the doc's priority order, showing the actual synced time ("Synced at 2:14 PM," honoring the
      device's 12h/24h clock preference via `SessionFormat.time()`) once fully caught up.
      `SyncStatus` is persisted across app restarts in `:core:sync`'s `push/SyncStatusStore.kt`
      (same `sync_state` DataStore `DeviceIdentityStore` already uses) - `SyncPushRunner.push()`
      saves whatever it computes on every run, periodic or manual alike, and `AccountViewModel`
      reads it back once on init instead of starting at `null`, so a relaunch reflects the actual
      last outcome rather than always resetting to "Not yet synced."
    - Failure visibility needs a dedicated state, not just a timestamp that silently goes stale -
      see the Settings/Account UI split above: a "last sync error" (auth expired / offline / quota
      exceeded) is tracked separately from "last synced at," so the UI can always distinguish
      "never synced" from "last attempt failed" rather than leaving the user unable to tell.
      Auth-expiry specifically should prompt re-sign-in as the fix, not fail silently. **Still not
      built**: today `AuthErrorReason.NETWORK`/`UNKNOWN` are the only classifications `push()`
      produces (see Revoked Google grant detection below for the missing auth-expiry case) - no
      re-sign-in prompt is wired to a sync failure specifically yet.
    - **A claim from another device can race an in-flight push - fully closed, both
      directions.** `SyncPushRunner` re-checks `isPrimary()` between every entity-type step, not
      only once at the very start of a run (`ensureStillPrimary()`, throwing
      `NotPrimaryAnymoreException` so every call site aborts automatically rather than needing its
      own check) - if a *different* device claims primary mid-run, this run stops immediately
      rather than continuing to write into a destination that device just claimed. `SyncPushWorker`
      also cancels its own periodic schedule the moment `isPrimary()` comes back `false` at the
      start of a run, rather than firing and no-opping forever. That side alone is reactive though
        - it only notices *after* the fact, on the next checkpoint. The real lock,
          `syncConfig.lockedAt` (a timestamp, not a plain boolean, so a crashed/killed push or claim
          that never clears it doesn't lock the account out forever - a consumer treats it as stale
          past `LOCK_STALE_AFTER_MS`, 2x the push job's own 5-minute timeout), closes it from the
          other side too: Phase 2's "Claim primary" checks it (fresh → refuse, stale/absent →
          proceed)
          inside the same transaction it uses to claim primary, *and* holds it for its own entire
          wipe-and-force-push duration, not just that initial check - and `SyncPushRunner` now
          refuses
          to start at all while it's held by anything else. The `isPrimary()` polling above is now
          genuinely redundant defense-in-depth rather than the only protection, since the lock
          closes
          the race directly - kept rather than removed, since it's cheap and catches anything the
          lock
          somehow didn't.
    - **Revoked Google grant detection - built, but manual testing found its premise was wrong.**
      `:core:sync`'s `auth/SessionRevocation.kt` has the shared `Throwable.isSessionRevoked()`
      check (`FirebaseAuthInvalidUserException`/`FirebaseAuthInvalidCredentialsException`, or a
      Firestore call failing with `FirebaseFirestoreException.Code.UNAUTHENTICATED`/
      `PERMISSION_DENIED`), used by both `SyncPushRunner.toReason()` and
      `AuthRepositoryImpl.deleteCloudData()` - both call `firebaseAuth.signOut()` locally the
      moment it's detected and report `AuthErrorReason.SESSION_REVOKED` ("You've been signed out.
      Please sign in again."). **This item was originally written assuming revoking Regimen's
      access from Google Account settings would eventually surface as one of these exceptions -
      manually tested and confirmed false.** Firebase Auth's session is backed by its own
      independently-managed refresh token once established; it does not re-validate against
      Google's live OAuth grant on renewal. Revoking access at `myaccount.google.com` only stops
      Google from issuing *new* credentials to this app - it does nothing to a Firebase session
      that already exists, so sync keeps working indefinitely afterward, fully oblivious to the
      revocation. **The actual way to stop sync** is one of the app's own actions: **Sign out**
      (ends this device's local session) or **Delete cloud data** (also calls
      `firebaseAuth.currentUser?.delete()`, which *does* terminate the Firebase session
      server-side) - revoking access at Google's site alone does neither. **What this
      classification actually protects against, then, isn't Google-side revocation at all - it's
      a cross-device scenario**: calling "Delete cloud data" from one device deletes the Firebase
      Auth user record, which invalidates every *other* device's session too; a device still
      signed in elsewhere should correctly hit an invalid-session error on its next sync attempt
      and force a local sign-out via this same code path. That cross-device case hasn't been
      manually verified yet (would need two signed-in devices, delete-cloud-data on one, confirm
      the other force-signs-out on its next sync).
- [x] Test strategy splits by what's actually being tested, rather than standing up a Firebase
  Local Emulator Suite (no CI pipeline exists here to make that pay for itself):
    - **Business logic** (cascade-tombstone enumeration, retry/backoff decision, the
      primary-status check, the pull-blocks-during-active-workout rule) is pure and
      Firestore-agnostic - covered by plain unit tests with fakes, no network or emulator
      involved, per `docs/testing.md`'s existing bar that real branching logic gets real coverage.
    - **Actual Firestore round-trips** (a push really lands, a claim really wipes-and-replaces the
      destination) are verified manually against the real project on the Android (AVD) emulator
      with a real Google account, via a written action script - matching the existing
      verification workflow for this project rather than adding automated integration tests
      against a fake backend.

---

## Phase 2: multi-device support (Pull cloud data / Claim primary)

Purely additive over Phase 1 - the overwhelming majority of users, expected to only ever have one
device, should never see any of this at all: sign in, and syncing just starts happening in the
background, no button-tapping, no confirmation dialogs. This phase's UI (the disclaimer and the
two actions below) only ever appears once there's an *actual* competing primary device to
reconcile against - never for the common, single-device case.

- [x] **`users/{uid}/syncConfig`** - a single document holding `primaryDeviceId` for the account
  (built as `SyncConfigDto`, `:core:sync`'s `device/SyncDeviceRepositoryImpl.kt`; no display label
  yet, that's still just a possible cosmetic addition). This is the **live, authoritative** record
  of which device is primary - every device reads it directly rather than comparing against any
  local bookkeeping of its own, which is what keeps this design immune to the local-state
  staleness problems (Auto Backup restoring stale values, Firebase `uid` churn on account
  deletion, etc.) an earlier, considerably more complex draft of this doc ran into. Device identity
  is a random UUID generated once per install (`:core:sync`'s `device/DeviceIdentityStore.kt`,
  its own dedicated DataStore), stored locally - low stakes if it doesn't survive a backup/restore,
  since the worst case is just a redundant re-claim prompt, not a correctness issue. Included in
  `deleteCloudData()`'s subcollection list (`AuthRepositoryImpl.kt`) alongside the entity
  subcollections, since Firestore doesn't cascade-delete subcollections when their parent document
  is deleted - leaving it out orphaned the claim document on every delete-cloud-data run.
- [x] **Silent auto-claim when no primary exists yet.** On sign-in, if `syncConfig.primaryDeviceId`
  is unset, this device claims it immediately and automatically via a Firestore transaction
  (`ensurePrimaryClaimed()`) - **no confirmation dialog, no disclaimer, no user action at all.**
  There is genuinely nothing to protect against in this case (an empty destination, no other device
  that could possibly be affected), so gating it behind the same confirm-and-claim ceremony the
  *actual* multi-device case needs would only add friction to the single most common path (a
  single-device user signing in for the first time) for no safety benefit. This is the only way
  most users will ever interact with Phase 2 at all: implicitly, once, at sign-in, then never
  again. Wired into `:feature:account`'s `AccountViewModel.signIn()` only - not onboarding, which
  has no sign-in action of its own (see Phase 1's sign-in entry points above). Best-effort: a
  failed claim (e.g. another device already primary) doesn't surface any error, since whether this
  device becomes primary is orthogonal to whether sign-in itself succeeded.
  `EnsurePrimaryClaimedUseCase`/`SyncDeviceRepository` live in `:core:domain`; a
  `FakeSyncDeviceRepository`
  in `:core:testing` backs `AccountViewModelTest`'s claim-triggered/not-triggered cases.
- [x] **Freshness watermark, to catch a stale Auto-Backup restore before it can regress the
  cloud - built.** Device-ID matching `primaryDeviceId` isn't sufficient on its own to prove this
  device's local state is safe to push from - Auto Backup runs on its own opportunistic (~daily)
  schedule, so a restored device's snapshot can be older than the last successful push.
  Concretely: the same physical device edits an entity, pushes it, then edits it *again* before
  any Auto Backup snapshot captures that second edit, then gets reformatted - the restored local
  state only has the first edit, `isDirty` still `false` for it (since it *was* successfully
  pushed once), so a naive resume-as-primary would never re-push it at all... but a related risk
  is worse: if the restore instead lands with that row `isDirty = true` for a *stale* reason and
  gets pushed, it would silently overwrite the cloud's already-newer second edit with the older
  restored one. `:core:sync`'s `device/FreshnessWatermarkStore.kt` holds this device's local copy
  of `syncConfig.lastPushedAt` (same `sync_state` DataStore covered by Auto Backup, alongside the
  device id) - written every time `SyncPushRunner` completes a successful run (in lockstep with
  the cloud write) or "Pull cloud data" succeeds (reset to whatever it just read). Compared before
  every push attempt, inside `SyncPushRunner.doPush()`'s same read that already fetches
  `lockedAt`: match (including both-null, the common never-synced-anywhere case) → proceed
  normally; mismatch → refuse via the same not-primary no-op path (no persisted-status
  clobbering, per that existing fix). The UI side needed its own signal, since `primaryDeviceId`
  matching this device no longer guarantees "safe to treat as normally primary" -
  `SyncDeviceRepository.secondaryDeviceReason(): SecondaryDeviceReason?` (`COMPETING_PRIMARY` |
  `STALE_LOCAL_STATE`, replacing the old plain `hasCompetingPrimary(): Boolean`) drives the same
  disclaimer/Pull/Claim UI with reason-specific copy - "Sync paused on this device" for the stale
  case, since "Another device is primary" would be actively wrong when nothing else has actually
  claimed primary. One scalar, one comparison, no merge or per-row reconciliation - not a
  reintroduction of the complexity that got cut earlier. **Not yet manually verified** -
  would need actually reproducing a stale Auto Backup restore (backup a device mid-session,
  edit again, restore over it) to see the disclaimer switch to the stale-state copy and confirm
  automatic push actually refuses until Pull/Claim resolves it.
- [x] **Secondary-device UI**: once a primary *is* already claimed (by this same device
  previously, or by a different one), any device that isn't the current primary shows a
  persistent disclaimer (Account screen, `AccountUiState.isSecondaryDevice` -
  `SyncDeviceRepository.hasCompetingPrimary()`, deliberately distinct from `!isPrimary()`, which
  is also true for the brand-new "nobody's claimed primary yet" case where nothing should show at
  all), plus exactly two actions - both full, one-directional, unconditional replaces, never a
  merge or a destination-state-dependent choice:
    - **Pull cloud data.** `SyncReplaceRepositoryImpl.pullCloudData()` (`:core:sync`'s `replace/`
      package) wipes this device's local sync-scoped state (the same entities/scope as Phase 1's
      sync scope) and replaces it with whatever's currently in Firestore, read back via the new
      `FirestoreSyncReader` (the mirror image of `FirestoreSyncPaths`/the push job's writes -
      reads each parent collection then its subcollections one parent at a time, not a
      `collectionGroup` query). Every entity's `firestore/*Mapping.kt` gained a `toEntity()`
      alongside its existing `toDto()`. **Refuses to run while a workout is in progress**
      (`IN_PROGRESS`/`IN_REST_TIME`/`PAUSED`/`EDITING`, via `WorkoutDao.hasAnyIncompleteWorkout()`)
        - that row lives in the same `Workout` table but was never part of sync scope to begin with,
          and a naive full-table wipe would destroy a live, foreground-service-backed session that
          has
          nothing to do with sync. Checked twice: once before the (potentially lengthy) Firestore
          read, to fail fast without wasting it, and again as the authoritative check inside the
          wipe's own Room transaction, since a workout can start in the gap between the two.
          Blocking
          outright (rather than trying to carve out a partial-table wipe) matches how the rest of
          the
          app already treats an active workout as an exclusive state. **Still not built**: resetting
          the local freshness watermark (above) to match `syncConfig`'s current `lastPushedAt` -
          deferred since the watermark's local storage doesn't exist as its own feature yet.
    - **Claim primary.** `SyncReplaceRepositoryImpl.claimPrimary()` atomically checks
      `syncConfig.lockedAt` and writes both this device's ID into `syncConfig.primaryDeviceId`
      *and* a fresh `lockedAt` inside one Firestore transaction - fresh `lockedAt` (within
      `LOCK_STALE_AFTER_MS`, 2x the push job's own 5-minute timeout as a clock-skew margin) means a
      push or another claim is genuinely in flight right now, so the claim refuses outright
      (`SyncReplaceErrorReason.PUSH_IN_PROGRESS`) rather than proceeding; stale or absent means
      it's safe to go. Unlike a normal push (which only holds the lock for its own run), Claim
      holds it for its *entire* duration - through the wipe and force-push below, not just the
      initial flip - and `SyncPushRunner` now refuses to start at all while it's held, closing the
      race in both directions: `primaryDeviceId` flipping early stops the old primary's *next*
      push attempt immediately (via its own `isPrimary()` check), and the held lock stops one
      that's already past that check from landing writes into a cloud tree Claim is concurrently
      wiping. Phase 1's mid-run polling (`SyncPushRunner.ensureStillPrimary()`) is now genuinely
      redundant defense-in-depth rather than the only protection. Once the claim succeeds,
      `FirestoreSyncReader.deleteAll()` wipes every document (recursively, including nested
      subcollections, in children-before-parents order at every level - not just tidiness, since
      Firestore has no referential integrity either, so an interruption leaves a harmless
      "childless parent" rather than invisible orphaned children a future call could never find),
      then every synced Room row/preferences document is marked dirty regardless of its prior
      state (each entity DAO's `markAllDirty`-shaped methods, scoped identically to that entity's
      own `getDirty` query) - not just whatever already happened to be dirty from this device's
      past history, which matters if this device was primary before and has mostly-clean
      `isDirty` state left over from prior incremental syncs. The actual upload reuses
      `SyncPushRepository.forcePush()`, not `push()` - the same incremental push loop Phase 1
      uses, but entered through a variant that skips `push()`'s own "is somebody else's lock
      already held" and freshness-watermark checks. Those checks exist to protect `push()` against
      a *different* device's in-flight write, but the lock Claim's own transaction just took a
      moment earlier is this same call chain's, not a foreign one - calling plain `push()` here
      would see that fresh `lockedAt`, treat it as another device's active write, and silently
      no-op the entire claim (reporting success without ever force-pushing anything, since a
      not-primary no-op carries no error). `forcePush()` exists specifically so this call site
      doesn't self-deadlock. A full initial backfill exceeding one run's batch cap isn't a failure,
      same as any capped-partial push - whatever's left dirty drains on the next periodic/manual
      run. The previously-primary device (if any) discovers it's been superseded passively, via
      its own periodic job's primary-status check - no push notification or cross-device
      messaging needed. **Known gap, not addressed**: if the Firestore wipe itself fails partway
      through (e.g. a network drop mid-delete), the account is left with `primaryDeviceId`
      already pointing at this device but a partially-wiped cloud tree - retrying Claim again is
      safe (both the wipe and the dirty-marking are idempotent), but there's no automatic
      resume/rollback, and no UI messaging yet prompting the user to retry specifically because of
      this.
    - Both actions' confirmation dialogs use `ConfirmDialog`'s existing
      `confirmEnableDelayMillis = 3000L` (the same mechanism and value `ActiveWorkoutSheet`
      already uses for ending a workout with incomplete exercises still pending) - the confirm
      button stays disabled for 3 seconds so a reflexive tap can't trigger what's an irreversible
      full replace in either direction.
    - **Both confirmations state a concrete count of what's about to be overwritten**, via
      `<plurals>` resources fed by `LocalWorkoutCountUseCase`/`CloudWorkoutCountUseCase` - "Claim
      primary" says "This will overwrite your cloud backup with the *N* workouts currently on
      this device," and "Pull cloud data" says "This will replace your local data with the *N*
      workouts in your cloud backup." Always shown, not conditional on anything - this is what
      protects against the realistic case of reformatting/reinstalling on a device that doesn't
      recover its old identity via Auto Backup: it shows up as a fresh, non-primary device with
      empty local data, and the *natural but catastrophically wrong* move is tapping "Claim
      primary" ("this is obviously my device") - which would silently wipe out the entire real
      cloud history and replace it with nothing. The correct sequence there is "Pull cloud data"
      first (safe - local is empty, nothing to lose), then claim primary only afterward once local
      actually reflects the restored history. Nothing else in this design stops someone from doing
      it in the wrong order; a visible "0 workouts" in the Claim confirmation is what makes that
      mistake obvious before it happens, without reintroducing conditional destination-state
      logic. The Firestore-side count uses a server-side `count()` aggregation query
      (`FirestoreSyncReader.countWorkouts()`), not a fetch of every document just to count them -
      the latter would burn real read quota proportional to history size purely to populate a
      confirmation dialog.
    - **Bug found and fixed while building this**: `AuthRepositoryImpl.deleteCloudData()`
      previously only deleted each top-level document per collection, silently orphaning every
      workout's `workoutExercises`/`setEntries`/`cardioEntries` and every routine's
      `routineExercises` (Firestore has no cascade delete). Now reuses
      `FirestoreSyncReader.deleteAll()`, the same recursive wipe Claim primary uses.
    - **Manually verified end to end on two real devices**, same Google account signed in on
      both: Pull cloud data (correct counts, correct data after replace, still shows as secondary
      afterward), Claim primary (correct counts, previously-primary device correctly shows the
      disclaimer after its next resume, workout-in-progress guard refuses Pull correctly). Three
      real bugs found this way, all fixed:
        1. `isSecondaryDevice` only ever refreshed once, in `AccountViewModel`'s `init` - a device
           demoted mid-session needed a full app restart to notice. Fixed with a lifecycle-aware
           refresh (`AccountViewModel.refreshOnResume()`, called from a `DisposableEffect`/
           `LifecycleEventObserver` on `ON_RESUME` in `AccountScreen.kt`) - not a live Firestore
           listener, a deliberate choice given how rare an active multi-device conflict actually
           is for this app; still needs backgrounding/foregrounding or navigating away and back,
           not instant, but far short of a full restart.
        2. `AccountViewModel.syncNow()` set `syncStatus` to whatever `push()` returned
           unconditionally - including the not-primary no-op shape, which would silently
           overwrite a real "Synced at ..." with "Not yet synced" the moment a device became
           secondary and someone tapped "Sync now" anyway. Fixed by skipping the assignment for
           that specific shape (mirroring the same fix already applied to what
           `SyncPushRunner.push()` persists) - and the button is now hidden entirely for
           secondary devices, not just guarded defensively.
        3. `AccountViewModel.confirmClaimPrimary()` never called `SchedulePeriodicSyncUseCase()`
           after a successful claim - a device that had been secondary already had its own
           periodic job self-cancel (it noticed it wasn't primary and stopped itself), and
           nothing else re-scheduled one once it became primary again. It would have stayed
           permanently unscheduled until that device's next sign-out/sign-in cycle, which could be
           never. Fixed by calling it (idempotent, `KEEP`) right after a successful claim.
    - **"Next sync scheduled at ..."** (Account screen, next to "Sync now," hidden for secondary
      devices) reads `WorkInfo.nextScheduleTimeMillis` for the actual unique periodic work
      (`SyncScheduleRepository.nextScheduledSyncAt()`) - genuine WorkManager-computed data, not
      derived/guessed from the 24h interval. `null`/hidden if nothing's currently scheduled.
- [x] Firestore document schema evolution (unlike Room, there's no formal `Migration` mechanism
  for a schemaless store): additive changes are the default and need no migration step - every
  `*Dto` gives each field a default value (already true throughout, required for Firestore's
  reflection-based POJO mapping to work at all when a field is absent), so a missing new field on
  an old document just reads as that default rather than failing. Renames/breaking shape changes
  are the risky case (an old app version could still push the old shape while a new version
  writes the new one) but low-probability given single-primary-writer design, not concurrent app
  versions writing simultaneously - the mapper still fails closed on an unrecognized shape
  (ignore/default the field) rather than crash, via `:core:sync`'s `firestore/EnumFallback.kt`
  (`parseEnumOrDefault<T>(value, default)`, falling back rather than throwing the way a plain
  `Enum.valueOf(value)` would). No dedicated schema-version field or migration step planned; this
  is a mapper-layer convention, not new infrastructure. **Bug found and fixed while auditing
  this**: every `*Dto.toEntity()` reverse mapper added earlier this session for "Pull cloud data"
  (`ExerciseDto`/`WorkoutDto`) used raw `EnumType.valueOf(string)` for their enum fields
  (`ExerciseType`/`MuscleGroup`/`Equipment`/`WorkoutStatus`/`WorkoutEndReason`) - which throws on
  an unrecognized value, directly violating this item's own stated convention, and would have
  crashed a Pull entirely the first time any single document had an enum value an older app
  version didn't recognize yet. `PreferencesRepositoryImpl`'s pre-existing enum parsing
  (`UnitSystem`/`ThemeMode`/`MaxWorkoutDuration`) was already correctly defensive
  (`runCatching { ... }.getOrNull() ?: default`) and needed no change. Every `*Dto` field name
  deliberately avoids the `is`-prefix Kotlin/Java-bean boolean convention (`complete`/`skipped`/
  `done`/`custom`/`builtIn`, not `isComplete`/`isSkipped`/`isDone`/`isCustom`/`isBuiltIn`) - for a
  `data class`'s `val isXxx: Boolean`, Kotlin generates a bean-style `isXxx()` getter, Firestore's
  serializer strips that `is` prefix when deriving the document field name on write, but
  `toObject()`'s data-class deserialization matches document fields against constructor parameter
  names literally on read - so an `is`-prefixed field name would silently round-trip back to its
  default value every time, rather than the value actually pushed. **Bug found and fixed**: every
  boolean `*Dto` field originally used the `is`-prefixed name matching its `*Entity` counterpart,
  so every synced `SetEntryEntity.isComplete`/`WorkoutExerciseEntity.isSkipped`/`isDone`/
  `ExerciseEntity.isCustom`/`MeasurementTypeEntity.isBuiltIn` silently reset to its default on
  every "Pull cloud data" - concretely, this meant every synced set showed as incomplete, which
  fed directly into personal records reading as empty on any device relying on synced (not
  locally-logged) data, since the PR query filters on `isComplete = 1`. `*Entity`/domain-model
  field names are unaffected (still `is`-prefixed, which is correct/idiomatic there - only the
  Firestore-facing `*Dto` shape needed the rename); each `toDto()`/`toEntity()` mapper now does the
  rename at the boundary.
- [x] Manual account/data deletion is two distinct, clearly-separated actions on the dedicated
  Account screen (Phase 1) - not one ambiguous "delete account," since there's no separate
  Regimen account to delete: **sign-out** keeps local data and the cloud backup, just stops
  syncing; **delete cloud data** wipes every Firestore document under `users/{uid}/**` (which
  naturally includes the `syncConfig` document too, so a subsequent sign-in correctly finds no
  primary claimed at all - no special-casing needed, `AuthRepositoryImpl.deleteCloudData()`'s
  `syncedSubcollections` loop plus the `preferences` doc and the user doc itself) plus the
  Firebase Auth user record (`firebaseAuth.currentUser?.delete()`), but does not revoke the
  Google OAuth grant (the user does that themselves via Google Account settings) and does not
  touch local data. Each confirmation modal's copy states plainly what that specific action does
  and does not do (`account_sign_out_dialog_text`/`account_delete_cloud_data_dialog_text`), so the
  two are never confused with each other. **Correctness gap found while building Phase 2's Claim
  primary (which needed the same kind of wipe and does it properly there)**: `deleteCloudData()`'s
  `syncedSubcollections` loop only deletes each *top-level* document in `workouts`/`routines`/
  etc. - Firestore has no cascade delete, so a workout's `workoutExercises` (and their own
  `setEntries`/`cardioEntries`) and a routine's `routineExercises` subcollections are left behind,
  orphaned rather than actually removed. Not fixed here (out of scope for Phase 2) - see
  `FirestoreSyncReader.deleteAll()`'s doc comment for the recursive version this method should
  eventually be updated to use.

---

## Cross-cutting notes

**Auto Backup is not a substitute for Phase 1/2 - they solve different problems.**
`AndroidManifest.xml`
already declares `android:allowBackup="true"` with `dataExtractionRules`/`fullBackupContent`
configured, so disaster-recovery for **logged-out, local-only users** ("don't lose my data if I
lose/reset my phone") is already covered today, with zero Firestore work. But this doc's actual
goal - sync for **signed-in users** - is a different problem Auto Backup structurally cannot
solve:

- Auto Backup only **restores at app install / device setup time** - it cannot pull anything into
  an already-running app on a second device. It can't do "install Regimen on a phone and a
  tablet, sign into the same account, keep both current" at all - only Phase 2's explicit "Pull
  cloud data"/"Claim primary" actions let a second device participate (deliberately, manually -
  see the multi-device note below).
- It runs on Android's own opportunistic schedule (idle + charging + Wi-Fi, roughly once a day)
  with no on-demand trigger and no app-visible status - not a deliberate, user-controlled sync.
- It's tied to the *device's* system backup account (an OS-level setting), not a deliberate
  in-app sign-in - no "signed in as," no "last synced at," no user control.
- It has a hard 25 MiB per-app size cap; Firestore has no such ceiling for a workout log that
  grows over years.

So Phase 1/2 stay necessary regardless of Auto Backup's existing coverage.

**Cost:** free at personal-use scale. Firebase Auth is free for Google Sign-In. Firestore's
Spark (free) tier - 1 GiB storage, 50K reads/day, 20K writes/day, 20K deletes/day, 10 GiB
egress/month, no credit card required - comfortably covers a single-user (or small handful of
users) workout log. Only exceeding those quotas (real multi-user scale) would require the
pay-as-you-go Blaze plan.

**Fully replaces the "build a custom backend" idea:** Firestore + Firebase Auth is the entire
backend for this app's defined scope - every piece of logic that would traditionally live
server-side (delete/tombstone handling, document mapping, primary/secondary determination) is
planned as client-side logic elsewhere in this doc, and there's no requirement here (server-side
aggregation, multi-user sharing, webhooks) that would need a separate custom backend. A custom
backend would only re-enter the picture if the app's scope itself changed (multi-user/social
features, server-computed analytics) - a new decision outside this doc, not a gap in it.

**Multi-device use is deliberate and manual, not automatic.** Rotating between two devices (e.g.
phone + tablet) is supported, but only through an explicit choice on the non-primary device each
time you want it caught up: **Pull cloud data** to catch this device up with whatever the primary
has pushed, or **Claim primary** to make this device the new authoritative one going forward.
There is no automatic "both devices always reflect the latest" behavior, and no merge of
independent edits from two devices - whichever direction you explicitly choose fully replaces the
losing side. This is a deliberate simplification: an earlier draft of this doc pursued automatic,
eventually-consistent multi-device convergence (periodic pull, per-document last-write-wins
conflict resolution) and kept surfacing genuine correctness gaps, because reconciling two devices'
data automatically is a hard problem. Restricting to one live writer at a time, with every
other device requiring an explicit destructive confirmation to participate, sidesteps that
problem class by construction rather than solving it - at the cost of "automatic" multi-device
convergence, which this app doesn't actually need for its personal-use scope.

**Quota scope:** Spark tier's free limits (1 GiB storage, 50K reads / 20K writes / 20K
deletes per day, 10 GiB egress/month) are pooled per Firebase *project*, not per user -
comfortable for personal use; would need re-evaluating if this app ever had many concurrent
users sharing one project. Exceeding a daily quota fails further reads/writes/deletes until
the daily reset (no silent auto-billing, since Spark has no billing account attached); lifting
the cap requires manually moving the project to the Blaze plan.
