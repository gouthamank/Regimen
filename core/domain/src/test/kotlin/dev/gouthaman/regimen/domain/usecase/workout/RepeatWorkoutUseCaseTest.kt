package dev.gouthaman.regimen.domain.usecase.workout

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineExercise
import dev.gouthaman.regimen.domain.model.RoutineExerciseWithExercise
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.RepeatWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatWorkoutUseCaseTest {

    private val clock = FakeClock(1_000L)
    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val running =
        Exercise(2, "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    @Test
    fun `missing source workout returns null`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val useCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        )

        val result = useCase(999)

        assertNull(result)
    }

    @Test
    fun `a routine-based source delegates to StartWorkoutUseCase`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            RoutineWithExercises(
                routine = Routine(id = 1, name = "Push Day", position = 0),
                exercises = listOf(
                    RoutineExerciseWithExercise(
                        RoutineExercise(1, 1, benchPress.id, 0, 3, 8, 90),
                        benchPress,
                    ),
                ),
            ),
        )
        val sourceId = workoutRepo.createWorkout(startTime = 1_000, routineId = 1)
        val useCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        )

        val newId = useCase(sourceId)!!

        assertEquals(3, workoutRepo.getWorkout(newId)!!.exercises[0].sets.size)
    }

    @Test
    fun `a freeform source clones logged strength sets`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val sourceId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.updateWorkout(workoutRepo.getWorkout(sourceId)!!.workout.copy(workoutStatus = WorkoutStatus.COMPLETE))
        val weId = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = sourceId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 1,
                weightKg = 60.0,
                reps = 10
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 2,
                weightKg = 65.0,
                reps = 8
            )
        )
        val useCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        )

        val newId = useCase(sourceId)!!

        val sets = workoutRepo.getWorkout(newId)!!.exercises[0].sets.sortedBy { it.setNumber }
        assertEquals(2, sets.size)
        assertEquals(60.0, sets[0].weightKg)
        assertEquals(10, sets[0].reps)
        assertEquals(65.0, sets[1].weightKg)
        assertEquals(8, sets[1].reps)
    }

    @Test
    fun `a freeform strength exercise with no logged sets clones as one blank set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val sourceId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = sourceId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        val useCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        )

        val newId = useCase(sourceId)!!

        val sets = workoutRepo.getWorkout(newId)!!.exercises[0].sets
        assertEquals(1, sets.size)
        assertNull(sets[0].weightKg)
        assertNull(sets[0].reps)
    }

    @Test
    fun `a freeform cardio exercise clones as a blank cardio bout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { id -> if (id == running.id) running else benchPress }
        val routineRepo = FakeRoutineRepository()
        val sourceId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val weId = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = sourceId,
                exerciseId = running.id,
                position = 0
            )
        )
        workoutRepo.upsertCardio(
            CardioEntry(
                workoutExerciseId = weId,
                durationSec = 1_800,
                distanceMeters = 5_000.0
            )
        )
        val useCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        )

        val newId = useCase(sourceId)!!

        val exerciseDetails = workoutRepo.getWorkout(newId)!!.exercises[0]
        assertEquals(0, exerciseDetails.sets.size)
        assertEquals(1, exerciseDetails.cardio.size)
        assertEquals(0L, exerciseDetails.cardio[0].durationSec)
        assertNull(exerciseDetails.cardio[0].distanceMeters)
    }
}
