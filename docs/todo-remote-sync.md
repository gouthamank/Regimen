# Remote sync (future - not started)

Regimen is local-only today (see `docs/architecture.md`); this doc tracks two pieces of
groundwork discussed for eventual multi-device sync, kept separate until either is actually
picked up. Not scheduled - revisit before starting either.

## Status legend

- `[x]` done and verified
- `[~]` in progress
- `[ ]` not started

---

## Option A: UUID primary-key migration

Prerequisite for *any* real backend sync (custom server or Firestore) - autoincrement `Long`
IDs collide across devices, since two offline devices can independently generate the same next
ID. Worth doing before a backend exists, since it's a local-only schema change either way.

- [ ] Swap every entity's `@PrimaryKey`/FK column from `Long` to a client-generated `String`
  UUID (`java.util.UUID.randomUUID().toString()`), generated at creation time rather than
  relying on Room's autoincrement-and-read-back.
    - Entities: `Exercise`, `Routine`, `RoutineExercise`, `Workout`, `WorkoutExercise`,
      `SetEntry`, `CardioEntry`, `MeasurementType`, `BodyMetric`.
- [ ] Update `:core:domain` models and repository interfaces to match (`id: String` instead of
  `id: Long`).
- [ ] Update nav route args (`:app`'s `Destinations`, each feature's route types) and any
  ViewModel taking an ID param.
- [ ] Write the Room `Migration` for existing installs:
    - Create new UUID-keyed tables.
    - Walk existing rows in FK dependency order (parents first: `Exercise`/`Routine`/
      `MeasurementType`, then `RoutineExercise`/`Workout`/`BodyMetric`, then `WorkoutExercise`,
      then `SetEntry`/`CardioEntry`).
    - Generate a UUID per old row, maintain an old-`Long`-ID → new-UUID map per entity type, and
      remap every FK column using that map before dropping the old tables.
  - Test against a real pre-migration DB snapshot, not just a fresh install - remap-order bugs
      silently corrupt relationships rather than crashing.
- [ ] Bump `Database version` past the current 5 and note it in `docs/architecture.md`'s data
  model section once done.

**Scope estimate:** multi-day, wide-but-mechanical (touches every module in the ID path) but
low design risk - the only genuinely delicate part is the migration's FK remapping.

---

## Option B: Firestore + Firebase Auth (Google Sign-In)

The "no backend to build or run" path to true multi-device sync, evaluated as a much lower-effort
alternative to a custom server + hand-rolled delta-sync protocol. Assumes Option A (UUID keys)
is done first, since Firestore document IDs need the same global-uniqueness property.

- [ ] Firebase Auth wired to Google Sign-In (Android sign-in flow → Firebase credential
  exchange). No cost for standard OAuth providers.
- [ ] Map Room entities to Firestore collections, scoped per-user (e.g.
  `users/{uid}/workouts/{workoutId}`).
- [ ] Decide the sync direction/trigger: Firestore's offline cache + listener model handles most
  of what a hand-rolled delta-sync engine would need (local cache, background sync, reconnect
  handling) - confirm this covers Regimen's actual read/write patterns before assuming it's a
  full replacement for Room, or whether Room stays the local source of truth with a sync layer
  bridging to Firestore.
- [ ] Conflict resolution: Firestore's default last-write-wins per-document is probably
  sufficient for a single-user log; confirm before relying on it silently.
- [ ] Migration path for existing local-only users: one-time upload of local Room data into
  Firestore on first sign-in.

**Cost:** free at personal-use scale. Firebase Auth is free for Google Sign-In. Firestore's
Spark (free) tier - 1 GiB storage, 50K reads/day, 20K writes/day, 20K deletes/day, 10 GiB
egress/month, no credit card required - comfortably covers a single-user (or small handful of
users) workout log. Only exceeding those quotas (real multi-user scale) would require the
pay-as-you-go Blaze plan.

**Open question:** whether this fully replaces the "build a custom backend" idea, or is only
the auth/sync transport with a custom backend still doing something else. Revisit once actual
sync requirements (multi-device simultaneous use? sharing data between users? none of the
above, just disaster recovery?) are clearer - if it's just disaster recovery, Android's Auto
Backup for Apps (see `docs/architecture.md`'s Settings section - data export is deferred) may
be sufficient without any of this.
