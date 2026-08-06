package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class FakeWorkoutBiometricsRepository : WorkoutBiometricsRepository {

    private val biometricsByWorkoutId = mutableMapOf<String, WorkoutBiometrics>()
    private val flowsByWorkoutId = mutableMapOf<String, MutableStateFlow<WorkoutBiometrics?>>()

    override suspend fun get(workoutId: String): WorkoutBiometrics? =
        biometricsByWorkoutId[workoutId]

    override fun observe(workoutId: String): StateFlow<WorkoutBiometrics?> =
        flowsByWorkoutId.getOrPut(workoutId) { MutableStateFlow(biometricsByWorkoutId[workoutId]) }

    override suspend fun getForWorkouts(workoutIds: List<String>): List<WorkoutBiometrics> =
        workoutIds.mapNotNull { biometricsByWorkoutId[it] }

    override suspend fun upsert(biometrics: WorkoutBiometrics): String {
        val id = biometrics.id.ifEmpty { UUID.randomUUID().toString() }
        val saved = biometrics.copy(id = id)
        biometricsByWorkoutId[biometrics.workoutId] = saved
        flowsByWorkoutId[biometrics.workoutId]?.value = saved
        return id
    }

    override suspend fun getMostRecentlyFetched(): WorkoutBiometrics? =
        biometricsByWorkoutId.values.maxByOrNull { it.fetchedAt }

    override suspend fun deleteAll() {
        biometricsByWorkoutId.clear()
        flowsByWorkoutId.values.forEach { it.value = null }
    }
}
