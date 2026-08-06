package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.WorkoutBiometricsDao
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutBiometricsRepositoryImpl @Inject constructor(
    private val dao: WorkoutBiometricsDao,
) : WorkoutBiometricsRepository {
    override suspend fun get(workoutId: String): WorkoutBiometrics? =
        dao.get(workoutId)?.toDomain()

    override suspend fun upsert(biometrics: WorkoutBiometrics): String {
        val id = biometrics.id.ifEmpty { UUID.randomUUID().toString() }
        dao.upsert(biometrics.copy(id = id).toEntity())

        return id
    }
}