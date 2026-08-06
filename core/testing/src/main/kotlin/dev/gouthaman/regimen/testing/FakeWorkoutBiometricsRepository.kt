package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import java.util.UUID

class FakeWorkoutBiometricsRepository : WorkoutBiometricsRepository {

    private val biometricsByWorkoutId = mutableMapOf<String, WorkoutBiometrics>()

    /** Completed workout ids available for [getCompletedWorkoutIdsMissingBiometrics] to consider,
     * keyed by their startTime - set up by tests directly, since this fake has no notion of a
     * workout on its own. */
    val completedWorkoutStartTimes = mutableMapOf<String, Long>()

    override suspend fun get(workoutId: String): WorkoutBiometrics? =
        biometricsByWorkoutId[workoutId]

    override suspend fun upsert(biometrics: WorkoutBiometrics): String {
        val id = biometrics.id.ifEmpty { UUID.randomUUID().toString() }
        biometricsByWorkoutId[biometrics.workoutId] = biometrics.copy(id = id)
        return id
    }

    override suspend fun getCompletedWorkoutIdsMissingBiometrics(sinceStartTime: Long): List<String> =
        completedWorkoutStartTimes
            .filterValues { it >= sinceStartTime }
            .keys
            .filterNot { it in biometricsByWorkoutId }
}
