package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.ResumeWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeWorkoutUseCaseTest {

    @Test
    fun `a paused workout resumes and banks the elapsed pause time`() = runTest {
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
        val useCase = ResumeWorkoutUseCase(repo, clock)

        useCase(id)

        val workout = repo.getWorkout(id)!!.workout
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.workoutStatus)
        assertNull(workout.pausedAt)
        assertEquals(6_000L, workout.accumulatedPausedMs)
    }

    @Test
    fun `a workout that is not paused is left untouched`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock(10_000L)
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = ResumeWorkoutUseCase(repo, clock)

        useCase(id)

        assertEquals(WorkoutStatus.IN_PROGRESS, repo.getWorkout(id)!!.workout.workoutStatus)
        assertEquals(0L, repo.getWorkout(id)!!.workout.accumulatedPausedMs)
    }
}
