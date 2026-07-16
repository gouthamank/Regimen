package dev.gouthaman.regimen.domain.usecase.progress

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetPersonalRecordsUseCaseTest {

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val pushUp =
        Exercise(2, "Push Up", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BODYWEIGHT)

    @Test
    fun `weight and reps records are merged and sorted with heaviest first`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress, pushUp)

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
                weightKg = 80.0,
                reps = 5,
                isComplete = true
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = benchWe,
                setNumber = 2,
                weightKg = 100.0,
                reps = 3,
                isComplete = true
            )
        )
        val pushUpWe = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = pushUp.id,
                position = 1
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = pushUpWe,
                setNumber = 1,
                reps = 20,
                isComplete = true
            )
        )
        workoutRepo.updateWorkout(workoutRepo.getWorkout(workoutId)!!.workout.copy(endTime = 2_000))

        GetPersonalRecordsUseCase(workoutRepo, exerciseRepo)().test {
            val records = awaitItem()
            assertEquals(2, records.size)
            assertEquals(benchPress.id, records[0].exerciseId)
            assertEquals(100.0, records[0].bestWeightKg)
            assertEquals(pushUp.id, records[1].exerciseId)
            assertEquals(20, records[1].bestReps)
        }
    }

    @Test
    fun `records for unknown exercises are dropped`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        workoutRepo.exerciseLookup = { benchPress }

        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val we = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = we,
                setNumber = 1,
                weightKg = 80.0,
                reps = 5,
                isComplete = true
            )
        )

        GetPersonalRecordsUseCase(workoutRepo, exerciseRepo)().test {
            assertEquals(emptyList<Any>(), awaitItem())
        }
    }

    @Test
    fun `incomplete sets do not count toward a personal record`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)

        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val we = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = we,
                setNumber = 1,
                weightKg = 999.0,
                reps = 1,
                isComplete = false
            )
        )
        workoutRepo.updateWorkout(workoutRepo.getWorkout(workoutId)!!.workout.copy(endTime = 2_000))

        GetPersonalRecordsUseCase(workoutRepo, exerciseRepo)().test {
            assertEquals(emptyList<Any>(), awaitItem())
        }
    }
}
