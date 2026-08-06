package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.util.Clock
import javax.inject.Inject

/**
 * Queries Health Connect for [workoutId]'s `[startTime, endTime]` and persists whatever's found
 * as a [WorkoutBiometrics] row. Returns whether anything was actually found - lets a retry/backfill
 * job tell "pulled" apart from "still nothing there yet, try again later". No-op (returns false)
 * for a workout that's missing, or hasn't finished yet.
 */
class PullBiometricsForWorkoutUseCase @Inject constructor(
    private val healthConnectRepo: HealthConnectRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: String): Boolean {
        val workout = workoutRepo.getWorkout(workoutId)?.workout ?: return false
        val endTime = workout.endTime ?: return false
        val sample = healthConnectRepo.queryBiometrics(workout.startTime, endTime) ?: return false

        workoutBiometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = workoutId,
                avgBpm = sample.avgBpm,
                maxBpm = sample.maxBpm,
                activeCaloriesKcal = sample.activeCaloriesKcal,
                sourcePackageName = sample.sourcePackageName,
                fetchedAt = clock.nowMillis(),
            ),
        )
        return true
    }
}
