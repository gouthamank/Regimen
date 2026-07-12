package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import dev.gouthaman.regimen.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
) : MeasurementRepository {
    override fun observeTypes(): Flow<List<MeasurementType>> =
        dao.observeTypes().map { list -> list.map { it.toDomain() } }

    override fun observeMetrics(typeId: Long): Flow<List<BodyMetric>> =
        dao.observeMetricsForType(typeId).map { list -> list.map { it.toDomain() } }

    override fun observeLatest(typeId: Long): Flow<BodyMetric?> =
        dao.observeLatestForType(typeId).map { it?.toDomain() }

    override suspend fun addType(name: String, unit: String): Long =
        dao.insertType(
            MeasurementTypeEntity(
                name = name.trim(),
                unit = unit.trim(),
                isBuiltIn = false
            )
        )

    override suspend fun deleteType(type: MeasurementType) = dao.deleteType(type.toEntity())

    override suspend fun addMetric(typeId: Long, date: Long, value: Double): Long =
        dao.insertMetric(BodyMetricEntity(measurementTypeId = typeId, date = date, value = value))

    override suspend fun deleteMetric(metric: BodyMetric) = dao.deleteMetric(metric.toEntity())
}
