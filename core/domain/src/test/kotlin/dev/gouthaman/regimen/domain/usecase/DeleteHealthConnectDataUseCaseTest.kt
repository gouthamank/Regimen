package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Test

class DeleteHealthConnectDataUseCaseTest {

    @Test
    fun `wipes every stored biometrics row`() = runTest {
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        biometricsRepo.upsert(WorkoutBiometrics(id = "", workoutId = "w1", fetchedAt = 1_000))
        biometricsRepo.upsert(WorkoutBiometrics(id = "", workoutId = "w2", fetchedAt = 2_000))
        val useCase = DeleteHealthConnectDataUseCase(biometricsRepo)

        useCase()

        assertNull(biometricsRepo.get("w1"))
        assertNull(biometricsRepo.get("w2"))
        assertNull(biometricsRepo.getMostRecentlyFetched())
    }
}
