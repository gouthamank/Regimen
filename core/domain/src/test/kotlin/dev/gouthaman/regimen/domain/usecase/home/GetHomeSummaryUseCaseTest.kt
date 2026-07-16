package dev.gouthaman.regimen.domain.usecase.home

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutExerciseWithDetails
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.usecase.GetHomeSummaryUseCase
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class GetHomeSummaryUseCaseTest {

    private val zone = ZoneId.systemDefault()
    private val exercise = Exercise(
        id = 1,
        name = "Bench Press",
        type = ExerciseType.STRENGTH,
        muscleGroup = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
    )

    private fun LocalDate.atNoonMillis(): Long =
        atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun thisWeekMonday(): LocalDate =
        LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun completedWorkout(
        id: Long,
        day: LocalDate,
        durationMillis: Long,
        sets: List<SetEntry>,
    ): WorkoutWithDetails {
        val start = day.atNoonMillis()
        return WorkoutWithDetails(
            workout = Workout(
                id = id,
                startTime = start,
                endTime = start + durationMillis,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
            exercises = listOf(
                WorkoutExerciseWithDetails(
                    workoutExercise = WorkoutExercise(
                        id = id,
                        workoutId = id,
                        exerciseId = exercise.id,
                        position = 0
                    ),
                    exercise = exercise,
                    sets = sets,
                    cardio = emptyList(),
                ),
            ),
        )
    }

    @Test
    fun `no completed workouts yields a zeroed summary`() = runTest {
        val repo = FakeWorkoutRepository()
        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(0, summary.workoutsThisWeek)
        assertEquals(0.0, summary.volumeKgThisWeek, 0.0)
        assertEquals(0, summary.weekStreak)
        assertEquals(0, summary.workoutsThisMonth)
    }

    @Test
    fun `in-progress workouts are excluded from the summary`() = runTest {
        val repo = FakeWorkoutRepository()
        val inProgress = completedWorkout(1, thisWeekMonday(), 60_000, emptyList())
            .let {
                it.copy(
                    workout = it.workout.copy(
                        workoutStatus = WorkoutStatus.IN_PROGRESS,
                        endTime = null
                    )
                )
            }
        repo.seed(inProgress)

        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(0, summary.workoutsThisWeek)
    }

    @Test
    fun `volume only counts completed sets`() = runTest {
        val repo = FakeWorkoutRepository()
        val monday = thisWeekMonday()
        val workout = completedWorkout(
            id = 1,
            day = monday,
            durationMillis = 60_000,
            sets = listOf(
                SetEntry(
                    id = 1,
                    workoutExerciseId = 1,
                    setNumber = 1,
                    weightKg = 100.0,
                    reps = 5,
                    isComplete = true
                ),
                SetEntry(
                    id = 2,
                    workoutExerciseId = 1,
                    setNumber = 2,
                    weightKg = 200.0,
                    reps = 5,
                    isComplete = false
                ),
            ),
        )
        repo.seed(workout)

        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(500.0, summary.volumeKgThisWeek, 0.0)
        assertEquals(1, summary.workoutsThisWeek)
        assertEquals(60_000L, summary.durationMillisThisWeek)
    }

    @Test
    fun `week streak counts consecutive weeks including an empty current week`() = runTest {
        val repo = FakeWorkoutRepository()
        val monday = thisWeekMonday()
        repo.seed(
            completedWorkout(1, monday.minusWeeks(1), 60_000, emptyList()),
            completedWorkout(2, monday.minusWeeks(2), 60_000, emptyList()),
        )

        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(0, summary.workoutsThisWeek)
        assertEquals(2, summary.weekStreak)
    }

    @Test
    fun `week streak breaks on a gap week`() = runTest {
        val repo = FakeWorkoutRepository()
        val monday = thisWeekMonday()
        repo.seed(
            completedWorkout(1, monday, 60_000, emptyList()),
            completedWorkout(2, monday.minusWeeks(2), 60_000, emptyList()),
        )

        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(1, summary.weekStreak)
    }

    @Test
    fun `month totals only include workouts from the current month`() = runTest {
        val repo = FakeWorkoutRepository()
        val thisMonth = LocalDate.now(zone).withDayOfMonth(1)
        val lastMonth = thisMonth.minusMonths(1).withDayOfMonth(15)
        repo.seed(
            completedWorkout(1, thisMonth, 60_000, emptyList()),
            completedWorkout(2, lastMonth, 90_000, emptyList()),
        )

        val summary = GetHomeSummaryUseCase(repo)().first()

        assertEquals(1, summary.workoutsThisMonth)
        assertEquals(60_000L, summary.durationMillisThisMonth)
    }
}
