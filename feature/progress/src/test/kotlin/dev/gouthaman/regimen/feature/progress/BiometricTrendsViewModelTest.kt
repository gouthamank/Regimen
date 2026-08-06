package dev.gouthaman.regimen.feature.progress

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.GetBiometricTrendRowsUseCase
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BiometricTrendsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val workoutRepo = FakeWorkoutRepository()
    private val routineRepo = FakeRoutineRepository()
    private val biometricsRepo = FakeWorkoutBiometricsRepository()

    private fun newViewModel() = BiometricTrendsViewModel(
        getBiometricTrendRows = GetBiometricTrendRowsUseCase(
            workoutRepo,
            routineRepo,
            biometricsRepo
        ),
    )

    private suspend fun completedWorkout(startTime: Long, routineId: String?): String {
        val id = workoutRepo.createWorkout(startTime = startTime, routineId = routineId)
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.COMPLETE,
                endTime = startTime + 1_000,
            ),
        )
        return id
    }

    @Test
    fun `rows include a combined row plus one per routine with a completed workout`() = runTest {
        val routineId = routineRepo.saveRoutine(null, "Push Day", emptyList())
        val w1 = completedWorkout(1_000, routineId)
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w1,
                avgBpm = 100,
                activeCaloriesKcal = 300.0,
                fetchedAt = 0
            ),
        )
        val viewModel = newViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            while (!state.loaded) state = awaitItem()
            assertEquals(2, state.rows.size)
            assertEquals(listOf(null, routineId), state.rows.map { it.routineId })
            assertEquals(listOf(100f), state.rows[1].avgBpmTrend)
            assertEquals(listOf(300f), state.rows[1].caloriesTrend)
        }
    }
}
