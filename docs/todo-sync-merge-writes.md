# Firestore push: switch to merge writes (not started)

Regimen's optional cloud sync (Firebase Auth + Firestore, see `docs/architecture.md`'s "Remote
sync" section) uses a **single-writer model**: exactly one signed-in device per account (the
primary) pushes local changes to Firestore; every other device only pulls or explicitly takes
over as primary. There is no merge/CRDT reconciliation between devices - each entity's push is a
whole-document replace.

## The problem

Every `write` lambda in `:core:sync`'s `push/SyncPushRunner.kt` (all 9 synced entity types, plus
the single preferences document) does:

```kotlin
path.set(entity.toDto()).await()
```

- a full document replace, not `SetOptions.merge()`. Firestore itself needs no migration for an
  additive `Dto` field (schemaless, and `toObject()` ignores unknown fields when reading), but the
  whole-document `.set()` has a real cross-version write hazard: discovered while adding
  `WorkoutExercise.notes`.

- **Old device reads new data, or new device reads old data**: safe either way.
  `toObject()` ignores any field in a document that the reading app's DTO class doesn't declare,
  and a field missing from the document just deserializes as `null` for a nullable property - no
  crash, no corruption.
- **Old device *writes* after a new device already set a new field - not safe.** If a newer app
  version writes a note (or any future-added field) to a `workoutExercises` document, then an
  older, not-yet-updated app version on a different signed-in device later re-pushes that same
  document for an unrelated reason (toggling skip/done, completing a set - anything that flips
  `isDirty`), its DTO class has no `notes` field at all, so the resulting `.set()` silently erases
  it. That wipe then propagates to every other device on its next pull.

This only bites when multiple devices on the same account are running different app versions
concurrently (e.g. phone updated, tablet hasn't yet) - but the failure mode is silent data loss,
not a visible error.

## Considered fix

Switch every `write` lambda's `.set(dto)` to `.set(dto, SetOptions.merge())`
(`com.google.firebase.firestore.SetOptions` is already imported/used in `SyncPushRunner.kt` for
the `syncConfig` lock/watermark writes) - uniform, ~10 call sites, no redesign of the push
mechanism itself.

Confirmed this doesn't break any existing "clear a field back to null" case (e.g.
`Workout.restTimeEndAt = null` when a rest countdown ends) - those fields are still *present* in
the DTO with an explicit null value, and merge writes whatever fields are present, nulls included.
Merge only skips fields that are entirely *absent* from the payload, which only happens for a
field a given app version's DTO class doesn't know about yet - exactly the case this is meant to
fix.

## Trade-off if adopted

A full `.set()` replace also currently means *removing* a field from a `Dto` class in some future
version cleans that field out of existing Firestore documents for free, on next push. Switching to
merge would leave a removed field's old value sitting in Firestore forever (zombie data) unless
something explicitly deletes it (`FieldValue.delete()`). Not a problem today - no synced field has
ever been removed - but a real ongoing cost of the merge approach worth remembering if that ever
comes up.

## Test coverage

No existing test exercises `SyncPushRunner`'s actual Firestore write semantics either way -
`PushDirtyBatchTest` only covers the generic dirty/budget/clear loop via fake lambdas, not the real
`.set()` call shape. This change would carry the same manual-verification bar as the rest of the
push job (per `docs/architecture.md`'s "Schema evolution & testing" section).

## Status

- [ ] Not started.
