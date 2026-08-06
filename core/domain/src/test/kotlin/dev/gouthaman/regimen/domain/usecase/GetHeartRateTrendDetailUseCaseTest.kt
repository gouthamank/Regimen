package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetHeartRateTrendDetailUseCaseTest {

    private suspend fun completedWorkout(
        workoutRepo: FakeWorkoutRepository,
        startTime: Long,
        endTime: Long,
        routineId: String?,
    ): String {
        val id = workoutRepo.createWorkout(startTime = startTime, routineId = routineId)
        workoutRepo.updateWorkout(
            workoutRepo.getWorkout(id)!!.workout.copy(
                workoutStatus = WorkoutStatus.COMPLETE,
                endTime = endTime,
                accumulatedPausedMs = 100,
            ),
        )
        return id
    }

    @Test
    fun `filters by routine, sorts chronologically, and skips workouts with no avg BPM`() =
        runTest {
            val workoutRepo = FakeWorkoutRepository()
            val biometricsRepo = FakeWorkoutBiometricsRepository()

            val w1 =
                completedWorkout(workoutRepo, startTime = 2_000, endTime = 3_100, routineId = "r1")
            val w2 =
                completedWorkout(workoutRepo, startTime = 1_000, endTime = 2_100, routineId = "r1")
            val w3 =
                completedWorkout(workoutRepo, startTime = 1_500, endTime = 2_500, routineId = "r2")

            biometricsRepo.upsert(
                WorkoutBiometrics(
                    id = "",
                    workoutId = w1,
                    avgBpm = 130,
                    fetchedAt = 0
                )
            )
            biometricsRepo.upsert(
                WorkoutBiometrics(
                    id = "",
                    workoutId = w2,
                    avgBpm = 110,
                    fetchedAt = 0
                )
            )
            // w3 never gets a biometrics row - must be excluded even though it's in range.

            val useCase = GetHeartRateTrendDetailUseCase(workoutRepo, biometricsRepo)
            val entries = useCase(routineId = "r1", range = HistoryRange.ALL).first()

            assertEquals(2, entries.size)
            assertEquals(w2, entries[0].workoutId)
            assertEquals(110, entries[0].avgBpm)
            assertEquals(1_000L, entries[0].durationMillis) // 2_100 - 1_000 - 100 paused
            assertEquals(w1, entries[1].workoutId)
            assertEquals(130, entries[1].avgBpm)
        }

    @Test
    fun `null routineId means every routine, combined`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()

        val w1 = completedWorkout(workoutRepo, startTime = 1_000, endTime = 2_000, routineId = "r1")
        val w2 = completedWorkout(workoutRepo, startTime = 2_000, endTime = 3_000, routineId = null)

        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w1,
                avgBpm = 100,
                fetchedAt = 0
            )
        )
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = w2,
                avgBpm = 90,
                fetchedAt = 0
            )
        )

        val useCase = GetHeartRateTrendDetailUseCase(workoutRepo, biometricsRepo)
        val entries = useCase(routineId = null, range = HistoryRange.ALL).first()

        assertEquals(listOf(w1, w2), entries.map { it.workoutId })
    }

    @Test
    fun `a range cutoff excludes workouts outside the window`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val biometricsRepo = FakeWorkoutBiometricsRepository()

        val now = System.currentTimeMillis()
        val recent =
            completedWorkout(workoutRepo, startTime = now, endTime = now + 1_000, routineId = null)
        val old = completedWorkout(workoutRepo, startTime = 0L, endTime = 1_000L, routineId = null)

        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = recent,
                avgBpm = 100,
                fetchedAt = 0
            )
        )
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = old,
                avgBpm = 90,
                fetchedAt = 0
            )
        )

        val useCase = GetHeartRateTrendDetailUseCase(workoutRepo, biometricsRepo)
        val entries = useCase(routineId = null, range = HistoryRange.FOUR_WEEKS).first()

        assertEquals(listOf(recent), entries.map { it.workoutId })
    }
}
