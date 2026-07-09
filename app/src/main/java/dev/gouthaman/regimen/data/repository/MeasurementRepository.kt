package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeasurementRepository @Inject constructor(
    private val dao: MeasurementDao,
) {
    fun observeTypes(): Flow<List<MeasurementType>> = dao.observeTypes()
    fun observeMetrics(typeId: Long): Flow<List<BodyMetric>> = dao.observeMetricsForType(typeId)
    fun observeLatest(typeId: Long): Flow<BodyMetric?> = dao.observeLatestForType(typeId)

    suspend fun addType(name: String, unit: String): Long =
        dao.insertType(MeasurementType(name = name.trim(), unit = unit.trim(), isBuiltIn = false))

    suspend fun deleteType(type: MeasurementType) = dao.deleteType(type)

    suspend fun addMetric(typeId: Long, date: Long, value: Double): Long =
        dao.insertMetric(BodyMetric(measurementTypeId = typeId, date = date, value = value))

    suspend fun deleteMetric(metric: BodyMetric) = dao.deleteMetric(metric)
}
