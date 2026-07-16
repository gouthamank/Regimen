package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.AddExercisesToWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddExercisesToWorkoutUseCaseTest {

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val running =
        Exercise(2, "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    @Test
    fun `missing workout is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(999, listOf(benchPress.id))
    }

    @Test
    fun `a strength exercise with no history gets a single blank set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(workoutId, listOf(benchPress.id))

        val sets = workoutRepo.getWorkout(workoutId)!!.exercises[0].sets
        assertEquals(1, sets.size)
        assertNull(sets[0].weightKg)
        assertNull(sets[0].reps)
    }

    @Test
    fun `a strength exercise prefills from its own most recent logged set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val pastWorkoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val pastWe = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = pastWorkoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = pastWe,
                setNumber = 1,
                weightKg = 70.0,
                reps = 6
            )
        )
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(pastWorkoutId)!!.workout.copy(
                endTime = 1_500,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )

        val workoutId = workoutRepo.createWorkout(startTime = 2_000, routineId = null)
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(workoutId, listOf(benchPress.id))

        val sets = workoutRepo.getWorkout(workoutId)!!.exercises[0].sets
        assertEquals(70.0, sets[0].weightKg)
        assertEquals(6, sets[0].reps)
    }

    @Test
    fun `a cardio exercise gets a single blank cardio bout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { running }
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(running)
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(workoutId, listOf(running.id))

        val details = workoutRepo.getWorkout(workoutId)!!.exercises[0]
        assertEquals(0, details.sets.size)
        assertEquals(1, details.cardio.size)
        assertEquals(0L, details.cardio[0].durationSec)
    }

    @Test
    fun `unknown exercise ids are skipped without breaking position ordering`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(workoutId, listOf(999, benchPress.id))

        val exercises = workoutRepo.getWorkout(workoutId)!!.exercises
        assertEquals(1, exercises.size)
        assertEquals(0, exercises[0].workoutExercise.position)
    }

    @Test
    fun `newly added exercises continue the position sequence after existing ones`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        val useCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo)

        useCase(workoutId, listOf(benchPress.id))

        val exercises = workoutRepo.getWorkout(workoutId)!!.exercises
        assertEquals(2, exercises.size)
        assertEquals(1, exercises[1].workoutExercise.position)
    }
}
