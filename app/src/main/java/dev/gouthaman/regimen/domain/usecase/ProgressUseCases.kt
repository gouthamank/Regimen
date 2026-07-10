package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.repository.ExerciseRepository
import dev.gouthaman.regimen.data.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.PersonalRecord
import dev.gouthaman.regimen.domain.model.WeekCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** PR list: heaviest weight per exercise, or (bodyweight, no logged weight) most reps in a
 * set — resolved with exercise names, heaviest/highest first. */
class GetPersonalRecordsUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
) {
    operator fun invoke(): Flow<List<PersonalRecord>> =
        combine(
            workoutRepo.observePersonalRecords(),
            workoutRepo.observeBestReps(),
            exerciseRepo.observeAll(),
        ) { weightPrs, repsPrs, exercises ->
            val byId = exercises.associateBy { it.id }
            val weightRecords = weightPrs.mapNotNull { row ->
                val ex = byId[row.exerciseId] ?: return@mapNotNull null
                PersonalRecord(ex.id, ex.name, bestWeightKg = row.bestWeightKg)
            }
            val repsRecords = repsPrs.mapNotNull { row ->
                val ex = byId[row.exerciseId] ?: return@mapNotNull null
                PersonalRecord(ex.id, ex.name, bestReps = row.bestReps)
            }
            (weightRecords + repsRecords).sortedWith(
                compareByDescending<PersonalRecord> { it.bestWeightKg ?: -1.0 }
                    .thenByDescending { it.bestReps ?: -1 }
            )
        }
}

/** Workout counts per week for [range] (oldest first). [HistoryRange.ALL] spans back to the first
 * logged workout. */
class GetWorkoutFrequencyUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(range: HistoryRange = HistoryRange.THREE_MONTHS): Flow<List<WeekCount>> =
        workoutRepo.observeCompleted().map { workouts ->
            val zone = ZoneId.systemDefault()
            fun weekOf(startTime: Long) =
                Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            val countsByWeek: Map<LocalDate, Int> =
                workouts.groupingBy { weekOf(it.workout.startTime) }.eachCount()

            val thisWeekStart = LocalDate.now(zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = range.weeks ?: run {
                val earliest =
                    workouts.minOfOrNull { weekOf(it.workout.startTime) } ?: thisWeekStart
                (ChronoUnit.WEEKS.between(earliest, thisWeekStart).toInt() + 1).coerceAtLeast(1)
            }
            (weeks - 1 downTo 0).map { back ->
                val weekStart = thisWeekStart.minusWeeks(back.toLong())
                WeekCount(weekStart, countsByWeek[weekStart] ?: 0)
            }
        }
}
