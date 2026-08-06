package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.BiometricTrendEntry
import dev.gouthaman.regimen.domain.model.BiometricTrendRow
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.cutoffMillis
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** One row per routine with a completed workout, plus a synthetic "combined" row. Full unfiltered
 * history - range filtering happens only in the detail screen. */
class GetBiometricTrendRowsUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
    private val biometricsRepo: WorkoutBiometricsRepository,
) {
    operator fun invoke(): Flow<List<BiometricTrendRow>> =
        combine(workoutRepo.observeCompleted(), routineRepo.observeAll()) { workouts, routines ->
            val sorted = workouts.sortedBy { it.workout.startTime }
            val biometricsByWorkoutId = biometricsRepo.getForWorkouts(sorted.map { it.workout.id })
                .associateBy { it.workoutId }

            fun rowFor(routineId: String?, routineName: String?): BiometricTrendRow {
                val matching =
                    sorted.filter { routineId == null || it.workout.routineId == routineId }
                val avgBpms = matching.mapNotNull { biometricsByWorkoutId[it.workout.id]?.avgBpm }
                val calories = matching
                    .mapNotNull { biometricsByWorkoutId[it.workout.id]?.activeCaloriesKcal }
                return BiometricTrendRow(
                    routineId = routineId,
                    routineName = routineName,
                    avgBpmTrend = avgBpms.map { it.toFloat() },
                    caloriesTrend = calories.map { it.toFloat() },
                )
            }

            val combinedRow = rowFor(routineId = null, routineName = null)
            val routineRows = routines
                .filter { rwe -> sorted.any { it.workout.routineId == rwe.routine.id } }
                .map { rwe -> rowFor(rwe.routine.id, rwe.routine.name) }
            listOf(combinedRow) + routineRows
        }
}

/** [routineId] null = combined. Range-filtered; a workout is included if it has at least one of
 * avg BPM or calories. */
class GetBiometricTrendDetailUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
    private val biometricsRepo: WorkoutBiometricsRepository,
) {
    operator fun invoke(routineId: String?, range: HistoryRange): Flow<List<BiometricTrendEntry>> =
        combine(workoutRepo.observeCompleted(), routineRepo.observeAll()) { workouts, routines ->
            val cutoff = range.cutoffMillis()
            val matching = workouts
                .filter { routineId == null || it.workout.routineId == routineId }
                .filter { cutoff == null || it.workout.startTime >= cutoff }
                .sortedBy { it.workout.startTime }
            val biometricsByWorkoutId =
                biometricsRepo.getForWorkouts(matching.map { it.workout.id })
                    .associateBy { it.workoutId }
            matching.mapNotNull { w ->
                val biometrics = biometricsByWorkoutId[w.workout.id]
                val avgBpm = biometrics?.avgBpm
                val calories = biometrics?.activeCaloriesKcal
                if (avgBpm == null && calories == null) return@mapNotNull null
                val endTime = w.workout.endTime ?: return@mapNotNull null
                BiometricTrendEntry(
                    workoutId = w.workout.id,
                    startTime = w.workout.startTime,
                    durationMillis = endTime - w.workout.startTime - w.workout.accumulatedPausedMs,
                    avgBpm = avgBpm,
                    activeCaloriesKcal = calories,
                    routineName = w.workout.routineId
                        ?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine?.name },
                )
            }
        }
}
