package dev.gouthaman.regimen.domain.usecase.progress

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class GetWorkoutFrequencyUseCaseTest {

    private val zone = ZoneId.systemDefault()

    private fun thisWeekMonday(): LocalDate =
        LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private fun completedAt(id: Long, day: LocalDate) = WorkoutWithDetails(
        workout = Workout(
            id = id,
            startTime = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            endTime = day.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
            workoutStatus = WorkoutStatus.COMPLETE,
        ),
        exercises = emptyList(),
    )

    @Test
    fun `four week range returns exactly four weeks ending at the current week`() = runTest {
        val repo = FakeWorkoutRepository()
        val monday = thisWeekMonday()
        repo.seed(completedAt(1, monday), completedAt(2, monday.minusWeeks(1)))

        GetWorkoutFrequencyUseCase(repo)(HistoryRange.FOUR_WEEKS).test {
            val weeks = awaitItem()
            assertEquals(4, weeks.size)
            assertEquals(monday, weeks.last().weekStart)
            assertEquals(1, weeks.last().count)
            assertEquals(1, weeks[2].count)
        }
    }

    @Test
    fun `a week with no workouts has a zero count`() = runTest {
        val repo = FakeWorkoutRepository()
        GetWorkoutFrequencyUseCase(repo)(HistoryRange.FOUR_WEEKS).test {
            val weeks = awaitItem()
            assertEquals(4, weeks.size)
            assertEquals(0, weeks.sumOf { it.count })
        }
    }

    @Test
    fun `all range spans back to the earliest logged workout`() = runTest {
        val repo = FakeWorkoutRepository()
        val monday = thisWeekMonday()
        repo.seed(completedAt(1, monday), completedAt(2, monday.minusWeeks(5)))

        GetWorkoutFrequencyUseCase(repo)(HistoryRange.ALL).test {
            val weeks = awaitItem()
            assertEquals(6, weeks.size)
            assertEquals(monday.minusWeeks(5), weeks.first().weekStart)
            assertEquals(monday, weeks.last().weekStart)
        }
    }

    @Test
    fun `all range with no workouts is a single current week`() = runTest {
        val repo = FakeWorkoutRepository()
        GetWorkoutFrequencyUseCase(repo)(HistoryRange.ALL).test {
            val weeks = awaitItem()
            assertEquals(1, weeks.size)
            assertEquals(thisWeekMonday(), weeks.first().weekStart)
        }
    }
}
