package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectBackfillWindow
import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

class RunBiometricsBackfillUseCaseTest {

    private suspend fun completedWorkout(
        workoutRepo: FakeWorkoutRepository,
        startTime: Long,
        endTime: Long,
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
    fun `pulls only completed workouts missing biometrics within the backfill window`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository(
            sampleForRange = HealthConnectBiometricsSample(
                avgBpm = 120,
                maxBpm = 150,
                activeCaloriesKcal = 200.0,
                sourcePackageName = "com.google.android.apps.healthdata",
            ),
        )
        val clock = FakeClock(10 * DAY_MILLIS)
        val pullUseCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)
        val useCase = RunBiometricsBackfillUseCase(workoutRepo, biometricsRepo, pullUseCase, clock)

        val missingId =
            completedWorkout(
                workoutRepo,
                startTime = 9 * DAY_MILLIS,
                endTime = 9 * DAY_MILLIS + 1_000
            )
        val alreadyPulledId =
            completedWorkout(
                workoutRepo,
                startTime = 9 * DAY_MILLIS,
                endTime = 9 * DAY_MILLIS + 1_000
            )
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = alreadyPulledId,
                fetchedAt = 1_000
            )
        )
        val tooOldId =
            completedWorkout(
                workoutRepo,
                startTime = 1 * DAY_MILLIS,
                endTime = 1 * DAY_MILLIS + 1_000
            )
        val inProgressId = workoutRepo.createWorkout(startTime = 9 * DAY_MILLIS, routineId = null)

        useCase(HealthConnectBackfillWindow.SEVEN)

        assertNotNull(biometricsRepo.get(missingId))
        assertEquals(1, healthConnectRepo.queriedRanges.size)
        assertNull(biometricsRepo.get(tooOldId))
        assertNull(biometricsRepo.get(inProgressId))
    }

    @Test
    fun `nothing to pull is a no-op`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val healthConnectRepo = FakeHealthConnectRepository()
        val clock = FakeClock(10 * DAY_MILLIS)
        val pullUseCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)
        val useCase = RunBiometricsBackfillUseCase(workoutRepo, biometricsRepo, pullUseCase, clock)

        useCase(HealthConnectBackfillWindow.SEVEN)

        assertEquals(0, healthConnectRepo.queriedRanges.size)
    }
}
