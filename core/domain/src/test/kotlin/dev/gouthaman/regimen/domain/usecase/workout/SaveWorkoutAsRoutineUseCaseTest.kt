package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaveWorkoutAsRoutineUseCaseTest {

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val running =
        Exercise(2, "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    @Test
    fun `a workout with only cardio exercises produces no routine`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { running }
        val routineRepo = FakeRoutineRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = running.id,
                position = 0
            )
        )
        val useCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo)

        val result = useCase(workoutId, "My Routine", defaultRestSec = 90)

        assertNull(result)
    }

    @Test
    fun `missing workout produces no routine`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val useCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo)

        assertNull(useCase(999, "My Routine", defaultRestSec = 90))
    }

    @Test
    fun `cardio exercises are excluded, target sets match logged sets, rest uses the default`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            workoutRepo.exerciseLookup = { id -> if (id == running.id) running else benchPress }
            val routineRepo = FakeRoutineRepository()
            val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
            val benchWe = workoutRepo.addExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = benchPress.id,
                    position = 0
                )
            )
            workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = benchWe,
                    setNumber = 1,
                    weightKg = 60.0,
                    reps = 10
                )
            )
            workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = benchWe,
                    setNumber = 2,
                    weightKg = 60.0,
                    reps = 10
                )
            )
            workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = benchWe,
                    setNumber = 3,
                    weightKg = 65.0,
                    reps = 8
                )
            )
            workoutRepo.addExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = running.id,
                    position = 1
                )
            )
            val useCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo)

            val routineId = useCase(workoutId, "My Routine", defaultRestSec = 75)!!

            val saved = routineRepo.getRoutine(routineId)!!
            assertEquals(1, saved.exercises.size)
            val spec = saved.exercises[0].routineExercise
            assertEquals(benchPress.id, spec.exerciseId)
            assertEquals(3, spec.targetSets)
            assertEquals(10, spec.targetReps)
            assertEquals(75, spec.targetRestSec)
        }

    @Test
    fun `a strength exercise with no logged sets still gets a target of one set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val routineRepo = FakeRoutineRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        val useCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo)

        val routineId = useCase(workoutId, "My Routine", defaultRestSec = 90)!!

        val spec = routineRepo.getRoutine(routineId)!!.exercises[0].routineExercise
        assertEquals(1, spec.targetSets)
        assertEquals(10, spec.targetReps)
    }

    @Test
    fun `a rep tie is broken toward the larger rep count`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val routineRepo = FakeRoutineRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val we = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(SetEntry(workoutExerciseId = we, setNumber = 1, reps = 10))
        workoutRepo.upsertSet(SetEntry(workoutExerciseId = we, setNumber = 2, reps = 10))
        workoutRepo.upsertSet(SetEntry(workoutExerciseId = we, setNumber = 3, reps = 12))
        workoutRepo.upsertSet(SetEntry(workoutExerciseId = we, setNumber = 4, reps = 12))
        val useCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo)

        val routineId = useCase(workoutId, "My Routine", defaultRestSec = 90)!!

        assertEquals(
            12,
            routineRepo.getRoutine(routineId)!!.exercises[0].routineExercise.targetReps
        )
    }
}
