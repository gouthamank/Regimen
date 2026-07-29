package dev.gouthaman.regimen.feature.progress

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitLabel
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProgressViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val benchPress =
        Exercise("1", "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val inclinePress =
        Exercise("2", "Incline Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val pullUp =
        Exercise("3", "Pull-up", ExerciseType.STRENGTH, MuscleGroup.BACK, Equipment.BODYWEIGHT)

    private val exerciseRepo =
        FakeExerciseRepository().apply { seed(benchPress, inclinePress, pullUp) }
    private val workoutRepo = FakeWorkoutRepository().apply {
        exerciseLookup = { id -> listOf(benchPress, inclinePress, pullUp).first { it.id == id } }
    }
    private val preferencesRepo = FakePreferencesRepository()

    private fun newViewModel() = ProgressViewModel(
        getPersonalRecords = GetPersonalRecordsUseCase(workoutRepo, exerciseRepo),
        getWorkoutFrequency = GetWorkoutFrequencyUseCase(workoutRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
    )

    private suspend fun loggedSet(
        exerciseId: String,
        weightKg: Double?,
        reps: Int?,
        startTime: Long = System.currentTimeMillis(),
    ) {
        val workoutId = workoutRepo.createWorkout(startTime = startTime, routineId = null)
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(workoutId)!!.workout.copy(
                endTime = startTime + 1_000,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )
        val weId = workoutRepo.addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = 0),
        )
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = weId,
                setNumber = 1,
                weightKg = weightKg,
                reps = reps,
                isComplete = true
            ),
        )
    }

    private suspend fun ReceiveTurbine<ProgressUiState>.awaitLoaded(): ProgressUiState {
        var state = awaitItem()
        while (!state.loaded) state = awaitItem()
        return state
    }

    @Test
    fun `weighted exercises resolve to a weight PR, bodyweight exercises resolve to a reps PR`() =
        runTest {
            loggedSet(benchPress.id, weightKg = 100.0, reps = 5)
            loggedSet(pullUp.id, weightKg = null, reps = 12)
            val viewModel = newViewModel()

            viewModel.uiState.test {
                val state = awaitLoaded()
                val chest =
                    state.personalRecordGroups.single { it.muscleGroup == MuscleGroup.CHEST }
                val back = state.personalRecordGroups.single { it.muscleGroup == MuscleGroup.BACK }

                val chestValue = chest.records.single().value
                assertTrue(chestValue is PersonalRecordValue.Weight)
                assertEquals("100", (chestValue as PersonalRecordValue.Weight).displayValue)
                assertEquals(UnitLabel.KG, chestValue.unitLabel)

                val backValue = back.records.single().value
                assertTrue(backValue is PersonalRecordValue.Reps)
                assertEquals(12, (backValue as PersonalRecordValue.Reps).count)
            }
        }

    @Test
    fun `groups are ordered by MuscleGroup declaration order, not insertion order`() = runTest {
        loggedSet(pullUp.id, weightKg = null, reps = 12)
        loggedSet(benchPress.id, weightKg = 100.0, reps = 5)
        val viewModel = newViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            assertEquals(
                listOf(MuscleGroup.CHEST, MuscleGroup.BACK),
                state.personalRecordGroups.map { it.muscleGroup },
            )
        }
    }

    @Test
    fun `records within a group stay ordered heaviest first`() = runTest {
        loggedSet(inclinePress.id, weightKg = 60.0, reps = 8)
        loggedSet(benchPress.id, weightKg = 100.0, reps = 5)
        val viewModel = newViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            val chest = state.personalRecordGroups.single { it.muscleGroup == MuscleGroup.CHEST }
            assertEquals(
                listOf(benchPress.id, inclinePress.id),
                chest.records.map { it.exerciseId })
        }
    }

    @Test
    fun `weight PR is converted to the user's preferred unit`() = runTest {
        preferencesRepo.seed(UserPreferences(weightUnit = UnitSystem.IMPERIAL))
        loggedSet(benchPress.id, weightKg = 100.0, reps = 5)
        val viewModel = newViewModel()

        viewModel.uiState.test {
            val state = awaitLoaded()
            val value =
                state.personalRecordGroups.single().records.single().value as PersonalRecordValue.Weight
            assertEquals("220.46", value.displayValue)
            assertEquals(UnitLabel.LB, value.unitLabel)
        }
    }

    @Test
    fun `setRange updates the exposed range and the recomputed frequency window`() = runTest {
        val viewModel = newViewModel()

        viewModel.uiState.test {
            val initial = awaitLoaded()
            assertEquals(HistoryRange.THREE_MONTHS, initial.range)
            assertEquals(13, initial.frequency.size)

            viewModel.setRange(HistoryRange.ONE_YEAR)
            var state = awaitItem()
            while (state.range != HistoryRange.ONE_YEAR || state.frequency.size != 52) state =
                awaitItem()
            assertEquals(HistoryRange.ONE_YEAR, state.range)
            assertEquals(52, state.frequency.size)
        }
    }
}
