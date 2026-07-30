package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity

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

/** Firestore shape for `users/{uid}/workouts/{workoutId}/workoutExercises/{weId}` - `workoutId`
 * itself is omitted, since it's already the parent document's own path. */
data class WorkoutExerciseDto(
    val exerciseId: String = "",
    val position: Int = 0,
    val isSkipped: Boolean = false,
    val isDone: Boolean = false,
    val supersetGroupId: String? = null,
    val lastModifiedAt: Long = 0,
)

fun WorkoutExerciseEntity.toDto(): WorkoutExerciseDto = WorkoutExerciseDto(
    exerciseId = exerciseId,
    position = position,
    isSkipped = isSkipped,
    isDone = isDone,
    supersetGroupId = supersetGroupId,
    lastModifiedAt = lastModifiedAt,
)

/** Firestore shape for `.../workoutExercises/{weId}/setEntries/{id}` - `workoutExerciseId` itself
 * is omitted, since it's already the parent document's own path. */
data class SetEntryDto(
    val setNumber: Int = 0,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isComplete: Boolean = false,
    val lastModifiedAt: Long = 0,
)

fun SetEntryEntity.toDto(): SetEntryDto = SetEntryDto(
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isComplete = isComplete,
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
