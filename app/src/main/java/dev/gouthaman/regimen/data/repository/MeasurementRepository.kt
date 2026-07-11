package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepository @Inject constructor(
    private val dao: MeasurementDao,
) {
    fun observeTypes(): Flow<List<MeasurementType>> =
        dao.observeTypes().map { list -> list.map { it.toDomain() } }

    fun observeMetrics(typeId: Long): Flow<List<BodyMetric>> =
        dao.observeMetricsForType(typeId).map { list -> list.map { it.toDomain() } }

    fun observeLatest(typeId: Long): Flow<BodyMetric?> =
        dao.observeLatestForType(typeId).map { it?.toDomain() }

    suspend fun addType(name: String, unit: String): Long =
        dao.insertType(
            MeasurementTypeEntity(
                name = name.trim(),
                unit = unit.trim(),
                isBuiltIn = false
            )
        )

    suspend fun deleteType(type: MeasurementType) = dao.deleteType(type.toEntity())

    suspend fun addMetric(typeId: Long, date: Long, value: Double): Long =
        dao.insertMetric(BodyMetricEntity(measurementTypeId = typeId, date = date, value = value))

    suspend fun deleteMetric(metric: BodyMetric) = dao.deleteMetric(metric.toEntity())
}
