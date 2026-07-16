package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import dev.gouthaman.regimen.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMeasurementRepository : MeasurementRepository {

    private val types = MutableStateFlow<List<MeasurementType>>(emptyList())
    private val metrics = MutableStateFlow<List<BodyMetric>>(emptyList())
    private var nextTypeId = 1L
    private var nextMetricId = 1L

    override fun observeTypes(): Flow<List<MeasurementType>> = types

    override fun observeMetrics(typeId: Long): Flow<List<BodyMetric>> =
        metrics.map { list -> list.filter { it.measurementTypeId == typeId }.sortedBy { it.date } }

    override fun observeLatest(typeId: Long): Flow<BodyMetric?> =
        metrics.map { list ->
            list.filter { it.measurementTypeId == typeId }.maxByOrNull { it.date }
        }

    override suspend fun addType(name: String, unit: String): Long {
        val id = nextTypeId++
        types.value = types.value + MeasurementType(id = id, name = name, unit = unit)
        return id
    }

    override suspend fun deleteType(type: MeasurementType) {
        types.value = types.value.filterNot { it.id == type.id }
        metrics.value = metrics.value.filterNot { it.measurementTypeId == type.id }
    }

    override suspend fun addMetric(typeId: Long, date: Long, value: Double): Long {
        val id = nextMetricId++
        metrics.value = metrics.value + BodyMetric(
            id = id,
            measurementTypeId = typeId,
            date = date,
            value = value
        )
        return id
    }

    override suspend fun deleteMetric(metric: BodyMetric) {
        metrics.value = metrics.value.filterNot { it.id == metric.id }
    }

    fun seedTypes(vararg seeded: MeasurementType) {
        types.value = seeded.toList()
        nextTypeId = (seeded.maxOfOrNull { it.id } ?: 0) + 1
    }

    fun seedMetrics(vararg seeded: BodyMetric) {
        metrics.value = seeded.toList()
        nextMetricId = (seeded.maxOfOrNull { it.id } ?: 0) + 1
    }
}
