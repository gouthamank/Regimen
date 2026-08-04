package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.domain.model.WorkoutEndReason
import dev.gouthaman.regimen.domain.model.WorkoutStatus

/** Firestore shape for `users/{uid}/workouts/{workoutId}` - only `workoutStatus == COMPLETE` rows
 * are ever mapped, but `workoutStatus` (and the rest/pause fields, always null/zero for a
 * completed workout) are still mirrored rather than omitted, so a document is a complete,
 * self-sufficient record on its own rather than relying on the reader knowing the scope filter
 * that was applied when it was written. */
data class WorkoutDto(
    val startTime: Long = 0,
    val endTime: Long? = null,
    val note: String? = null,
    val routineId: String? = null,
    val workoutStatus: String = "",
    val endReason: String? = null,
    val pausedAt: Long? = null,
    val accumulatedPausedMs: Long = 0,
    val restTimeEndAt: Long? = null,
    val restTotalSec: Int? = null,
    val restWorkoutExerciseId: String? = null,
    val lastModifiedAt: Long = 0,
)

fun WorkoutEntity.toDto(): WorkoutDto = WorkoutDto(
    startTime = startTime,
    endTime = endTime,
    note = note,
    routineId = routineId,
    workoutStatus = workoutStatus.name,
    endReason = endReason?.name,
    pausedAt = pausedAt,
    accumulatedPausedMs = accumulatedPausedMs,
    restTimeEndAt = restTimeEndAt,
    restTotalSec = restTotalSec,
    restWorkoutExerciseId = restWorkoutExerciseId,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto]. `workoutStatus` falls back to `COMPLETE` on an
 * unrecognized value, consistent with every document in this collection already being `COMPLETE`
 * by construction. `endReason` falls back to `MANUAL` - a non-null string means it ended for some
 * reason this app version doesn't recognize yet, and "the user finished it" is the safer guess. */
fun WorkoutDto.toEntity(id: String): WorkoutEntity = WorkoutEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    note = note,
    routineId = routineId,
    workoutStatus = parseEnumOrDefault(workoutStatus, WorkoutStatus.COMPLETE),
    endReason = endReason?.let { parseEnumOrDefault(it, WorkoutEndReason.MANUAL) },
    pausedAt = pausedAt,
    accumulatedPausedMs = accumulatedPausedMs,
    restTimeEndAt = restTimeEndAt,
    restTotalSec = restTotalSec,
    restWorkoutExerciseId = restWorkoutExerciseId,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)

/** Firestore shape for `users/{uid}/workouts/{workoutId}/workoutExercises/{weId}` - `workoutId`
 * itself is omitted, since it's already the parent document's own path. */
/** `skipped`/`done`, not `isSkipped`/`isDone` - Kotlin's Java-bean-style getter for an
 * `is`-prefixed `Boolean` (`isSkipped()`) gets its `is` stripped by Firestore's serializer when
 * deriving the document field name, but `toObject()`'s data-class deserialization matches document
 * fields against constructor parameter names literally - so a round trip through Firestore would
 * silently reset every `is`-prefixed boolean back to its default. Avoiding the `is` prefix here
 * sidesteps the mismatch entirely. */
data class WorkoutExerciseDto(
    val exerciseId: String = "",
    val position: Int = 0,
    val skipped: Boolean = false,
    val done: Boolean = false,
    val supersetGroupId: String? = null,
    val notes: String? = null,
    val lastModifiedAt: Long = 0,
)

fun WorkoutExerciseEntity.toDto(): WorkoutExerciseDto = WorkoutExerciseDto(
    exerciseId = exerciseId,
    position = position,
    skipped = isSkipped,
    done = isDone,
    supersetGroupId = supersetGroupId,
    notes = notes,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto] - [workoutId] comes from the parent document's own
 * path, same reasoning as [id]. */
fun WorkoutExerciseDto.toEntity(id: String, workoutId: String): WorkoutExerciseEntity =
    WorkoutExerciseEntity(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        position = position,
        isSkipped = skipped,
        isDone = done,
        supersetGroupId = supersetGroupId,
        notes = notes,
        isDirty = false,
        lastModifiedAt = lastModifiedAt,
    )

/** Firestore shape for `.../workoutExercises/{weId}/setEntries/{id}` - `workoutExerciseId` itself
 * is omitted, since it's already the parent document's own path. `complete`, not `isComplete` -
 * see [WorkoutExerciseDto]'s doc for why an `is`-prefixed Boolean silently loses its value across
 * a Firestore round trip. */
data class SetEntryDto(
    val setNumber: Int = 0,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val complete: Boolean = false,
    val lastModifiedAt: Long = 0,
)

fun SetEntryEntity.toDto(): SetEntryDto = SetEntryDto(
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    complete = isComplete,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto] - [workoutExerciseId] comes from the parent document's
 * own path, same reasoning as [id]. */
fun SetEntryDto.toEntity(id: String, workoutExerciseId: String): SetEntryEntity = SetEntryEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isComplete = complete,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)

/** Firestore shape for `.../workoutExercises/{weId}/cardioEntries/{id}` - `workoutExerciseId`
 * itself is omitted, since it's already the parent document's own path. */
data class CardioEntryDto(
    val durationSec: Long = 0,
    val distanceMeters: Double? = null,
    val lastModifiedAt: Long = 0,
)

fun CardioEntryEntity.toDto(): CardioEntryDto = CardioEntryDto(
    durationSec = durationSec,
    distanceMeters = distanceMeters,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto] - [workoutExerciseId] comes from the parent document's
 * own path, same reasoning as [id]. */
fun CardioEntryDto.toEntity(id: String, workoutExerciseId: String): CardioEntryEntity =
    CardioEntryEntity(
        id = id,
        workoutExerciseId = workoutExerciseId,
        durationSec = durationSec,
        distanceMeters = distanceMeters,
        isDirty = false,
        lastModifiedAt = lastModifiedAt,
    )
