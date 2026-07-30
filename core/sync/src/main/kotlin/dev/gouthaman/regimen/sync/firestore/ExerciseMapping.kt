package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** Firestore shape for `users/{uid}/exercises/{exerciseId}` - only `isCustom == true` rows are
 * ever mapped (built-ins ship with the APK, out of sync scope entirely). Mirrors [ExerciseEntity]
 * exactly except `id` (the document's own path) and `isDirty` (local-only, never leaves the
 * device) - every property needs a default for Firestore's reflection-based POJO mapping. */
data class ExerciseDto(
    val name: String = "",
    val type: String = "",
    val muscleGroup: String = "",
    val equipment: String = "",
    val isCustom: Boolean = true,
    val lastModifiedAt: Long = 0,
)

fun ExerciseEntity.toDto(): ExerciseDto = ExerciseDto(
    name = name,
    type = type.name,
    muscleGroup = muscleGroup.name,
    equipment = equipment.name,
    isCustom = isCustom,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto] - [id] comes from the document's own path, [isDirty]
 * is always `false` since a freshly-pulled row already matches the cloud exactly. */
fun ExerciseDto.toEntity(id: String): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    type = ExerciseType.valueOf(type),
    muscleGroup = MuscleGroup.valueOf(muscleGroup),
    equipment = Equipment.valueOf(equipment),
    isCustom = isCustom,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)
