package dev.gouthaman.regimen.feature.active

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.AddExercisesToWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.AddSetUseCase
import dev.gouthaman.regimen.domain.usecase.AdjustRestUseCase
import dev.gouthaman.regimen.domain.usecase.CancelWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteSetUseCase
import dev.gouthaman.regimen.domain.usecase.FinishWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.PauseWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ResumeWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.StartRestUseCase
import dev.gouthaman.regimen.domain.usecase.StopRestUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleDoneExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleSkipExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutNoteUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertCardioUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertSetUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRestAlerts
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActiveWorkoutViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)

    private fun viewModel(
        workoutId: Long,
        workoutRepo: FakeWorkoutRepository,
        appScope: CoroutineScope,
        routineRepo: FakeRoutineRepository = FakeRoutineRepository(),
        preferencesRepo: FakePreferencesRepository = FakePreferencesRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        restAlerts: FakeRestAlerts = FakeRestAlerts(),
        clock: FakeClock = FakeClock(),
    ) = ActiveWorkoutViewModel(
        workoutId = workoutId,
        observeWorkout = ObserveWorkoutUseCase(workoutRepo),
        observeRoutines = ObserveRoutinesUseCase(routineRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        observeExercises = ObserveExercisesUseCase(exerciseRepo),
        upsertSet = UpsertSetUseCase(workoutRepo),
        addSetUseCase = AddSetUseCase(workoutRepo),
        deleteSetUseCase = DeleteSetUseCase(workoutRepo),
        toggleSkipUseCase = ToggleSkipExerciseUseCase(workoutRepo),
        toggleDoneUseCase = ToggleDoneExerciseUseCase(workoutRepo),
        addExercisesUseCase = AddExercisesToWorkoutUseCase(workoutRepo, exerciseRepo),
        upsertCardio = UpsertCardioUseCase(workoutRepo),
        updateNoteUseCase = UpdateWorkoutNoteUseCase(workoutRepo),
        finishWorkoutUseCase = FinishWorkoutUseCase(workoutRepo, clock),
        cancelWorkoutUseCase = CancelWorkoutUseCase(workoutRepo),
        pauseWorkoutUseCase = PauseWorkoutUseCase(workoutRepo, clock),
        resumeWorkoutUseCase = ResumeWorkoutUseCase(workoutRepo, clock),
        startRestUseCase = StartRestUseCase(workoutRepo, clock),
        adjustRestUseCase = AdjustRestUseCase(workoutRepo, clock),
        stopRestUseCase = StopRestUseCase(workoutRepo),
        restAlerts = restAlerts,
        clock = clock,
        appScope = appScope,
    )

    @Test
    fun `starting a rest countdown surfaces it in uiState`() = runTest(dispatcher) {
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
                weightKg = 50.0,
                reps = 10
            )
        )
        val viewModel = viewModel(workoutId, workoutRepo, appScope = this)
        runCurrent()

        assertNull(viewModel.uiState.value.rest)

        viewModel.startRest(we, durationSec = 30)
        runCurrent()

        val rest = viewModel.uiState.value.rest
        assertNotNull(rest)
        assertEquals(30, rest!!.totalSec)
        assertEquals(we, rest.workoutExerciseId)
    }

    @Test
    fun `rest timer auto-completes the target set and marks the exercise done at zero`() =
        runTest(dispatcher) {
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
            val setId = workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = we,
                    setNumber = 1,
                    weightKg = 50.0,
                    reps = 10,
                    isComplete = false
                )
            )
            val restAlerts = FakeRestAlerts()
            val viewModel =
                viewModel(workoutId, workoutRepo, appScope = this, restAlerts = restAlerts)
            runCurrent()

            viewModel.startRest(we, durationSec = 30)
            runCurrent()
            assertNotNull(viewModel.uiState.value.rest)

            advanceTimeBy(15_000)
            runCurrent()
            assertNotNull(viewModel.uiState.value.rest)
            assertTrue(restAlerts.fired.isEmpty())

            advanceUntilIdle()

            assertNull(viewModel.uiState.value.rest)
            assertEquals(1, restAlerts.fired.size)
            assertEquals(workoutId, restAlerts.fired.single().workoutId)

            val updatedWorkout = workoutRepo.getWorkout(workoutId)!!
            val updatedExercise = updatedWorkout.exercises.single()
            assertTrue(updatedExercise.sets.single { it.id == setId }.isComplete)
            assertTrue(updatedExercise.workoutExercise.isDone)
        }

    @Test
    fun `rest timer leaves an invalid set incomplete and surfaces a one-shot event`() =
        runTest(dispatcher) {
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
            val setId = workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = we,
                    setNumber = 1,
                    weightKg = null,
                    reps = null,
                    isComplete = false
                )
            )
            val restAlerts = FakeRestAlerts()
            val viewModel =
                viewModel(workoutId, workoutRepo, appScope = this, restAlerts = restAlerts)
            runCurrent()

            viewModel.startRest(we, durationSec = 5)
            runCurrent()

            advanceUntilIdle()

            assertNull(viewModel.uiState.value.rest)
            assertEquals(1, restAlerts.fired.size)
            val updatedWorkout = workoutRepo.getWorkout(workoutId)!!
            val updatedExercise = updatedWorkout.exercises.single()
            assertFalse(updatedExercise.sets.single { it.id == setId }.isComplete)
            assertFalse(updatedExercise.workoutExercise.isDone)

            viewModel.restSetInvalidEvents.test {
                assertEquals(Unit, awaitItem())
            }
        }

    @Test
    fun `adding rest time clamps so remaining time cannot exceed the original duration`() =
        runTest(dispatcher) {
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
                    weightKg = 50.0,
                    reps = 10
                )
            )
            val clock = FakeClock(0L)
            val viewModel = viewModel(workoutId, workoutRepo, appScope = this, clock = clock)
            runCurrent()

            viewModel.startRest(we, durationSec = 60)
            runCurrent()
            assertEquals(60_000L, viewModel.uiState.value.rest!!.endAtMillis)

            viewModel.addRestTime(30)
            runCurrent()

            assertEquals(60_000L, viewModel.uiState.value.rest!!.endAtMillis)
            assertEquals(60, viewModel.uiState.value.rest!!.totalSec)
        }

    @Test
    fun `adding negative rest time clamps to now, immediately completing the rest`() =
        runTest(dispatcher) {
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
                    weightKg = 50.0,
                    reps = 10
                )
            )
            val clock = FakeClock(0L)
            val restAlerts = FakeRestAlerts()
            val viewModel = viewModel(
                workoutId,
                workoutRepo,
                appScope = this,
                clock = clock,
                restAlerts = restAlerts
            )
            runCurrent()

            viewModel.startRest(we, durationSec = 10)
            runCurrent()
            assertNotNull(viewModel.uiState.value.rest)

            viewModel.addRestTime(-30)
            runCurrent()

            assertNull(viewModel.uiState.value.rest)
            assertEquals(1, restAlerts.fired.size)
        }

    @Test
    fun `adding rest time after some has already elapsed clamps relative to the current time, not the original start`() =
        runTest(dispatcher) {
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
                    weightKg = 50.0,
                    reps = 10
                )
            )
            val clock = FakeClock(0L)
            val viewModel = viewModel(workoutId, workoutRepo, appScope = this, clock = clock)
            runCurrent()

            viewModel.startRest(we, durationSec = 60)
            runCurrent()
            assertEquals(60_000L, viewModel.uiState.value.rest!!.endAtMillis)

            clock.advanceBy(20_000)
            advanceTimeBy(20_000)
            runCurrent()
            assertNotNull(viewModel.uiState.value.rest)

            viewModel.addRestTime(30)
            runCurrent()

            assertEquals(80_000L, viewModel.uiState.value.rest!!.endAtMillis)
            assertEquals(60, viewModel.uiState.value.rest!!.totalSec)
        }

    @Test
    fun `stopping rest early still completes the just-performed set without firing an alert`() =
        runTest(dispatcher) {
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
            val setId = workoutRepo.upsertSet(
                SetEntry(
                    workoutExerciseId = we,
                    setNumber = 1,
                    weightKg = 50.0,
                    reps = 10,
                    isComplete = false
                )
            )
            val restAlerts = FakeRestAlerts()
            val viewModel =
                viewModel(workoutId, workoutRepo, appScope = this, restAlerts = restAlerts)
            runCurrent()

            viewModel.startRest(we, durationSec = 60)
            runCurrent()
            assertNotNull(viewModel.uiState.value.rest)

            viewModel.stopRest()
            runCurrent()

            assertNull(viewModel.uiState.value.rest)
            assertTrue(restAlerts.fired.isEmpty())
            val updatedExercise = workoutRepo.getWorkout(workoutId)!!.exercises.single()
            assertTrue(updatedExercise.sets.single { it.id == setId }.isComplete)
            assertTrue(updatedExercise.workoutExercise.isDone)
        }
}
