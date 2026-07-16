package dev.gouthaman.regimen.domain.usecase.workout

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
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartWorkoutUseCaseTest {

    private val clock = FakeClock(1_000L)
    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise(2, "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)

    private fun routineWith(vararg exercises: Pair<Exercise, RoutineExercise>) =
        RoutineWithExercises(
            routine = Routine(id = 1, name = "Push Day", position = 0),
            exercises = exercises.map { (exercise, routineExercise) ->
                RoutineExerciseWithExercise(
                    routineExercise,
                    exercise
                )
            },
        )

    @Test
    fun `freeform workout has no exercises and returns the new id`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(null)

        assertEquals(1L, id)
        assertEquals(0, workoutRepo.getWorkout(id)?.exercises?.size)
    }

    @Test
    fun `missing routine still creates a bare workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = 42)

        assertEquals(0, workoutRepo.getWorkout(id)?.exercises?.size)
    }

    @Test
    fun `routine with no prior session prefills target reps and no weight`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                benchPress to RoutineExercise(
                    1,
                    1,
                    benchPress.id,
                    0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                ),
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = 1)

        val details = workoutRepo.getWorkout(id)!!
        assertEquals(1, details.exercises.size)
        val sets = details.exercises[0].sets
        assertEquals(3, sets.size)
        sets.forEach { set ->
            assertNull(set.weightKg)
            assertEquals(8, set.reps)
        }
    }

    @Test
    fun `routine prefills weight and reps from the most recent session of the same routine`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            val routineRepo = FakeRoutineRepository()
            routineRepo.seed(
                routineWith(
                    benchPress to RoutineExercise(
                        1,
                        1,
                        benchPress.id,
                        0,
                        targetSets = 2,
                        targetReps = 8,
                        targetRestSec = 90
                    ),
                ),
            )
            workoutRepo.seed(
                priorSessionFor(routineId = 1, exercise = benchPress, note = "Add 2.5kg next time"),
            )
            val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

            val id = useCase(routineId = 1)

            val details = workoutRepo.getWorkout(id)!!
            val sets = details.exercises[0].sets.sortedBy { it.setNumber }
            assertEquals(100.0, sets[0].weightKg)
            assertEquals(5, sets[0].reps)
            assertEquals(105.0, sets[1].weightKg)
            assertEquals(5, sets[1].reps)
            assertEquals("Add 2.5kg next time", details.workout.note)
        }

    @Test
    fun `a blank prior note is not carried forward`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(benchPress to RoutineExercise(1, 1, benchPress.id, 0, 2, 8, 90)),
        )
        workoutRepo.seed(priorSessionFor(routineId = 1, exercise = benchPress, note = "   "))
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = 1)

        assertNull(workoutRepo.getWorkout(id)!!.workout.note)
    }

    @Test
    fun `target sets below one still logs a single set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                benchPress to RoutineExercise(
                    1,
                    1,
                    benchPress.id,
                    0,
                    targetSets = 0,
                    targetReps = 8,
                    targetRestSec = 90
                )
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = 1)

        assertEquals(1, workoutRepo.getWorkout(id)!!.exercises[0].sets.size)
    }

    private suspend fun priorSessionFor(
        routineId: Long,
        exercise: Exercise,
        note: String?
    ): WorkoutWithDetails {
        val repo = FakeWorkoutRepository()
        val workoutId = repo.createWorkout(startTime = 1_000, routineId = routineId)
        repo.updateWorkout(
            repo.getWorkout(workoutId)!!.workout.copy(
                note = note,
                endTime = 2_000,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )
        val weId = repo.addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exercise.id, position = 0),
        )
        repo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 1,
                weightKg = 100.0,
                reps = 5
            )
        )
        repo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 2,
                weightKg = 105.0,
                reps = 5
            )
        )
        return repo.getWorkout(workoutId)!!
    }

    @Test
    fun `exercises are added in routine position order`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                squat to RoutineExercise(2, 1, squat.id, 1, 3, 5, 120),
                benchPress to RoutineExercise(1, 1, benchPress.id, 0, 3, 8, 90),
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = 1)

        val details = workoutRepo.getWorkout(id)!!
        assertTrue(details.exercises[0].exercise.id == benchPress.id)
        assertTrue(details.exercises[1].exercise.id == squat.id)
    }
}
