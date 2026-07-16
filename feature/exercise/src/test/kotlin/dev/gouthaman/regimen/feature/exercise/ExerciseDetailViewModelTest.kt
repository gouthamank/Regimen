package dev.gouthaman.regimen.feature.exercise

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import dev.gouthaman.regimen.testingandroid.FakeBundleRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExerciseDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val fakeBundleRule = FakeBundleRule()

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val pushUp =
        Exercise(2, "Push Up", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BODYWEIGHT)

    private val exerciseRepo = FakeExerciseRepository()
    private val workoutRepo = FakeWorkoutRepository()
    private val preferencesRepo = FakePreferencesRepository()
    private val routineRepo = FakeRoutineRepository()

    private fun newViewModel(exerciseId: Long) = ExerciseDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("exerciseId" to exerciseId)),
        observeExercise = ObserveExerciseUseCase(exerciseRepo),
        getPersonalRecords = GetPersonalRecordsUseCase(workoutRepo, exerciseRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        observeExerciseHistory = ObserveExerciseHistoryUseCase(workoutRepo),
        deleteExercise = DeleteExerciseUseCase(exerciseRepo, workoutRepo, routineRepo),
    )

    private suspend fun logCompletedSet(exercise: Exercise, weightKg: Double?, reps: Int?) {
        workoutRepo.exerciseLookup = { exercise }
        val workoutId =
            workoutRepo.createWorkout(startTime = System.currentTimeMillis(), routineId = null)
        val weId = workoutRepo.addExercise(
            WorkoutExercise(
                workoutId = workoutId,
                exerciseId = exercise.id,
                position = 0
            )
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 1,
                weightKg = weightKg,
                reps = reps,
                isComplete = true
            )
        )
        workoutRepo.updateWorkout(workoutRepo.getWorkout(workoutId)!!.workout.copy(endTime = System.currentTimeMillis() + 1_000))
    }

    private suspend fun ReceiveTurbine<ExerciseDetailUiState>.awaitLoaded(): ExerciseDetailUiState {
        var state = awaitItem()
        while (!state.loaded) state = awaitItem()
        return state
    }

    @Test
    fun `a weighted exercise resolves its PR as a Weight value`() = runTest {
        exerciseRepo.seed(benchPress)
        logCompletedSet(benchPress, weightKg = 80.0, reps = 5)
        val viewModel = newViewModel(benchPress.id)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertTrue(state.pr is ExercisePrValue.Weight)
            assertEquals("80", (state.pr as ExercisePrValue.Weight).displayValue)
        }
    }

    @Test
    fun `a bodyweight exercise resolves its PR as a Reps value`() = runTest {
        exerciseRepo.seed(pushUp)
        logCompletedSet(pushUp, weightKg = null, reps = 15)
        val viewModel = newViewModel(pushUp.id)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(ExercisePrValue.Reps(15), state.pr)
        }
    }

    @Test
    fun `an exercise with no logged sets has no PR`() = runTest {
        exerciseRepo.seed(benchPress)
        val viewModel = newViewModel(benchPress.id)

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertNull(state.pr)
        }
    }

    @Test
    fun `deleteCurrent marks the exercise deleted when it is unused`() = runTest {
        exerciseRepo.seed(benchPress)
        val viewModel = newViewModel(benchPress.id)

        viewModel.uiState.test { awaitLoaded() }
        viewModel.deleteCurrent()

        viewModel.deleted.test {
            var deleted = awaitItem()
            while (!deleted) deleted = awaitItem()
            assertTrue(deleted)
        }
    }

    @Test
    fun `deleteCurrent reports usage instead of deleting when the exercise is referenced by a workout`() =
        runTest {
            exerciseRepo.seed(benchPress)
            logCompletedSet(benchPress, weightKg = 80.0, reps = 5)
            val viewModel = newViewModel(benchPress.id)

            viewModel.uiState.test { awaitLoaded() }
            viewModel.deleteCurrent()

            viewModel.deleteBlockedInfo.test {
                var info = awaitItem()
                while (info == null) info = awaitItem()
                assertTrue(info.inWorkouts)
                assertTrue(!info.inRoutines)
            }
        }
}
