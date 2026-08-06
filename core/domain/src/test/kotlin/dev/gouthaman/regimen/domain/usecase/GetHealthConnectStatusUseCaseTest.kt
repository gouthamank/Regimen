package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHealthConnectStatusUseCaseTest {

    @Test
    fun `needs permission - no optional flag, no pull history`() = runTest {
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.NEEDS_PERMISSION)
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val useCase = GetHealthConnectStatusUseCase(healthConnectRepo, biometricsRepo)

        val status = useCase()

        assertEquals(HealthConnectConnectionState.NEEDS_PERMISSION, status.connectionState)
        assertFalse(status.hasOptionalPermissionAvailable)
        assertNull(status.detectedSourceAppLabel)
        assertNull(status.lastPulledAt)
    }

    @Test
    fun `active with every required permission granted - no optional flag`() = runTest {
        val healthConnectRepo = FakeHealthConnectRepository(
            connectionState = HealthConnectConnectionState.ACTIVE,
            requiredPermissionsResult = setOf("READ_HEART_RATE", "READ_HEALTH_DATA_IN_BACKGROUND"),
            grantedPermissionsResult = setOf("READ_HEART_RATE", "READ_HEALTH_DATA_IN_BACKGROUND"),
        )
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val useCase = GetHealthConnectStatusUseCase(healthConnectRepo, biometricsRepo)

        val status = useCase()

        assertFalse(status.hasOptionalPermissionAvailable)
        assertEquals(
            setOf("READ_HEART_RATE", "READ_HEALTH_DATA_IN_BACKGROUND"),
            status.requiredPermissions,
        )
    }

    @Test
    fun `active but an optional permission is available and ungranted`() = runTest {
        val healthConnectRepo = FakeHealthConnectRepository(
            connectionState = HealthConnectConnectionState.ACTIVE,
            requiredPermissionsResult = setOf("READ_HEART_RATE", "READ_HEALTH_DATA_IN_BACKGROUND"),
            grantedPermissionsResult = setOf("READ_HEART_RATE"),
        )
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        val useCase = GetHealthConnectStatusUseCase(healthConnectRepo, biometricsRepo)

        val status = useCase()

        assertTrue(status.hasOptionalPermissionAvailable)
    }

    @Test
    fun `resolves the most recently fetched workout's source app label and timestamp`() = runTest {
        val healthConnectRepo = FakeHealthConnectRepository(
            appLabels = mapOf("com.google.android.apps.healthdata" to "Google Health"),
        )
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        biometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = "w1",
                sourcePackageName = "com.google.android.apps.healthdata",
                fetchedAt = 1_000,
            ),
        )
        val useCase = GetHealthConnectStatusUseCase(healthConnectRepo, biometricsRepo)

        val status = useCase()

        assertEquals("Google Health", status.detectedSourceAppLabel)
        assertEquals(1_000L, status.lastPulledAt)
    }
}
