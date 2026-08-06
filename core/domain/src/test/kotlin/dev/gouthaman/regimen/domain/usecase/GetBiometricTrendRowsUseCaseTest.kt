package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetBiometricTrendRowsUseCaseTest {

    private suspend fun completedWorkout(
        workoutRepo: FakeWorkoutRepository,
        startTime: Long,
        routineId: String?,
    ): String {
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
    fun `combines by routine and includes a synthetic combined row`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val routineRepo = FakeRoutineRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()

        val r1 = routineRepo.saveRoutine(null, "Push Day", emptyList())
        val r2 = routineRepo.saveRoutine(null, "Legs", emptyList())
        routineRepo.saveRoutine(
            null,
            "Unused Routine",
            emptyList()
        ) // no workouts - must not appear

        val w1 = completedWorkout(workoutRepo, startTime = 1_000, routineId = r1)
        val w2 = completedWorkout(workoutRepo, startTime = 2_000, routineId = r1)
        val w3 = completedWorkout(workoutRepo, startTime = 3_000, routineId = r2) // no biometrics
        val w4 = completedWorkout(workoutRepo, startTime = 4_000, routineId = null) // freeform

        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w1,
                avgBpm = 100,
                activeCaloriesKcal = 200.0,
                fetchedAt = 0
            ),
        )
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w2,
                avgBpm = 110,
                activeCaloriesKcal = 210.0,
                fetchedAt = 0
            ),
        )
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w4,
                avgBpm = 90,
                fetchedAt = 0
            )
        )

        val rows = GetBiometricTrendRowsUseCase(workoutRepo, routineRepo, biometricsRepo)().first()

        assertEquals(3, rows.size)
        val combined = rows.first { it.routineId == null }
        assertEquals(listOf(100f, 110f, 90f), combined.avgBpmTrend)
        assertEquals(listOf(200f, 210f), combined.caloriesTrend)

        val row1 = rows.first { it.routineId == r1 }
        assertEquals("Push Day", row1.routineName)
        assertEquals(listOf(100f, 110f), row1.avgBpmTrend)
        assertEquals(listOf(200f, 210f), row1.caloriesTrend)

        val row2 = rows.first { it.routineId == r2 }
        assertEquals("Legs", row2.routineName)
        assertEquals(emptyList<Float>(), row2.avgBpmTrend)
        assertEquals(emptyList<Float>(), row2.caloriesTrend)
    }
}
