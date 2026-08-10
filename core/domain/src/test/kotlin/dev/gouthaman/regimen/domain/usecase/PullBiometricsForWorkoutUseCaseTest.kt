package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PullBiometricsForWorkoutUseCaseTest {

    private suspend fun completedWorkout(
        workoutRepo: FakeWorkoutRepository,
        startTime: Long = 1_000,
        endTime: Long = 5_000,
    ): String {
        val id = workoutRepo.createWorkout(startTime = startTime, routineId = null)
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.COMPLETE,
                endTime = endTime,
            ),
        )
        return id
    }

    @Test
    fun `a found sample is persisted as a WorkoutBiometrics row`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            sampleForRange = HealthConnectBiometricsSample(
                avgBpm = 120,
                maxBpm = 150,
                activeCaloriesKcal = 300.5,
                sourcePackageName = "com.google.android.apps.healthdata",
            ),
        )
        val clock = FakeClock(9_000L)
        val workoutId = completedWorkout(workoutRepo)
        val useCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)

        val found = useCase(workoutId)

        assertTrue(found)
        val saved = biometricsRepo.get(workoutId)
        assertEquals(120, saved?.avgBpm)
        assertEquals(150, saved?.maxBpm)
        assertEquals(300.5, saved?.activeCaloriesKcal)
        assertEquals("com.google.android.apps.healthdata", saved?.sourcePackageName)
        assertEquals(9_000L, saved?.fetchedAt)
        assertEquals(1_000L to 5_000L, healthConnectRepo.lastQueriedRange)
    }

    @Test
    fun `nothing found in Health Connect leaves no row and returns false`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository(sampleForRange = null)
        val clock = FakeClock()
        val workoutId = completedWorkout(workoutRepo)
        val useCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)

        val found = useCase(workoutId)

        assertFalse(found)
        assertNull(biometricsRepo.get(workoutId))
    }

    @Test
    fun `a workout that hasn't finished yet is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            sampleForRange = HealthConnectBiometricsSample(
                avgBpm = 120,
                maxBpm = null,
                activeCaloriesKcal = null,
                sourcePackageName = null,
            ),
        )
        val clock = FakeClock()
        val workoutId = workoutRepo.createWorkout(startTime = 1_000, routineId = null)
        val useCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)

        val found = useCase(workoutId)

        assertFalse(found)
        assertNull(biometricsRepo.get(workoutId))
        assertNull(healthConnectRepo.lastQueriedRange)
    }

    @Test
    fun `re-pulling an existing row overwrites its stats but keeps the cached heart-rate series`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            val biometricsRepo = FakeWorkoutBiometricsRepository()
            val healthConnectRepo = FakeHealthConnectRepository(
                sampleForRange = HealthConnectBiometricsSample(
                    avgBpm = 130,
                    maxBpm = 160,
                    activeCaloriesKcal = 400.0,
                    sourcePackageName = "com.google.android.apps.healthdata",
                ),
            )
            val clock = FakeClock(9_000L)
            val workoutId = completedWorkout(workoutRepo)
            biometricsRepo.upsert(
                WorkoutBiometrics(
                    id = "",
                    workoutId = workoutId,
                    avgBpm = 100,
                    fetchedAt = 1_000L,
                    heartRateSeries = listOf(90, 95, 100),
                ),
            )
            val useCase =
                PullBiometricsForWorkoutUseCase(
                    healthConnectRepo,
                    workoutRepo,
                    biometricsRepo,
                    clock
                )

            useCase(workoutId)

            val saved = biometricsRepo.get(workoutId)
            assertEquals(130, saved?.avgBpm)
            assertEquals(listOf(90, 95, 100), saved?.heartRateSeries)
        }

    @Test
    fun `a missing workout is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository()
        val clock = FakeClock()
        val useCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)

        val found = useCase("does-not-exist")

        assertFalse(found)
    }
}
