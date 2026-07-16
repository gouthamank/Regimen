package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.AddSetUseCase
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddSetUseCaseTest {

    @Test
    fun `the first set of an exercise starts at set number one with no prior values`() = runTest {
        val repo = FakeWorkoutRepository()
        val workoutId = repo.createWorkout(startTime = 1_000, routineId = null)
        val weId =
            repo.addExercise(WorkoutExercise(workoutId = workoutId, exerciseId = 1, position = 0))
        val useCase = AddSetUseCase(repo)

        useCase(weId, lastSet = null)

        val sets = repo.getWorkout(workoutId)!!.exercises[0].sets
        assertEquals(1, sets.size)
        assertEquals(1, sets[0].setNumber)
        assertNull(sets[0].weightKg)
        assertNull(sets[0].reps)
    }

    @Test
    fun `a subsequent set continues numbering and seeds from the last set`() = runTest {
        val repo = FakeWorkoutRepository()
        val workoutId = repo.createWorkout(startTime = 1_000, routineId = null)
        val weId =
            repo.addExercise(WorkoutExercise(workoutId = workoutId, exerciseId = 1, position = 0))
        val lastSet =
            SetEntry(id = 1, workoutExerciseId = weId, setNumber = 2, weightKg = 80.0, reps = 6)
        val useCase = AddSetUseCase(repo)

        useCase(weId, lastSet)

        val added = repo.getWorkout(workoutId)!!.exercises[0].sets.last()
        assertEquals(3, added.setNumber)
        assertEquals(80.0, added.weightKg)
        assertEquals(6, added.reps)
    }
}
