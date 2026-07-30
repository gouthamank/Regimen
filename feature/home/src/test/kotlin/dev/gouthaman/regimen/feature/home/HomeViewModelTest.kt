package dev.gouthaman.regimen.feature.home

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.AuthAccount
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.GetHomeSummaryUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveAccountStatusUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveActiveWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.testing.FakeAuthRepository
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeMeasurementRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val routineRepo = FakeRoutineRepository()
    private val workoutRepo = FakeWorkoutRepository()
    private val preferencesRepo = FakePreferencesRepository()
    private val measurementRepo = FakeMeasurementRepository()
    private val authRepo = FakeAuthRepository()
    private val clock = FakeClock(1_000L)

    private fun newViewModel() = HomeViewModel(
        getHomeSummary = GetHomeSummaryUseCase(workoutRepo),
        observeRoutines = ObserveRoutinesUseCase(routineRepo),
        observeHistory = ObserveHistoryUseCase(workoutRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        getWorkoutFrequency = GetWorkoutFrequencyUseCase(workoutRepo),
        observeMeasurementTypes = ObserveMeasurementTypesUseCase(measurementRepo),
        observeMeasurements = ObserveMeasurementsUseCase(measurementRepo),
        observeActiveWorkoutId = ObserveActiveWorkoutIdUseCase(workoutRepo),
        observeAccountStatus = ObserveAccountStatusUseCase(authRepo),
        startWorkoutUseCase = StartWorkoutUseCase(workoutRepo, routineRepo, clock),
        getInProgressWorkoutId = GetInProgressWorkoutIdUseCase(workoutRepo),
    )

    private fun routine(id: String, position: Int) =
        RoutineWithExercises(Routine(id, "Routine $id", position), emptyList())

    private suspend fun completedWorkoutFor(routineId: String, startTime: Long): String {
        val id = workoutRepo.createWorkout(startTime = startTime, routineId = routineId)
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(id)!!.workout.copy(
                endTime = startTime + 1_000,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )
        return id
    }

    @Test
    fun `hasRoutines and isEstablished reflect data presence`() = runTest {
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertFalse(state.hasRoutines)
            assertFalse(state.isEstablished)

            routineRepo.seed(routine("1", 0))
            completedWorkoutFor(routineId = "1", startTime = System.currentTimeMillis())

            state = awaitItem()
            while (!state.hasRoutines || !state.isEstablished) state = awaitItem()
            assertTrue(state.hasRoutines)
            assertTrue(state.isEstablished)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hasWorkoutInProgress reflects an unfinished workout`() = runTest {
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertFalse(state.hasWorkoutInProgress)

            workoutRepo.createWorkout(startTime = System.currentTimeMillis(), routineId = null)

            state = awaitItem()
            while (!state.hasWorkoutInProgress) state = awaitItem()
            assertTrue(state.hasWorkoutInProgress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `weekly volume is converted to display units`() = runTest {
        val workoutId = completedWorkoutFor(routineId = "1", startTime = System.currentTimeMillis())
        val weId = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = "1",
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                isComplete = true
            ),
        )
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.workoutsThisWeek == 0) state = awaitItem()
            assertEquals(1, state.workoutsThisWeek)
            assertEquals("500", state.volumeThisWeek.displayValue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `firstName is null when signed out`() = runTest {
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals(null, state.firstName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `firstName is the first token of the signed-in account's display name`() = runTest {
        authRepo.seedSignedIn(AuthAccount(uid = "u1", email = "a@b.com", displayName = "Jane Doe"))
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals("Jane", state.firstName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `firstName is the whole display name when it has no space`() = runTest {
        authRepo.seedSignedIn(AuthAccount(uid = "u1", email = "a@b.com", displayName = "Jane"))
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals("Jane", state.firstName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `firstName is null when the display name is blank`() = runTest {
        authRepo.seedSignedIn(AuthAccount(uid = "u1", email = "a@b.com", displayName = "   "))
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals(null, state.firstName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startWorkout resumes an in-progress workout instead of starting a new one`() = runTest {
        val existingId =
            workoutRepo.createWorkout(startTime = System.currentTimeMillis(), routineId = null)
        val viewModel = newViewModel()

        viewModel.startedWorkout.test {
            viewModel.startWorkout(routineId = "42")
            assertEquals(existingId, awaitItem())
        }
    }

    @Test
    fun `startWorkout starts a new workout when none is in progress`() = runTest {
        routineRepo.seed(routine("7", position = 0))
        val viewModel = newViewModel()

        viewModel.startedWorkout.test {
            viewModel.startWorkout(routineId = "7")
            val newId = awaitItem()
            assertEquals("7", workoutRepo.getWorkout(newId)!!.workout.routineId)
        }
    }
}
