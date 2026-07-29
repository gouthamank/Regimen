# Remote sync (future - not started)

Regimen is local-only today (see `docs/architecture.md`); this doc tracks groundwork for
eventual multi-device sync, kept separate until picked up. Not scheduled - revisit before
starting.

**Staged so no single step can destroy data.** The phases below are ordered specifically so
each one is safe to ship and bake in on its own before the next begins - each phase ships and
runs in production for a while before the next starts, rather than landing all at once:

- **Phase 0** is a one-shot, irreversible local schema migration - isolating it means if
  something's wrong, it surfaces from real usage before any sync code ever touches that data.
- **Phase 1** is push-only (local → Firestore, no download, no delete propagation) - a pure
  backup. Nothing incoming can ever touch local data, so no bug in this phase can lose or
  overwrite anything on-device.
- **Phase 2** (pull, merge, delete propagation) only starts once Phase 1 has run clean for a
  while and there's real Firestore data to test merge logic against, instead of only synthetic
  scenarios.

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

## Phase 1: Firestore push-only sync (local → cloud backup)

The "no backend to build or run" path to true multi-device sync, evaluated as a much
lower-effort alternative to a custom server + hand-rolled delta-sync protocol. Assumes Phase 0
(UUID keys) is done first, since Firestore document IDs need the same global-uniqueness
property. This phase only ever *writes* to Firestore - no download, no merge, no delete
propagation - so a bug here can't lose or overwrite local data; worst case is an incomplete or
stale cloud backup, not data loss on-device.

- [ ] One-time Firebase console setup (manual, done once before any code lands):
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
- [ ] Write and host a short privacy policy (personal domain, not a Play Store listing concern -
  no Play distribution is planned). Needed for a reason independent of Play: Firebase Auth's
  Google Sign-In is backed by a Google Cloud OAuth client, and while its consent screen sits in
  **"Testing" status**, refresh tokens expire after **7 days** - the periodic/unattended sync job
  would silently stop working weekly until manual re-auth. Moving the consent screen to
  **"Production" status** avoids that expiry and doesn't require Play distribution or a Google
  review (non-sensitive scopes only: email/profile) - but "External" user type (required; "Internal"
  is Workspace-org-only, not available for a personal Gmail account) requires a privacy policy URL
  in the consent screen config before Google allows publishing to Production. Scope the policy to
  what's actually collected: Google account email (auth only), workout/routine/measurement data
  synced to Firestore, no third-party sharing, no ads/analytics.
- [ ] New Gradle dependencies (none present today): Firebase Auth + Firestore (via the Firebase
  BoM), the `google-services` Gradle plugin, and WorkManager (for the periodic sync job) - plus
  the `google-services.json` from the Firebase console. For "Sign in with Google" specifically,
  use **Credential Manager** (`androidx.credentials:credentials` +
  `androidx.credentials:credentials-play-services-auth` +
  `com.google.android.libraries.identity.googleid:googleid`), not the legacy
  `GoogleSignInClient`/Play Services Auth API, which Google has deprecated - verify current
  artifact versions before pinning, per this doc's usual caveat.
- [ ] Graceful degradation without Google Play Services: this app has zero GMS dependency today
  (pure AndroidX, `minSdk 26`); Credential Manager's Google ID option requires Play Services to
  be present/up to date. Since sign-in must stay "optional, skippable, fully usable local-only"
  (decided below), absence of Play Services needs to show as a normal disabled/hidden sign-in
  option, not an unhandled exception.
- [ ] Add the `INTERNET` permission to `AndroidManifest.xml` - the app currently declares none
  (`VIBRATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` only),
  since it has no network access today. This is the point where "local-only" stops being
  literally true even for users who never sign in.
- [ ] New **`:core:sync`** module for auth/sync, not folded into `:core:data` - the surface area
  (Firebase Auth + Firestore clients, the WorkManager sync job, account-switch safety, tombstone
  tracking, LWW conflict resolution) is substantial and shared across `:feature:account`,
  `:feature:settings`, and the sync worker alike, mirroring `:core:data`'s role for the
  Room-backed repositories rather than mixing local-persistence DI with cloud-sync DI in one
  module. Follows the existing `di/<Name>Module.kt` convention for its Hilt bindings.
