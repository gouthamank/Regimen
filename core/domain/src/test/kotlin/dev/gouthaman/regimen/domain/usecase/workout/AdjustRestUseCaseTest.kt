package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.AdjustRestUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustRestUseCaseTest {

    private suspend fun restingWorkout(
        repo: FakeWorkoutRepository,
        endAt: Long,
        totalSec: Int,
    ): String {
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        repo.updateWorkout(
            repo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.IN_REST_TIME,
                restTimeEndAt = endAt,
                restTotalSec = totalSec,
                restWorkoutExerciseId = "1",
            ),
        )
        return id
    }

    @Test
    fun `adding time clamps so it cannot exceed the original duration from now`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(20_000L)
        val id = restingWorkout(repo, endAt = 60_000L, totalSec = 60)
        val useCase = AdjustRestUseCase(repo, clock)

        useCase(id, deltaSec = 30)

        assertEquals(80_000L, repo.getWorkout(id)!!.workout.restTimeEndAt)
    }

    @Test
    fun `subtracting time clamps so it cannot go before now`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(20_000L)
        val id = restingWorkout(repo, endAt = 25_000L, totalSec = 10)
        val useCase = AdjustRestUseCase(repo, clock)

        useCase(id, deltaSec = -30)

        assertEquals(20_000L, repo.getWorkout(id)!!.workout.restTimeEndAt)
    }

    @Test
    fun `an adjustment within bounds is applied exactly`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(20_000L)
        val id = restingWorkout(repo, endAt = 50_000L, totalSec = 60)
        val useCase = AdjustRestUseCase(repo, clock)

        useCase(id, deltaSec = 5)

        assertEquals(55_000L, repo.getWorkout(id)!!.workout.restTimeEndAt)
    }

    @Test
    fun `a workout that is not resting is left untouched`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(20_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = AdjustRestUseCase(repo, clock)

        useCase(id, deltaSec = 15)

        assertEquals(null, repo.getWorkout(id)!!.workout.restTimeEndAt)
    }
}
