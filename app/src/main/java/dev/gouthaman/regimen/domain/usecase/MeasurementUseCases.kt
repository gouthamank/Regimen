package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.repository.MeasurementRepository
import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMeasurementTypesUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    operator fun invoke(): Flow<List<MeasurementType>> = repo.observeTypes()
}

class ObserveMeasurementsUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    operator fun invoke(typeId: Long): Flow<List<BodyMetric>> = repo.observeMetrics(typeId)
}

class AddMeasurementUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(typeId: Long, date: Long, value: Double): Long =
        repo.addMetric(typeId, date, value)
}

class AddMeasurementTypeUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(name: String, unit: String): Long = repo.addType(name, unit)
}

class DeleteMeasurementTypeUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(type: MeasurementType) = repo.deleteType(type)
}

class DeleteMeasurementUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(metric: BodyMetric) = repo.deleteMetric(metric)
}
