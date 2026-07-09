package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.model.HomeSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * This-week totals plus a weekly streak, derived from completed workouts. Weeks start on Monday,
 * matching [GetWorkoutFrequencyUseCase]. Volume counts only completed sets (same "logged work"
 * semantics as the PR derivation); bodyweight sets contribute no load.
 */
class GetHomeSummaryUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(): Flow<HomeSummary> = workoutRepo.observeCompleted().map { workouts ->
        val zone = ZoneId.systemDefault()
        fun weekOf(startTime: Long): LocalDate =
            Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val thisWeek = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val countsByWeek = workouts.groupingBy { weekOf(it.workout.startTime) }.eachCount()

        val thisWeekWorkouts = workouts.filter { weekOf(it.workout.startTime) == thisWeek }
        val volume = thisWeekWorkouts.sumOf { w ->
            w.exercises.sumOf { we ->
                we.sets.filter { it.isComplete }
                    .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
            }
        }
        val duration = thisWeekWorkouts.sumOf { w ->
            val end = w.workout.endTime ?: w.workout.startTime
            (end - w.workout.startTime).coerceAtLeast(0)
        }

        // Consecutive weeks with >=1 workout, ending at the current week. The current
        // (in-progress) week is allowed to be empty without breaking the streak.
        var streak = 0
        var cursor = thisWeek
        if ((countsByWeek[cursor] ?: 0) == 0) cursor = cursor.minusWeeks(1)
        while ((countsByWeek[cursor] ?: 0) > 0) {
            streak++
            cursor = cursor.minusWeeks(1)
        }

        HomeSummary(
            workoutsThisWeek = thisWeekWorkouts.size,
            volumeKgThisWeek = volume,
            durationMillisThisWeek = duration,
            weekStreak = streak,
        )
    }
}
