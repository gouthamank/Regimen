package dev.gouthaman.regimen.feature.active

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import dev.gouthaman.regimen.testingandroid.FakeBundleRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val fakeBundleRule = FakeBundleRule()

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise(2, "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)
    private val running =
        Exercise(3, "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    private fun viewModel(
        workoutId: Long,
        workoutRepo: FakeWorkoutRepository,
        routineRepo: FakeRoutineRepository = FakeRoutineRepository(),
        preferencesRepo: FakePreferencesRepository = FakePreferencesRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
    ) = WorkoutSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf("workoutId" to workoutId)),
        observeWorkout = ObserveWorkoutUseCase(workoutRepo),
        observeRoutines = ObserveRoutinesUseCase(routineRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        getPersonalRecords = GetPersonalRecordsUseCase(workoutRepo, exerciseRepo),
        saveAsRoutineUseCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo),
    )

    private suspend fun finishedWorkout(workoutRepo: FakeWorkoutRepository, workoutId: Long) {
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(workoutId)!!.workout.copy(
                endTime = 5_000,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )
    }

    @Test
    fun `a missing workout reports not found`() = runTest {
        val viewModel = viewModel(999, FakeWorkoutRepository())

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.loaded)
            assertTrue(state.notFound)
        }
    }

    @Test
    fun `volume only sums completed sets, formatted in the current unit system`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
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
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = we,
                setNumber = 2,
                weightKg = 999.0,
                reps = 5,
                isComplete = false
            )
        )
        finishedWorkout(workoutRepo, workoutId)
        val preferencesRepo = FakePreferencesRepository()
        preferencesRepo.seed(UserPreferences(weightUnit = UnitSystem.METRIC))
        val viewModel = viewModel(workoutId, workoutRepo, preferencesRepo = preferencesRepo)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.completedSets)
            assertEquals("500", state.volume.displayValue)
        }
    }

    @Test
    fun `an exercise that beats its prior best weight counts as a PR hit`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            workoutRepo.exerciseLookup = { benchPress }
            val exerciseRepo = FakeExerciseRepository()
            exerciseRepo.seed(benchPress)

            val pastId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
            val pastWe = workoutRepo.addExercise(
                WorkoutExercise(
                    workoutId = pastId,
                    exerciseId = benchPress.id,
                    position = 0
                )
            )
            workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = pastWe,
                    setNumber = 1,
                    weightKg = 90.0,
                    reps = 5,
                    isComplete = true
                )
            )
            finishedWorkout(workoutRepo, pastId)

            val workoutId = workoutRepo.createWorkout(startTime = 2_000, routineId = null)
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
                    weightKg = 100.0,
                    reps = 5,
                    isComplete = true
                )
            )
            finishedWorkout(workoutRepo, workoutId)

            val viewModel = viewModel(workoutId, workoutRepo, exerciseRepo = exerciseRepo)

            viewModel.uiState.test {
                assertEquals(listOf("Bench Press"), awaitItem().prsHit)
            }
        }

    @Test
    fun `an exercise that only ties its prior best weight is not a PR hit`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)

        val pastId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val pastWe = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = pastId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = pastWe,
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, pastId)

        val workoutId = workoutRepo.createWorkout(startTime = 2_000, routineId = null)
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
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, workoutId)

        val viewModel = viewModel(workoutId, workoutRepo, exerciseRepo = exerciseRepo)

        viewModel.uiState.test {
            assertEquals(emptyList<String>(), awaitItem().prsHit)
        }
    }

    @Test
    fun `a first-ever completed exercise counts as a PR hit`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
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
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, workoutId)

        val viewModel = viewModel(workoutId, workoutRepo, exerciseRepo = exerciseRepo)

        viewModel.uiState.test {
            assertEquals(listOf("Bench Press"), awaitItem().prsHit)
        }
    }

    @Test
    fun `an exercise below its overall best weight is not a PR hit`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(benchPress)

        val pastId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val pastWe = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = pastId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = pastWe,
                setNumber = 1,
                weightKg = 120.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, pastId)

        val workoutId = workoutRepo.createWorkout(startTime = 2_000, routineId = null)
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
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, workoutId)

        val viewModel = viewModel(workoutId, workoutRepo, exerciseRepo = exerciseRepo)

        viewModel.uiState.test {
            assertEquals(emptyList<String>(), awaitItem().prsHit)
        }
    }

    @Test
    fun `cardio exercises never count toward a PR hit`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { running }
        val exerciseRepo = FakeExerciseRepository()
        exerciseRepo.seed(running)

        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = running.id,
                position = 0
            )
        )
        finishedWorkout(workoutRepo, workoutId)

        val viewModel = viewModel(workoutId, workoutRepo, exerciseRepo = exerciseRepo)

        viewModel.uiState.test {
            assertEquals(emptyList<String>(), awaitItem().prsHit)
        }
    }

    @Test
    fun `a freeform session with a strength exercise can be saved as a routine`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        finishedWorkout(workoutRepo, workoutId)
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().canSaveAsRoutine)
        }
    }

    @Test
    fun `a routine-based session cannot be saved as a routine again`() = runTest {
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(RoutineWithExercises(Routine(1, "Push Day", 0), emptyList()))
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = 1)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        finishedWorkout(workoutRepo, workoutId)
        val viewModel = viewModel(workoutId, workoutRepo, routineRepo)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Push Day", state.routineName)
            assertFalse(state.canSaveAsRoutine)
        }
    }

    @Test
    fun `a freeform session with only cardio cannot be saved as a routine`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { running }
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = running.id,
                position = 0
            )
        )
        finishedWorkout(workoutRepo, workoutId)
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertFalse(awaitItem().canSaveAsRoutine)
        }
    }

    @Test
    fun `saveAsRoutine persists a new routine from this session`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
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
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            )
        )
        finishedWorkout(workoutRepo, workoutId)
        val routineRepo = FakeRoutineRepository()
        val viewModel = viewModel(workoutId, workoutRepo, routineRepo)
        viewModel.uiState.test { awaitItem() }

        viewModel.saveAsRoutine("My New Routine")

        val saved = routineRepo.getRoutine(1)
        assertEquals("My New Routine", saved?.routine?.name)
    }
}
