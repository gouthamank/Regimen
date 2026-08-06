package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import java.util.UUID

class FakeWorkoutBiometricsRepository : WorkoutBiometricsRepository {

    private val biometricsByWorkoutId = mutableMapOf<String, WorkoutBiometrics>()

    override suspend fun get(workoutId: String): WorkoutBiometrics? =
        biometricsByWorkoutId[workoutId]

    override suspend fun upsert(biometrics: WorkoutBiometrics): String {
        val id = biometrics.id.ifEmpty { UUID.randomUUID().toString() }
        biometricsByWorkoutId[biometrics.workoutId] = biometrics.copy(id = id)
        return id
    }
}
