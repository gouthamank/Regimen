package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HeartRateTrendEntry
import dev.gouthaman.regimen.domain.model.HeartRateTrendRow
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.cutoffMillis
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** One row per routine with a completed workout, plus a synthetic "combined" row. Full unfiltered
 * history - range filtering happens only in the detail screen. */
class GetHeartRateTrendRowsUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
    private val biometricsRepo: WorkoutBiometricsRepository,
) {
    operator fun invoke(): Flow<List<HeartRateTrendRow>> =
        combine(workoutRepo.observeCompleted(), routineRepo.observeAll()) { workouts, routines ->
            val sorted = workouts.sortedBy { it.workout.startTime }
            val biometricsByWorkoutId = biometricsRepo.getForWorkouts(sorted.map { it.workout.id })
                .associateBy { it.workoutId }

            fun rowFor(routineId: String?, routineName: String?): HeartRateTrendRow {
                val matching =
                    sorted.filter { routineId == null || it.workout.routineId == routineId }
                val avgBpms = matching.mapNotNull { biometricsByWorkoutId[it.workout.id]?.avgBpm }
                return HeartRateTrendRow(
                    routineId = routineId,
                    routineName = routineName,
                    trend = avgBpms.map { it.toFloat() },
                    entryCount = avgBpms.size,
                )
            }

            val combinedRow = rowFor(routineId = null, routineName = null)
            val routineRows = routines
                .filter { rwe -> sorted.any { it.workout.routineId == rwe.routine.id } }
                .map { rwe -> rowFor(rwe.routine.id, rwe.routine.name) }
            listOf(combinedRow) + routineRows
        }
}

/** [routineId] null = combined. Range-filtered; only workouts with a pulled avg BPM and known end
 * time are included. */
class GetHeartRateTrendDetailUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val biometricsRepo: WorkoutBiometricsRepository,
) {
    operator fun invoke(routineId: String?, range: HistoryRange): Flow<List<HeartRateTrendEntry>> =
        workoutRepo.observeCompleted().map { workouts ->
            val cutoff = range.cutoffMillis()
            val matching = workouts
                .filter { routineId == null || it.workout.routineId == routineId }
                .filter { cutoff == null || it.workout.startTime >= cutoff }
                .sortedBy { it.workout.startTime }
            val biometricsByWorkoutId =
                biometricsRepo.getForWorkouts(matching.map { it.workout.id })
                    .associateBy { it.workoutId }
            matching.mapNotNull { w ->
                val avgBpm = biometricsByWorkoutId[w.workout.id]?.avgBpm ?: return@mapNotNull null
                val endTime = w.workout.endTime ?: return@mapNotNull null
                HeartRateTrendEntry(
                    workoutId = w.workout.id,
                    startTime = w.workout.startTime,
                    durationMillis = endTime - w.workout.startTime - w.workout.accumulatedPausedMs,
                    avgBpm = avgBpm,
                )
            }
        }
}
