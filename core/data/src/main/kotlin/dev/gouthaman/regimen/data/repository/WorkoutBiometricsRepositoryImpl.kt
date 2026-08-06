package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.WorkoutBiometricsDao
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutBiometricsRepositoryImpl @Inject constructor(
    private val dao: WorkoutBiometricsDao,
) : WorkoutBiometricsRepository {
    override suspend fun get(workoutId: String): WorkoutBiometrics? =
        dao.get(workoutId)?.toDomain()

    override fun observe(workoutId: String): Flow<WorkoutBiometrics?> =
        dao.observe(workoutId).map { it?.toDomain() }

    override suspend fun getForWorkouts(workoutIds: List<String>): List<WorkoutBiometrics> =
        dao.getForWorkouts(workoutIds).map { it.toDomain() }

    override suspend fun upsert(biometrics: WorkoutBiometrics): String {
        val id = biometrics.id.ifEmpty { UUID.randomUUID().toString() }
        dao.upsert(biometrics.copy(id = id).toEntity())

        return id
    }

    override suspend fun getMostRecentlyFetched(): WorkoutBiometrics? =
        dao.getMostRecentlyFetched()?.toDomain()

    override suspend fun deleteAll() = dao.deleteAll()
}