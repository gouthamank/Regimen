package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutNoteUseCase
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateWorkoutNoteUseCaseTest {

    @Test
    fun `a non-blank note is stored as-is`() = runTest {
        val repo = FakeWorkoutRepository()
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = UpdateWorkoutNoteUseCase(repo)

        useCase(id, "Felt strong today")

        assertEquals("Felt strong today", repo.getWorkout(id)!!.workout.note)
    }

    @Test
    fun `a blank note is stored as null`() = runTest {
        val repo = FakeWorkoutRepository()
        val id = repo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = UpdateWorkoutNoteUseCase(repo)

        useCase(id, "   ")

        assertNull(repo.getWorkout(id)!!.workout.note)
    }

    @Test
    fun `a missing workout is a no-op`() = runTest {
        val repo = FakeWorkoutRepository()
        UpdateWorkoutNoteUseCase(repo)(999, "note")
    }
}
