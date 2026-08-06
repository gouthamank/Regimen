package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.testing.FakeHealthConnectPrefsRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileHealthConnectScheduleUseCaseTest {

    @Test
    fun `permission revoked after the feature was left enabled cancels the stale schedule`() =
        runTest {
            val prefsRepo = FakeHealthConnectPrefsRepository(
                initial = HealthConnectPrefs(
                    healthConnectEnabled = true,
                    backgroundSyncEnabled = true
                ),
            )
            val scheduleRepo = FakeHealthConnectScheduleRepository()
            val healthConnectRepo =
                FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
            val useCase =
                ReconcileHealthConnectScheduleUseCase(healthConnectRepo, prefsRepo, scheduleRepo)
            useCase()

            // Permission revoked out from under the still-enabled feature, e.g. via Health Connect's
            // own Settings UI while Regimen was backgrounded.
            healthConnectRepo.connectionState = HealthConnectConnectionState.NEEDS_PERMISSION
            useCase()

            assertTrue(scheduleRepo.cancelCalled)
            assertNull(scheduleRepo.scheduledFrequency)
        }

    @Test
    fun `background permission missing keeps the job cancelled even while core permissions are granted`() =
        runTest {
            val prefsRepo = FakeHealthConnectPrefsRepository(
                initial = HealthConnectPrefs(
                    healthConnectEnabled = true,
                    backgroundSyncEnabled = true
                ),
            )
            val scheduleRepo = FakeHealthConnectScheduleRepository()
            val healthConnectRepo = FakeHealthConnectRepository(
                connectionState = HealthConnectConnectionState.ACTIVE,
                requiredPermissionsResult = setOf(
                    "READ_HEART_RATE",
                    "READ_HEALTH_DATA_IN_BACKGROUND"
                ),
                grantedPermissionsResult = setOf("READ_HEART_RATE"),
            )
            val useCase =
                ReconcileHealthConnectScheduleUseCase(healthConnectRepo, prefsRepo, scheduleRepo)

            useCase()

            assertNull(scheduleRepo.scheduledFrequency)
        }

    @Test
    fun `background sync toggled off keeps the job cancelled even while every permission is granted`() =
        runTest {
            val prefsRepo = FakeHealthConnectPrefsRepository(
                initial = HealthConnectPrefs(
                    healthConnectEnabled = true,
                    backgroundSyncEnabled = false
                ),
            )
            val scheduleRepo = FakeHealthConnectScheduleRepository()
            val healthConnectRepo =
                FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
            val useCase =
                ReconcileHealthConnectScheduleUseCase(healthConnectRepo, prefsRepo, scheduleRepo)

            useCase()

            assertNull(scheduleRepo.scheduledFrequency)
        }
}
