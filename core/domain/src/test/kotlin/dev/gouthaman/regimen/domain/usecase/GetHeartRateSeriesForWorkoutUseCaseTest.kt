package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HeartRateSample
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHeartRateSeriesForWorkoutUseCaseTest {

    private suspend fun completedWorkout(
        workoutRepo: FakeWorkoutRepository,
        startTime: Long = 0,
        endTime: Long = 100,
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
    fun `buckets and averages samples into a chart-ready series`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            heartRateSeriesResult = listOf(
                HeartRateSample(time = 0, bpm = 100),
                HeartRateSample(time = 50, bpm = 120),
            ),
        )
        val workoutId = completedWorkout(workoutRepo, startTime = 0, endTime = 100)
        val useCase = GetHeartRateSeriesForWorkoutUseCase(
            healthConnectRepo, workoutRepo, FakeWorkoutBiometricsRepository(),
        )

        val points = useCase(workoutId)

        assertEquals(listOf(100f, 120f), points)
        assertEquals(0L to 100L, healthConnectRepo.lastQueriedRange)
    }

    @Test
    fun `a cached series is returned without querying Health Connect`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val workoutId = completedWorkout(workoutRepo)
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = workoutId,
                fetchedAt = 0,
                heartRateSeries = listOf(90, 95)
            ),
        )
        val useCase =
            GetHeartRateSeriesForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo)

        val points = useCase(workoutId)

        assertEquals(listOf(90f, 95f), points)
        assertNull(healthConnectRepo.lastQueriedRange)
    }

    @Test
    fun `a live result is cached onto an existing biometrics row`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            heartRateSeriesResult = listOf(HeartRateSample(time = 0, bpm = 100)),
        )
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val workoutId = completedWorkout(workoutRepo)
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = workoutId,
                avgBpm = 100,
                fetchedAt = 0
            )
        )
        val useCase =
            GetHeartRateSeriesForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo)

        useCase(workoutId)

        val saved = biometricsRepo.get(workoutId)
        assertEquals(listOf(100), saved?.heartRateSeries)
        assertEquals(100, saved?.avgBpm) // untouched
    }

    @Test
    fun `a live result is not persisted when no biometrics row exists yet`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            heartRateSeriesResult = listOf(HeartRateSample(time = 0, bpm = 100)),
        )
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val workoutId = completedWorkout(workoutRepo)
        val useCase =
            GetHeartRateSeriesForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo)

        val points = useCase(workoutId)

        assertEquals(listOf(100f), points)
        assertNull(biometricsRepo.get(workoutId))
    }

    @Test
    fun `no samples found returns an empty list`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository(heartRateSeriesResult = emptyList())
        val workoutId = completedWorkout(workoutRepo)
        val useCase = GetHeartRateSeriesForWorkoutUseCase(
            healthConnectRepo, workoutRepo, FakeWorkoutBiometricsRepository(),
        )

        assertTrue(useCase(workoutId).isEmpty())
    }

    @Test
    fun `a workout that hasn't finished yet is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            heartRateSeriesResult = listOf(HeartRateSample(time = 0, bpm = 100)),
        )
        val workoutId = workoutRepo.createWorkout(startTime = 0, routineId = null)
        val useCase = GetHeartRateSeriesForWorkoutUseCase(
            healthConnectRepo, workoutRepo, FakeWorkoutBiometricsRepository(),
        )

        assertTrue(useCase(workoutId).isEmpty())
        assertEquals(null, healthConnectRepo.lastQueriedRange)
    }

    @Test
    fun `a missing workout is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val healthConnectRepo = FakeHealthConnectRepository()
        val useCase = GetHeartRateSeriesForWorkoutUseCase(
            healthConnectRepo, workoutRepo, FakeWorkoutBiometricsRepository(),
        )

        assertTrue(useCase("does-not-exist").isEmpty())
    }
}
