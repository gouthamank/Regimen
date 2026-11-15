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
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise("2", "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)

    private fun routineWith(vararg exercises: Pair<Exercise, RoutineExercise>) =
        RoutineWithExercises(
            routine = Routine(id = "1", name = "Push Day", position = 0),
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

        assertEquals("1", id)
        assertEquals(0, workoutRepo.getWorkout(id)?.exercises?.size)
    }

    @Test
    fun `missing routine still creates a bare workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "42")

        assertEquals(0, workoutRepo.getWorkout(id)?.exercises?.size)
    }

    @Test
    fun `routine with no prior session prefills target reps and no weight`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                benchPress to RoutineExercise(
                    "1",
                    "1",
                    benchPress.id,
                    0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                ),
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

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
                        "1",
                        "1",
                        benchPress.id,
                        0,
                        targetSets = 2,
                        targetReps = 8,
                        targetRestSec = 90
                    ),
                ),
            )
            workoutRepo.addPriorSession(
                routineId = "1",
                exercise = benchPress,
                note = "Add 2.5kg next time",
            )
            val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

            val id = useCase(routineId = "1")

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
            routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 2, 8, 90)),
        )
        workoutRepo.addPriorSession(routineId = "1", exercise = benchPress, note = "   ")
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        assertNull(workoutRepo.getWorkout(id)!!.workout.note)
    }

    @Test
    fun `routine prefills exercise notes from the most recent session of the same routine`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            val routineRepo = FakeRoutineRepository()
            routineRepo.seed(
                routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 2, 8, 90)),
            )
            workoutRepo.addPriorSession(
                routineId = "1",
                exercise = benchPress,
                note = null,
                exerciseNote = "Elbows tucked",
            )
            val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

            val id = useCase(routineId = "1")

            assertEquals(
                "Elbows tucked",
                workoutRepo.getWorkout(id)!!.exercises[0].workoutExercise.notes,
            )
        }

    @Test
    fun `a blank prior exercise note is not carried forward`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 2, 8, 90)),
        )
        workoutRepo.addPriorSession(
            routineId = "1",
            exercise = benchPress,
            note = null,
            exerciseNote = "   ",
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        assertNull(workoutRepo.getWorkout(id)!!.exercises[0].workoutExercise.notes)
    }

    @Test
    fun `target sets below one still logs a single set`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                benchPress to RoutineExercise(
                    "1",
                    "1",
                    benchPress.id,
                    0,
                    targetSets = 0,
                    targetReps = 8,
                    targetRestSec = 90
                )
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        assertEquals(1, workoutRepo.getWorkout(id)!!.exercises[0].sets.size)
    }

    /** Adds a finished session logging [exercise] - [sets] as (weightKg, reps), all completed,
     * as a real finished session's sets always are once unlogged ones are pruned. Empty [sets]
     * models an exercise skipped that session. */
    private suspend fun FakeWorkoutRepository.addPriorSession(
        routineId: String?,
        exercise: Exercise,
        note: String? = null,
        exerciseNote: String? = null,
        startTime: Long = 1_000,
        sets: List<Pair<Double?, Int>> = listOf(100.0 to 5, 105.0 to 5),
    ) {
        val workoutId = createWorkout(startTime = startTime, routineId = routineId)
        updateWorkout(
            getWorkout(workoutId)!!.workout.copy(
                note = note,
                endTime = startTime + 1_000,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )
        val weId = addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exercise.id, position = 0),
        )
        val we = getWorkout(workoutId)!!.exercises.first { it.workoutExercise.id == weId }
        updateExercise(we.workoutExercise.copy(notes = exerciseNote, isSkipped = sets.isEmpty()))
        sets.forEachIndexed { i, (weightKg, reps) ->
            upsertSet(
                SetEntry(
                    workoutExerciseId = weId,
                    setNumber = i + 1,
                    weightKg = weightKg,
                    reps = reps,
                    isComplete = true,
                )
            )
        }
    }

    @Test
    fun `exercises are added in routine position order`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(
                squat to RoutineExercise("2", "1", squat.id, 1, 3, 5, 120),
                benchPress to RoutineExercise("1", "1", benchPress.id, 0, 3, 8, 90),
            ),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        val details = workoutRepo.getWorkout(id)!!
        assertTrue(details.exercises[0].exercise.id == benchPress.id)
        assertTrue(details.exercises[1].exercise.id == squat.id)
    }

    @Test
    fun `an exercise skipped last session prefills from the last session that logged it`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            val routineRepo = FakeRoutineRepository()
            routineRepo.seed(
                routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 2, 8, 90)),
            )
            workoutRepo.addPriorSession(
                routineId = "1",
                exercise = benchPress,
                startTime = 1_000,
                sets = listOf(80.0 to 6, 82.5 to 6),
                exerciseNote = "Pause at the bottom",
            )
            workoutRepo.addPriorSession(
                routineId = "1",
                exercise = benchPress,
                note = "Short on time",
                startTime = 5_000,
                sets = emptyList(),
            )
            val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

            val id = useCase(routineId = "1")

            val details = workoutRepo.getWorkout(id)!!
            val sets = details.exercises[0].sets.sortedBy { it.setNumber }
            assertEquals(80.0, sets[0].weightKg)
            assertEquals(6, sets[0].reps)
            assertEquals(82.5, sets[1].weightKg)
            assertEquals("Pause at the bottom", details.exercises[0].workoutExercise.notes)
            // The session note still comes from the routine's most recent session.
            assertEquals("Short on time", details.workout.note)
        }

    @Test
    fun `an exercise never logged in this routine falls back to any workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 2, 8, 90)),
        )
        workoutRepo.addPriorSession(
            routineId = null,
            exercise = benchPress,
            sets = listOf(60.0 to 10, 60.0 to 9),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        val sets = workoutRepo.getWorkout(id)!!.exercises[0].sets.sortedBy { it.setNumber }
        assertEquals(60.0, sets[0].weightKg)
        assertEquals(10, sets[0].reps)
        assertEquals(9, sets[1].reps)
    }

    @Test
    fun `this routine's history wins over a more recent session elsewhere`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(
            routineWith(benchPress to RoutineExercise("1", "1", benchPress.id, 0, 1, 8, 90)),
        )
        workoutRepo.addPriorSession(
            routineId = "1",
            exercise = benchPress,
            startTime = 1_000,
            sets = listOf(100.0 to 5),
        )
        workoutRepo.addPriorSession(
            routineId = null,
            exercise = benchPress,
            startTime = 5_000,
            sets = listOf(70.0 to 12),
        )
        val useCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock)

        val id = useCase(routineId = "1")

        val set = workoutRepo.getWorkout(id)!!.exercises[0].sets.single()
        assertEquals(100.0, set.weightKg)
        assertEquals(5, set.reps)
    }
}
