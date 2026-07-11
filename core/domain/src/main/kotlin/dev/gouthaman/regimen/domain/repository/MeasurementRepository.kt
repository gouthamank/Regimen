package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    fun observeTypes(): Flow<List<MeasurementType>>
    fun observeMetrics(typeId: Long): Flow<List<BodyMetric>>
    fun observeLatest(typeId: Long): Flow<BodyMetric?>

    suspend fun addType(name: String, unit: String): Long
    suspend fun deleteType(type: MeasurementType)

    suspend fun addMetric(typeId: Long, date: Long, value: Double): Long
    suspend fun deleteMetric(metric: BodyMetric)
}
