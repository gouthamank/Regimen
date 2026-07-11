package dev.gouthaman.regimen.domain.model

/** A body-measurement type. "Bodyweight" is built-in; users add custom types. */
data class MeasurementType(
    val id: Long = 0,
    val name: String,
    val unit: String,
    val isBuiltIn: Boolean = false,
)

data class BodyMetric(
    val id: Long = 0,
    val measurementTypeId: Long,
    val date: Long,
    val value: Double,
)
