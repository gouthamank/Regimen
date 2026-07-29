package dev.gouthaman.regimen.feature.exercise

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ExerciseLibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val benchPress =
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise("2", "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)
    private val running =
        Exercise("3", "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)
    private val customCurl = Exercise(
        "4",
        "My Curl",
        ExerciseType.STRENGTH,
        MuscleGroup.ARMS,
        Equipment.DUMBBELL,
        isCustom = true
    )

    private fun newViewModel(): ExerciseLibraryViewModel {
        val repo = FakeExerciseRepository().apply { seed(benchPress, squat, running, customCurl) }
        return ExerciseLibraryViewModel(ObserveExercisesUseCase(repo))
    }

    private suspend fun ReceiveTurbine<ExerciseLibraryUiState>.awaitNonEmpty(): ExerciseLibraryUiState {
        var state = awaitItem()
        while (state.exercises.isEmpty()) state = awaitItem()
        return state
    }

    @Test
    fun `no filters shows every exercise`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            assertEquals(4, awaitNonEmpty().exercises.size)
        }
    }

    @Test
    fun `toggling a type filters to that type, toggling it again clears the filter`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            awaitNonEmpty()

            viewModel.toggleType(ExerciseType.CARDIO)
            var state = awaitItem()
            while (state.filters.type != ExerciseType.CARDIO) state = awaitItem()
            assertEquals(listOf(running), state.exercises)

            viewModel.toggleType(ExerciseType.CARDIO)
            state = awaitItem()
            while (state.filters.type != null) state = awaitItem()
            assertEquals(4, state.exercises.size)
        }
    }

    @Test
    fun `toggling a different muscle group replaces the previous one`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            awaitNonEmpty()

            viewModel.toggleMuscleGroup(MuscleGroup.CHEST)
            var state = awaitItem()
            while (state.filters.muscleGroup != MuscleGroup.CHEST) state = awaitItem()
            assertEquals(listOf(benchPress), state.exercises)

            viewModel.toggleMuscleGroup(MuscleGroup.LEGS)
            state = awaitItem()
            while (state.filters.muscleGroup != MuscleGroup.LEGS) state = awaitItem()
            assertEquals(listOf(squat), state.exercises)
        }
    }

    @Test
    fun `toggleCustomOnly restricts to custom exercises`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            awaitNonEmpty()

            viewModel.toggleCustomOnly()
            var state = awaitItem()
            while (!state.filters.customOnly) state = awaitItem()
            assertEquals(listOf(customCurl), state.exercises)
        }
    }

    @Test
    fun `filters combine with AND semantics`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            awaitNonEmpty()

            viewModel.toggleType(ExerciseType.STRENGTH)
            viewModel.toggleEquipment(Equipment.DUMBBELL)

            var state = awaitItem()
            while (state.filters.type != ExerciseType.STRENGTH || state.filters.equipment != Equipment.DUMBBELL) {
                state = awaitItem()
            }
            assertEquals(listOf(customCurl), state.exercises)
        }
    }

    @Test
    fun `setQuery narrows by name or tag match`() = runTest {
        val viewModel = newViewModel()
        viewModel.uiState.test {
            awaitNonEmpty()

            viewModel.setQuery("cardio")
            var state = awaitItem()
            while (state.filters.query != "cardio") state = awaitItem()
            assertEquals(listOf(running), state.exercises)
            assertNull(state.filters.type)
        }
    }
}
