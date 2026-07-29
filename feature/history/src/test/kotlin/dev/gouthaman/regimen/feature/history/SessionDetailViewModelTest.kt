package dev.gouthaman.regimen.feature.history

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.DeleteWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.EditWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.RepeatWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import dev.gouthaman.regimen.testingandroid.FakeBundleRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val fakeBundleRule = FakeBundleRule()

    private val clock = FakeClock(1_000L)
    private val benchPress =
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val running =
        Exercise("2", "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)

    private fun viewModel(
        workoutId: String,
        workoutRepo: FakeWorkoutRepository,
        routineRepo: FakeRoutineRepository = FakeRoutineRepository(),
        preferencesRepo: FakePreferencesRepository = FakePreferencesRepository(),
    ) = SessionDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("workoutId" to workoutId)),
        observeWorkout = ObserveWorkoutUseCase(workoutRepo),
        observeRoutines = ObserveRoutinesUseCase(routineRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        deleteWorkoutUseCase = DeleteWorkoutUseCase(workoutRepo),
        saveAsRoutineUseCase = SaveWorkoutAsRoutineUseCase(workoutRepo, routineRepo),
        getInProgressWorkoutId = GetInProgressWorkoutIdUseCase(workoutRepo),
        repeatWorkoutUseCase = RepeatWorkoutUseCase(
            workoutRepo,
            StartWorkoutUseCase(workoutRepo, routineRepo, clock),
            clock
        ),
        editWorkoutUseCase = EditWorkoutUseCase(workoutRepo),
    )

    @Test
    fun `a missing workout reports not found`() = runTest {
        val viewModel = viewModel("missing", FakeWorkoutRepository())

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.loaded)
            assertTrue(state.notFound)
        }
    }

    @Test
    fun `a workout with no strength exercises cannot be saved as a routine`() = runTest {
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
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertFalse(awaitItem().canSaveAsRoutine)
        }
    }

    @Test
    fun `a workout with a strength exercise can be saved as a routine`() = runTest {
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
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().canSaveAsRoutine)
        }
    }

    @Test
    fun `a mix of strength and cardio exercises can still be saved as a routine`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val lookup = mapOf(benchPress.id to benchPress, running.id to running)
        workoutRepo.exerciseLookup = { id -> lookup.getValue(id) }
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = running.id,
                position = 0
            )
        )
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 1
            )
        )
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertTrue(awaitItem().canSaveAsRoutine)
        }
    }

    @Test
    fun `a routine-based session resolves its routine name`() = runTest {
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(RoutineWithExercises(Routine("1", "Push Day", 0), emptyList()))
        val workoutRepo = FakeWorkoutRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = "1")
        val viewModel = viewModel(workoutId, workoutRepo, routineRepo)

        viewModel.uiState.test {
            assertEquals("Push Day", awaitItem().routineName)
        }
    }

    @Test
    fun `a routine-based session can still be saved as a routine`() = runTest {
        val routineRepo = FakeRoutineRepository()
        routineRepo.seed(RoutineWithExercises(Routine("1", "Push Day", 0), emptyList()))
        val workoutRepo = FakeWorkoutRepository()
        workoutRepo.exerciseLookup = { benchPress }
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = "1")
        workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = benchPress.id,
                position = 0
            )
        )
        val viewModel = viewModel(workoutId, workoutRepo, routineRepo)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals("Push Day", state.routineName)
            assertTrue(state.canSaveAsRoutine)
        }
    }

    @Test
    fun `a freeform session has no routine name`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertNull(awaitItem().routineName)
        }
    }

    @Test
    fun `a blank note is surfaced as null`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        workoutRepo.updateWorkout(workoutRepo.getWorkout(workoutId)!!.workout.copy(note = "   "))
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.uiState.test {
            assertNull(awaitItem().note)
        }
    }

    @Test
    fun `delete removes the workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val viewModel = viewModel(workoutId, workoutRepo)

        viewModel.delete()

        assertNull(workoutRepo.getWorkout(workoutId))
    }
}
