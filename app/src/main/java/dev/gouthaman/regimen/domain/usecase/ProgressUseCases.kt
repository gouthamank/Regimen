package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.repository.ExerciseRepository
import dev.gouthaman.regimen.data.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.model.PersonalRecord
import dev.gouthaman.regimen.domain.model.WeekCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** PR list: heaviest weight per exercise, resolved with exercise names, heaviest first. */
class GetPersonalRecordsUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
) {
    operator fun invoke(): Flow<List<PersonalRecord>> =
        combine(workoutRepo.observePersonalRecords(), exerciseRepo.observeAll()) { prs, exercises ->
            val byId = exercises.associateBy { it.id }
            prs.mapNotNull { row ->
                val ex = byId[row.exerciseId] ?: return@mapNotNull null
                PersonalRecord(ex.id, ex.name, row.bestWeightKg)
            }.sortedByDescending { it.bestWeightKg }
        }
}

/** Workout counts per week for the last [weeks] weeks (oldest first). */
class GetWorkoutFrequencyUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(weeks: Int = 8): Flow<List<WeekCount>> =
        workoutRepo.observeCompleted().map { workouts ->
            val zone = ZoneId.systemDefault()
            val countsByWeek: Map<LocalDate, Int> = workouts
                .groupingBy { w ->
                    Instant.ofEpochMilli(w.workout.startTime).atZone(zone).toLocalDate()
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }
                .eachCount()

            val thisWeekStart = LocalDate.now(zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (weeks - 1 downTo 0).map { back ->
                val weekStart = thisWeekStart.minusWeeks(back.toLong())
                WeekCount(weekStart, countsByWeek[weekStart] ?: 0)
            }
        }
}
