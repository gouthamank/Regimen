package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity

/** Firestore shape for `users/{uid}/routines/{routineId}`. */
data class RoutineDto(
    val name: String = "",
    val position: Int = 0,
    val lastModifiedAt: Long = 0,
)

fun RoutineEntity.toDto(): RoutineDto = RoutineDto(
    name = name,
    position = position,
    lastModifiedAt = lastModifiedAt,
)

/** Firestore shape for `users/{uid}/routines/{routineId}/routineExercises/{id}` - `routineId`
 * itself is omitted, since it's already the parent document's own path. */
data class RoutineExerciseDto(
    val exerciseId: String = "",
    val position: Int = 0,
    val targetSets: Int = 0,
    val targetReps: Int = 0,
    val targetRestSec: Int = 0,
    val supersetGroupId: String? = null,
    val lastModifiedAt: Long = 0,
)

fun RoutineExerciseEntity.toDto(): RoutineExerciseDto = RoutineExerciseDto(
    exerciseId = exerciseId,
    position = position,
    targetSets = targetSets,
    targetReps = targetReps,
    targetRestSec = targetRestSec,
    supersetGroupId = supersetGroupId,
    lastModifiedAt = lastModifiedAt,
)
