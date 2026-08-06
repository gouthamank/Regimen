package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectBackfillWindow
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.util.Clock
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

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

/**
 * Finds `COMPLETE` workouts started within [backfillWindow] that don't have a [WorkoutBiometrics]
 * row yet, and calls [PullBiometricsForWorkoutUseCase] for each. Composed from
 * [WorkoutRepository]/[WorkoutBiometricsRepository] directly rather than a dedicated cross-table
 * query, so the candidate-selection logic here is exercised by ordinary fakes.
 */
class RunBiometricsBackfillUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
    private val pullBiometricsForWorkoutUseCase: PullBiometricsForWorkoutUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(backfillWindow: HealthConnectBackfillWindow) {
        val now = clock.nowMillis()
        val sinceStartTime = now - backfillWindow.days * DAY_MILLIS
        val completedIds = workoutRepo.observeCompletedBetween(sinceStartTime, now).first()
            .map { it.id }
        val missingIds = completedIds.filter { workoutBiometricsRepo.get(it) == null }
        for (id in missingIds) {
            pullBiometricsForWorkoutUseCase(id)
        }
    }
}
