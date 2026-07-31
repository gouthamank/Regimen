package dev.gouthaman.regimen.sync.firestore

import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity

/** Firestore shape for `users/{uid}/measurementTypes/{typeId}` - only `isBuiltIn == false` rows
 * are ever mapped (the built-in Bodyweight type ships with the APK, out of sync scope entirely).
 * `builtIn`, not `isBuiltIn` - see `WorkoutExerciseDto`'s doc (WorkoutMapping.kt) for why an
 * `is`-prefixed Boolean silently loses its value across a Firestore round trip. */
data class MeasurementTypeDto(
    val name: String = "",
    val unit: String = "",
    val builtIn: Boolean = false,
    val lastModifiedAt: Long = 0,
)

fun MeasurementTypeEntity.toDto(): MeasurementTypeDto = MeasurementTypeDto(
    name = name,
    unit = unit,
    builtIn = isBuiltIn,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto]. */
fun MeasurementTypeDto.toEntity(id: String): MeasurementTypeEntity = MeasurementTypeEntity(
    id = id,
    name = name,
    unit = unit,
    isBuiltIn = builtIn,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)

/** Firestore shape for `users/{uid}/bodyMetrics/{metricId}`. */
data class BodyMetricDto(
    val measurementTypeId: String = "",
    val date: Long = 0,
    val value: Double = 0.0,
    val lastModifiedAt: Long = 0,
)

fun BodyMetricEntity.toDto(): BodyMetricDto = BodyMetricDto(
    measurementTypeId = measurementTypeId,
    date = date,
    value = value,
    lastModifiedAt = lastModifiedAt,
)

/** "Pull cloud data"'s reverse of [toDto]. */
fun BodyMetricDto.toEntity(id: String): BodyMetricEntity = BodyMetricEntity(
    id = id,
    measurementTypeId = measurementTypeId,
    date = date,
    value = value,
    isDirty = false,
    lastModifiedAt = lastModifiedAt,
)
