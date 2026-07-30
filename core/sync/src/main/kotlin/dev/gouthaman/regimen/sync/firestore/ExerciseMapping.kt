package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.ExerciseEntity

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
