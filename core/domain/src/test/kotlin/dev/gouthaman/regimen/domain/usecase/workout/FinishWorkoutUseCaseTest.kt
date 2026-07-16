package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.FinishWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishWorkoutUseCaseTest {

    @Test
    fun `an in-progress workout is marked complete with an end time`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(10_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = FinishWorkoutUseCase(repo, clock)

        useCase(id)

        val workout = repo.getWorkout(id)!!.workout
        assertEquals(WorkoutStatus.COMPLETE, workout.workoutStatus)
        assertEquals(10_000L, workout.endTime)
    }

    @Test
    fun `a paused workout settles its paused time before finishing`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(10_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        repo.updateWorkout(
            repo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.PAUSED,
                pausedAt = 5_000L,
                accumulatedPausedMs = 1_000,
            ),
        )
        val useCase = FinishWorkoutUseCase(repo, clock)

        useCase(id)

        val workout = repo.getWorkout(id)!!.workout
        assertNull(workout.pausedAt)
        assertEquals(6_000L, workout.accumulatedPausedMs)
        assertEquals(10_000L, workout.endTime)
    }

    @Test
    fun `an already complete workout is left untouched`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(10_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        repo.updateWorkout(
            repo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.COMPLETE,
                endTime = 5_000
            )
        )
        val useCase = FinishWorkoutUseCase(repo, clock)

        useCase(id)

        assertEquals(5_000L, repo.getWorkout(id)!!.workout.endTime)
    }

    @Test
    fun `a workout being edited is left untouched`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(10_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        repo.updateWorkout(repo.getWorkout(id)!!.workout.copy(workoutStatus = WorkoutStatus.EDITING))
        val useCase = FinishWorkoutUseCase(repo, clock)

        useCase(id)

        assertEquals(WorkoutStatus.EDITING, repo.getWorkout(id)!!.workout.workoutStatus)
    }
}
