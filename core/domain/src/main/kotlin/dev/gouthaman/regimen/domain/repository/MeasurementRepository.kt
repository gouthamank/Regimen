package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import kotlinx.coroutines.flow.Flow

interface MeasurementRepository {
    fun observeTypes(): Flow<List<MeasurementType>>
    fun observeMetrics(typeId: String): Flow<List<BodyMetric>>
    fun observeLatest(typeId: String): Flow<BodyMetric?>

    suspend fun addType(name: String, unit: String): String
    suspend fun deleteType(type: MeasurementType)

    suspend fun addMetric(typeId: String, date: Long, value: Double): String
    suspend fun deleteMetric(metric: BodyMetric)
}
