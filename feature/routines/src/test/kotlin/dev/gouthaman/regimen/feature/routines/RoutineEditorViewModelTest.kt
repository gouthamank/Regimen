package dev.gouthaman.regimen.feature.routines

import androidx.lifecycle.SavedStateHandle
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutineUseCase
import dev.gouthaman.regimen.domain.usecase.SaveRoutineUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import dev.gouthaman.regimen.testingandroid.FakeBundleRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RoutineEditorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val fakeBundleRule = FakeBundleRule()

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise(2, "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)
    private val deadlift =
        Exercise(3, "Deadlift", ExerciseType.STRENGTH, MuscleGroup.BACK, Equipment.BARBELL)

    private fun newViewModel(restDefaultSec: Int = 90): RoutineEditorViewModel {
        val exerciseRepo = FakeExerciseRepository().apply { seed(benchPress, squat, deadlift) }
        val routineRepo = FakeRoutineRepository()
        val preferencesRepo = FakePreferencesRepository().apply {
            seed(UserPreferences(restDefaultSec = restDefaultSec))
        }
        return RoutineEditorViewModel(
            savedStateHandle = SavedStateHandle(mapOf("routineId" to 0L)),
            observeExercises = ObserveExercisesUseCase(exerciseRepo),
            observeRoutine = ObserveRoutineUseCase(routineRepo),
            observePreferences = ObservePreferencesUseCase(preferencesRepo),
            saveRoutine = SaveRoutineUseCase(routineRepo),
        )
    }

    @Test
    fun `checking exercises appends them with default targets`() = runTest {
        val viewModel = newViewModel(restDefaultSec = 60)

        viewModel.setExercises(listOf(benchPress.id, squat.id))

        val exercises = viewModel.uiState.value.exercises
        assertEquals(2, exercises.size)
        assertEquals(benchPress.id, exercises[0].exerciseId)
        assertEquals(squat.id, exercises[1].exerciseId)
        exercises.forEach {
            assertEquals(3, it.targetSets)
            assertEquals(10, it.targetReps)
            assertEquals(60, it.targetRestSec)
        }
    }

    @Test
    fun `customized entries survive a reconciliation that keeps their id`() = runTest {
        val viewModel = newViewModel()
        viewModel.setExercises(listOf(benchPress.id, squat.id))
        viewModel.setSets(index = 0, value = 5)

        viewModel.setExercises(listOf(benchPress.id, squat.id, deadlift.id))

        val exercises = viewModel.uiState.value.exercises
        assertEquals(3, exercises.size)
        assertEquals(5, exercises[0].targetSets)
        assertEquals(benchPress.id, exercises[0].exerciseId)
        assertEquals(3, exercises[2].targetSets)
        assertEquals(deadlift.id, exercises[2].exerciseId)
    }

    @Test
    fun `unchecking an exercise drops it`() = runTest {
        val viewModel = newViewModel()
        viewModel.setExercises(listOf(benchPress.id, squat.id))

        viewModel.setExercises(listOf(squat.id))

        val exercises = viewModel.uiState.value.exercises
        assertEquals(1, exercises.size)
        assertEquals(squat.id, exercises[0].exerciseId)
    }

    @Test
    fun `kept entries keep their relative order and additions are appended`() = runTest {
        val viewModel = newViewModel()
        viewModel.setExercises(listOf(squat.id, benchPress.id))

        viewModel.setExercises(listOf(benchPress.id, deadlift.id, squat.id))

        val exercises = viewModel.uiState.value.exercises
        assertEquals(listOf(squat.id, benchPress.id, deadlift.id), exercises.map { it.exerciseId })
    }

    @Test
    fun `unknown ids from the picker are ignored`() = runTest {
        val viewModel = newViewModel()

        viewModel.setExercises(listOf(benchPress.id, 999L))

        val exercises = viewModel.uiState.value.exercises
        assertEquals(listOf(benchPress.id), exercises.map { it.exerciseId })
    }
}
