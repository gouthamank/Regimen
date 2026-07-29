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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
) : MeasurementRepository {
    override fun observeTypes(): Flow<List<MeasurementType>> =
        dao.observeTypes().map { list -> list.map { it.toDomain() } }

    override fun observeMetrics(typeId: String): Flow<List<BodyMetric>> =
        dao.observeMetricsForType(typeId).map { list -> list.map { it.toDomain() } }

    override fun observeLatest(typeId: String): Flow<BodyMetric?> =
        dao.observeLatestForType(typeId).map { it?.toDomain() }

    override suspend fun addType(name: String, unit: String): String {
        val id = UUID.randomUUID().toString()
        dao.insertType(
            MeasurementTypeEntity(
                id = id,
                name = name.trim(),
                unit = unit.trim(),
                isBuiltIn = false
            )
        )
        return id
    }

    override suspend fun deleteType(type: MeasurementType) = dao.deleteType(type.toEntity())

    override suspend fun addMetric(typeId: String, date: Long, value: Double): String {
        val id = UUID.randomUUID().toString()
        dao.insertMetric(
            BodyMetricEntity(id = id, measurementTypeId = typeId, date = date, value = value)
        )
        return id
    }

    override suspend fun deleteMetric(metric: BodyMetric) = dao.deleteMetric(metric.toEntity())
}