- [ ] Sign-in entry points: a new onboarding page mentioning that signing in enables sync
  (optional, skippable - the app is fully usable local-only without it), plus a two-tier
  auth/sync UI rather than a single Settings section:
    - **Settings' "Account" summary row**: signed-in account (or "Signed out"), last successful
      sync timestamp (or the "last attempt failed" state from the sync-trigger item above), and
      a "Sync now" action - a compact glanceable summary, not the full detail surface.
    - **Dedicated Account screen** (tapping the summary row navigates in - a new nav destination,
      not a dialog): full sign-in details, plus exactly two destructive actions - **not** a
      "delete account" concept, since there's no separate Regimen account to delete, only a
      signed-in Google identity and a Firestore backup:
        - **Sign out** - stops syncing, keeps local data, keeps the cloud backup as-is.
        - **Delete cloud data** - wipes every Firestore document under `users/{uid}/**` (and,
          as routine cleanup, the now-pointless Firebase Auth user record) - does **not** touch
          the Google OAuth grant, which the user can only revoke themselves via their own Google
          Account settings.
          Each action sits behind its own confirmation modal (the existing shared `ConfirmDialog`
          pattern - `:core:designsystem`'s `dialog/ConfirmDialog.kt`, used with
          `destructive = true`,
          see Routines' delete flow for a reference usage), and each modal's copy must make the
          distinction explicit - "Delete cloud data" specifically should say it does not sign out
          of Google or affect local data, so the two actions are never confused. This is also where
          Phase 2's cloud-data-deletion behavior (see below) surfaces in the UI.
    - Onboarding (`:feature:onboarding`) is currently a hardcoded 2-page pager (Units,
      Appearance) with separate adaptive layout composables per window posture
      (Compact/BookOrExpanded vs. Tabletop) - a third page means updating the page count and
      both layout variants, not just adding a composable.
    - Settings' (`:feature:settings`) `SettingsViewModel` is currently a pure
      preference-pass-through wrapper with no other state; the Account summary row's state
      (signed-in-as, loading, error, last-synced-at) doesn't fit that shape and needs its own
      state class. Both the Settings summary row's `SettingsViewModel` and the dedicated Account
      screen's own ViewModel (in `:feature:account`, see below) read this state via a shared
      `:core:domain` use-case (e.g. `ObserveAccountStatusUseCase`), rather than sharing a
      ViewModel instance across module boundaries.
      `docs/testing.md` currently skips onboarding/settings ViewModel tests entirely as pure
      pass-through - both the summary and the dedicated Account screen have actual branching
      logic and should get real test coverage per the ViewModel testing tier.
    - The dedicated Account screen gets its own `:feature:account` module, consistent with the
      existing one-module-per-screen convention (each screen/tab is its own Gradle module per
      `docs/architecture.md`'s module structure) - not a nav destination folded into
      `:feature:settings`. Settings only holds the compact summary row and navigates out to
      `:feature:account` for the full screen.
    - Per `CLAUDE.md`'s string/formatting conventions: all new user-facing copy (onboarding
      sign-in page text, Settings Account summary, the dedicated Account screen, confirmation
      modal copy, sign-in error/loading states) goes in `strings.xml` via `stringResource()`, not
      hardcoded. The "last synced at" instant must be exposed as a raw timestamp in UI state and
      formatted by the Composable at render time (matching the `SessionFormat`/`MeasurementFormat`
      pattern) - the ViewModel must not pre-format it into a display string itself.
- [ ] Map Room entities to Firestore collections, scoped per-user (e.g.
  `users/{uid}/workouts/{workoutId}`), using **subcollections** (not embedded arrays) for
  FK-child entities - `WorkoutExercise`/`SetEntry`/`CardioEntry` as child documents under
  `Workout`, `RoutineExercise` under `Routine`. Chosen over embedding because it sidesteps
  Firestore's 1 MiB document-size limit entirely (no realistic ceiling on workout size) and,
  more importantly, gives Phase 2's per-document last-write-wins conflict resolution finer
  granularity - a conflict discards only the losing device's edits to the one set/exercise that
  conflicted, not an entire workout's worth of edits. Costs more reads/writes per sync than one
  document per workout, but quota isn't the binding constraint at this app's data volume (numeric/
  short-string fields only, no attachments).
- [ ] Sync scope: user-authored Room data - `Exercise` rows where `isCustom = true` (built-ins
  ship with the APK and shouldn't be uploaded at all), custom `MeasurementType`s,
  `Routine`/`Workout`/etc. - **plus app preferences** (units, theme, rest-timer defaults):
  sign-in carries both over to a new device, not just workout/routine data.
    - Preferences live in DataStore, not Room, and aren't in Phase 0's entity list (no FK
      references, no UUID needed) - they need their own sync path rather than riding the
      Room-entity mechanism: a single `users/{uid}/preferences` document rather than a
      collection, since there's exactly one preferences set per user, not many rows.
    - Same conflict-resolution shape as everything else (Phase 2's last-write-wins), but
      DataStore has no existing per-write timestamp - needs the same kind of `lastModifiedAt`
      tracking added as the Room-entity change-tracking item below, just for the preferences
      document instead of a table.
- [ ] Only push completed workouts: `Workout.workoutStatus` (`IN_PROGRESS`/`IN_REST_TIME`/
  `PAUSED`/`EDITING`/`COMPLETE`) means a session can be mid-flight, backed by the Active Workout
  foreground service and its live pause/resume state - exclude anything short of `COMPLETE` from
  the sync job, since even in a push-only phase there's no reason to expose a live in-progress
  session's internal state remotely.
- [ ] Change-tracking mechanism (currently missing entirely): no entity has an `updatedAt` or
  dirty-flag column today (confirmed - none of the Room entities track modification time), so
  the periodic sync job has no way to know which local rows changed since the last push. Needs
  either a `lastModifiedAt` column added to every synced entity, or a separate dirty-row-tracking
  table populated on write - this is new schema work, likely bundled with (or immediately
  following) Phase 0's migration since it touches the same tables. (This column is also what
  Phase 2's conflict resolution will key off - see below.)
- [ ] Account-switch safety: even push-only sync writes into whichever account is currently
  authenticated - signing into account B on a device that last synced as account A, without
  clearing local data, would push A's local log into B's Firestore space. The gate belongs on
  the **push, not the sign-in** - switching Google accounts via the account picker is normal,
  expected sign-in behavior and can't sensibly be blocked or require "sign out first" as a
  precondition (choosing a different account *is* the sign-in action). So: sign-in always
  succeeds for any account; a stored last-synced-account-uid is compared on the next sync
  attempt, and if it doesn't match the newly-signed-in account, show a one-time warning
  ("this device last synced as A, you're signed in as B - continuing syncs local data under B
  instead") before the first push under the new account is allowed to proceed.
- [ ] Sync trigger: periodic, not write-through-per-mutation - a background job (e.g.
  WorkManager) batches unsynced local changes on a schedule / app-foreground event rather than
  pushing to Firestore on every Room write, since batching is cheaper on the daily quota.
  WorkManager's `PeriodicWorkRequest` has a 15-minute minimum interval, which caps how frequent
  "periodic" can actually be. Constrained with `NetworkType.CONNECTED` (don't even attempt a run
  offline) and `BackoffPolicy.EXPONENTIAL` on failure (WorkManager's built-in retry, not custom
  retry logic).
    - Failure visibility needs a dedicated state, not just a timestamp that silently goes stale -
      see the Settings/Account UI split above: a "last sync error" (auth expired / offline / quota
      exceeded) is tracked separately from "last synced at," so the UI can always distinguish
      "never synced" from "last attempt failed" rather than leaving the user unable to tell.
      Auth-expiry specifically should prompt re-sign-in as the fix, not fail silently.
- [ ] Test strategy splits by what's actually being tested, rather than standing up a Firebase
  Local Emulator Suite (no CI pipeline exists here to make that pay for itself):
    - **Business logic** (the LWW comparator, cascade-tombstone enumeration, retry/backoff
      decision, account-mismatch check) is pure and Firestore-agnostic - covered by plain unit
      tests with fakes, no network or emulator involved, per `docs/testing.md`'s existing bar
      that real branching logic gets real coverage.
    - **Actual Firestore round-trips** (a push really lands, a real conflict resolves as
      expected) are verified manually against the real project on the Android (AVD) emulator
      with a real Google account, via a written action script - matching the existing
      verification workflow for this project rather than adding automated integration tests
      against a fake backend.

---

## Phase 2: pull, merge, and delete propagation (full two-way sync)

Only start once Phase 1 has been running clean in production for a while - this phase is where
incoming data can actually touch local state, so it's the phase that needs the most confidence
going in.

- [ ] Migration path for sign-in: not a binary "download if returning, nothing if local-only" -
  Android's Auto Backup (already enabled, see below) restores the local Room DB automatically on
  a new device/reinstall *before* the user ever reaches the sign-in screen, so local Room can
  never be assumed empty at sign-in time. Every sign-in is a merge of two potentially non-empty,
  potentially divergent datasets (Auto-Backup-restored local state vs. this account's Firestore
  state, which may be older or newer depending on when the last periodic sync vs. the last Auto
  Backup snapshot ran) - handle it via the same per-document last-write-wins conflict resolution
  used elsewhere, not a separate first-sign-in-only code path.
- [ ] Conflict resolution: last-write-wins per-document, no custom merge logic - acceptable
  because conflicts should only arise from switching primary device with unsynced local edits
  still pending, not simultaneous concurrent use. Compares by each entity's own `lastModifiedAt`
  (the Phase 1 change-tracking column), stored as document data - **not** Firestore's server
  write-timestamp metadata, since periodic batched sync decouples when an edit happened from
  when it synced (a genuinely older edit made while offline could sync later than a genuinely
  newer one, and write-time LWW would pick the wrong winner). Document-level granularity (one
  workout, one routine, etc.) means a conflict discards only the losing device's edits to that
  specific record, not unrelated data, but it does so silently with no conflict UI or prompt.
- [ ] Delete propagation (currently missing entirely): every core DAO
  (`ExerciseDao`/`RoutineDao`/`MeasurementDao`/`WorkoutDao`) does a hard `@Delete` today - once a
  row is gone from Room there's no trace it ever existed, so the sync job can't tell "this was
  deleted locally, delete the Firestore copy too." Needs a tombstone (a pending-deletion record,
  e.g. entity type + old ID, kept until the next sync confirms the remote doc is removed, then
  cleared) rather than relying on the row's absence. Watch out for `onDelete = CASCADE`
  (used by `Routine`→`RoutineExercise`, `Workout`→`WorkoutExercise`→`SetEntry`/`CardioEntry`,
  etc.) - SQLite removes cascaded child rows at the engine level, invisible to whatever DAO call
  triggered the parent delete, so a tombstone recorded only for the explicitly-deleted parent
  row would leave every cascade-deleted descendant's Firestore document orphaned. Cascade
  victims need to be enumerated (query children before issuing the parent delete) and
  tombstoned too, not just the one row the DAO call touched. Full current cascade set:
  `Routine`→`RoutineExercise`, `Workout`→`WorkoutExercise`→(`SetEntry`, `CardioEntry`),
  `RoutineExercise`→`Exercise`, `WorkoutExercise`→`Exercise`, `MeasurementType`→`BodyMetric`.
- [ ] Room stays the local source of truth; the periodic sync layer bridges to Firestore rather
  than using Firestore's live-listener model, since no real-time/multi-device convergence is
  needed.
- [ ] Pull trigger: same shape as Phase 1's push trigger (periodic WorkManager job, `NetworkType.
  CONNECTED`, exponential backoff) - **plus an app-foreground-triggered pull**, not periodic
  only. Given ongoing casual dual-device rotation is a real use case (see the cross-cutting note
  below), opening the app on device B should immediately attempt a pull of whatever device A
  pushed, rather than waiting up to the 15-minute periodic floor - this is what keeps switching
  devices feeling responsive without needing Firestore's live-listener model.
- [ ] Firestore document schema evolution (unlike Room, there's no formal `Migration` mechanism
  for a schemaless store): additive changes are the default and need no migration step - new
  fields are read with a default/fallback when absent, handled defensively in the Firestore
  document ↔ domain model mapper layer, since an old document simply won't have the field yet.
  Renames/breaking shape changes are the risky case (an old app version could still push the old
  shape while a new version writes the new one) but low-probability given single-device use, not
  concurrent app versions - the mapper should still fail closed on an unrecognized shape
  (ignore/default the field) rather than crash. No dedicated schema-version field or migration
  step planned; this is a mapper-layer convention, not new infrastructure.
- [ ] Manual account/data deletion is two distinct, clearly-separated actions on the dedicated
  Account screen (Phase 1) - not one ambiguous "delete account," since there's no separate
  Regimen account to delete: **sign-out** keeps local data and the cloud backup, just stops
  syncing; **delete cloud data** wipes every Firestore document under `users/{uid}/**` plus the
  Firebase Auth user record, but does not revoke the Google OAuth grant (the user does that
  themselves via Google Account settings) and does not touch local data. Each confirmation
  modal's copy must state plainly what that specific action does and does not do, so the two are
  never confused with each other.

---

## Cross-cutting notes

**Auto Backup is not a substitute for Phase 1/2 - they solve different problems.**
`AndroidManifest.xml`
already declares `android:allowBackup="true"` with `dataExtractionRules`/`fullBackupContent`
configured, so disaster-recovery for **logged-out, local-only users** ("don't lose my data if I
lose/reset my phone") is already covered today, with zero Firestore work. But this doc's actual
goal - sync for **signed-in users** - is a different problem Auto Backup structurally cannot
solve:

- Auto Backup only **restores at app install / device setup time** - it cannot pull-merge into
  an already-running app on a second device. It can't do "install Regimen on a phone and a
  tablet, sign into the same account, keep both current" - only Phase 2's periodic pull/merge
  can (eventually-consistent, not real-time - see the multi-device note below).
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
server-side (conflict resolution, delete/tombstone handling, document mapping) is planned as
client-side logic elsewhere in this doc, and there's no requirement here (server-side
aggregation, multi-user sharing, webhooks) that would need a separate custom backend. A custom
backend would only re-enter the picture if the app's scope itself changed (multi-user/social
features, server-computed analytics) - a new decision outside this doc, not a gap in it.

**Multi-device use:** ongoing casual rotation between two devices (e.g. phone + tablet) **is**
a real use case, not just one-shot reinstall/new-device recovery - a user should be able to log
a workout on one device, pick up the other later the same day, and see it reflected without
manually forcing anything. What's still explicitly out of scope is *simultaneous* concurrent
use of two devices at once (live listeners, real-time conflict resolution for edits happening
on both devices in the same moment) - periodic, eventually-consistent sync is the right shape
for "used one at a time, switched between," just not for "both active right now." See Phase 2's
foreground-triggered pull item above for how this stays reasonably responsive without needing
real-time infrastructure.

**Quota scope:** Spark tier's free limits (1 GiB storage, 50K reads / 20K writes / 20K
deletes per day, 10 GiB egress/month) are pooled per Firebase *project*, not per user -
comfortable for personal use; would need re-evaluating if this app ever had many concurrent
users sharing one project. Exceeding a daily quota fails further reads/writes/deletes until
the daily reset (no silent auto-billing, since Spark has no billing account attached); lifting
the cap requires manually moving the project to the Blaze plan.
