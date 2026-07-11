package dev.gouthaman.regimen.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType

/** A body-measurement type. "Bodyweight" is built-in; users add custom types. */
@Entity(tableName = "measurement_types")
data class MeasurementTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val isBuiltIn: Boolean = false,
)

fun MeasurementTypeEntity.toDomain(): MeasurementType =
    MeasurementType(id = id, name = name, unit = unit, isBuiltIn = isBuiltIn)

fun MeasurementType.toEntity(): MeasurementTypeEntity =
    MeasurementTypeEntity(id = id, name = name, unit = unit, isBuiltIn = isBuiltIn)

@Entity(
    tableName = "body_metrics",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementTypeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("measurementTypeId")],
)
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementTypeId: Long,
    val date: Long,
    val value: Double,
)

fun BodyMetricEntity.toDomain(): BodyMetric =
    BodyMetric(id = id, measurementTypeId = measurementTypeId, date = date, value = value)

fun BodyMetric.toEntity(): BodyMetricEntity =
    BodyMetricEntity(id = id, measurementTypeId = measurementTypeId, date = date, value = value)

/** A body metric with its measurement type resolved. Currently unused (no DAO query returns it) —
 * kept as a Room-only type with no domain mirror since nothing consumes it. */
data class BodyMetricWithTypeEntity(
    @Embedded val metric: BodyMetricEntity,
    @Relation(parentColumn = "measurementTypeId", entityColumn = "id")
    val type: MeasurementTypeEntity,
)
