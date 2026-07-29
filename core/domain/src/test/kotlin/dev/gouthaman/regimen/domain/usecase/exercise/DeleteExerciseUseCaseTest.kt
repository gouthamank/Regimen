package dev.gouthaman.regimen.domain.usecase.exercise

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineExercise
import dev.gouthaman.regimen.domain.model.RoutineExerciseWithExercise
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseResult
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteExerciseUseCaseTest {

    private val benchPress =
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)

    @Test
    fun `an exercise used by no routine or workout is deleted`() = runTest {
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val useCase =
            DeleteExerciseUseCase(exerciseRepo, FakeWorkoutRepository(), FakeRoutineRepository())

        val result = useCase(benchPress)

        assertEquals(DeleteExerciseResult.Deleted, result)
        assertEquals(null, exerciseRepo.getById(benchPress.id))
    }

    @Test
    fun `an exercise referenced by a routine is refused`() = runTest {
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            RoutineWithExercises(
                routine = Routine("1", "Push Day", 0),
                exercises = listOf(
                    RoutineExerciseWithExercise(
                        RoutineExercise("1", "1", benchPress.id, 0, 3, 8, 90),
                        benchPress
                    ),
                ),
            ),
        )
        val useCase = DeleteExerciseUseCase(exerciseRepo, FakeWorkoutRepository(), routineRepo)

        val result = useCase(benchPress) as DeleteExerciseResult.InUse

        assertTrue(result.inRoutines)
        assertTrue(!result.inWorkouts)
        assertEquals(benchPress, exerciseRepo.getById(benchPress.id))
    }

    @Test
    fun `an exercise referenced by a past workout is refused`() = runTest {
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val workoutRepo = FakeWorkoutRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        val useCase = DeleteExerciseUseCase(exerciseRepo, workoutRepo, FakeRoutineRepository())

        val result = useCase(benchPress) as DeleteExerciseResult.InUse

        assertTrue(result.inWorkouts)
        assertTrue(!result.inRoutines)
    }
}
