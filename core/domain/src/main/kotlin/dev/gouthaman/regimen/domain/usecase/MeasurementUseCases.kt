package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import dev.gouthaman.regimen.domain.repository.MeasurementRepository
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
    operator fun invoke(typeId: String): Flow<List<BodyMetric>> = repo.observeMetrics(typeId)
}

class AddMeasurementUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(typeId: String, date: Long, value: Double): String =
        repo.addMetric(typeId, date, value)
}

class AddMeasurementTypeUseCase @Inject constructor(
    private val repo: MeasurementRepository,
) {
    suspend operator fun invoke(name: String, unit: String): String = repo.addType(name, unit)
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
