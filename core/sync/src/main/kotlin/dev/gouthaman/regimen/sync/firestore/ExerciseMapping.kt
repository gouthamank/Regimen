package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** Firestore shape for `users/{uid}/exercises/{exerciseId}` - only `isCustom == true` rows are
 * ever mapped (built-ins ship with the APK, out of sync scope entirely). Mirrors [ExerciseEntity]
 * exactly except `id` (the document's own path) and `isDirty` (local-only, never leaves the
 * device) - every property needs a default for Firestore's reflection-based POJO mapping.
 * `custom`, not `isCustom` - see `WorkoutExerciseDto`'s doc (WorkoutMapping.kt) for why an
 * `is`-prefixed Boolean silently loses its value across a Firestore round trip. */
data class ExerciseDto(
    val name: String = "",
    val type: String = "",
    val muscleGroup: String = "",
    val equipment: String = "",
    val custom: Boolean = true,
    val lastModifiedAt: Long = 0,
)

fun ExerciseEntity.toDto(): ExerciseDto = ExerciseDto(
    name = name,
    type = type.name,
    muscleGroup = muscleGroup.name,
    equipment = equipment.name,
    custom = isCustom,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto] - [id] comes from the document's own path, [isDirty]
 * is always `false` since a freshly-pulled row already matches the cloud exactly. Enum fields
 * fail closed via [parseEnumOrDefault] rather than throwing on an unrecognized value - see that
 * function's own doc. */
fun ExerciseDto.toEntity(id: String): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    type = parseEnumOrDefault(type, ExerciseType.STRENGTH),
    muscleGroup = parseEnumOrDefault(muscleGroup, MuscleGroup.OTHER),
    equipment = parseEnumOrDefault(equipment, Equipment.OTHER),
    isCustom = custom,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)
