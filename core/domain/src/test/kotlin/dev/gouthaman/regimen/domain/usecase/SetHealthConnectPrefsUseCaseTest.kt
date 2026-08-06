package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.testing.FakeHealthConnectPrefsRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetHealthConnectPrefsUseCaseTest {

    private fun useCase(
        healthConnectRepo: FakeHealthConnectRepository,
        prefsRepo: FakeHealthConnectPrefsRepository,
        scheduleRepo: FakeHealthConnectScheduleRepository,
    ) = SetHealthConnectPrefsUseCase(
        prefsRepo,
        ReconcileHealthConnectScheduleUseCase(healthConnectRepo, prefsRepo, scheduleRepo),
    )

    @Test
    fun `enabling the feature schedules the backfill job when active and fully permitted`() =
        runTest {
            val prefsRepo = FakeHealthConnectPrefsRepository(
                initial = HealthConnectPrefs(
                    backgroundSyncEnabled = true,
                    retryFrequency = HealthConnectRetryFrequency.DAILY,
                ),
            )
            val scheduleRepo = FakeHealthConnectScheduleRepository()
            val healthConnectRepo =
                FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)

            useCase(healthConnectRepo, prefsRepo, scheduleRepo).setHealthConnectEnabled(true)

            assertTrue(prefsRepo.prefs.first().healthConnectEnabled)
            assertEquals(HealthConnectRetryFrequency.DAILY, scheduleRepo.scheduledFrequency)
        }

    @Test
    fun `enabling background sync schedules the job when the feature is already on and active`() =
        runTest {
            val prefsRepo = FakeHealthConnectPrefsRepository(
                initial = HealthConnectPrefs(healthConnectEnabled = true),
            )
            val scheduleRepo = FakeHealthConnectScheduleRepository()
            val healthConnectRepo =
                FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)

            useCase(healthConnectRepo, prefsRepo, scheduleRepo).setBackgroundSyncEnabled(true)

            assertTrue(prefsRepo.prefs.first().backgroundSyncEnabled)
            assertEquals(HealthConnectRetryFrequency.SIX_HOURS, scheduleRepo.scheduledFrequency)
        }

    @Test
    fun `disabling background sync cancels the job even while the feature stays enabled`() =
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

            useCase(healthConnectRepo, prefsRepo, scheduleRepo).setBackgroundSyncEnabled(false)

            assertFalse(prefsRepo.prefs.first().backgroundSyncEnabled)
            assertTrue(scheduleRepo.cancelCalled)
        }

    @Test
    fun `enabling the feature without permission does not schedule anything`() = runTest {
        val prefsRepo = FakeHealthConnectPrefsRepository()
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.NEEDS_PERMISSION)

        useCase(healthConnectRepo, prefsRepo, scheduleRepo).setHealthConnectEnabled(true)

        assertTrue(prefsRepo.prefs.first().healthConnectEnabled)
        assertNull(scheduleRepo.scheduledFrequency)
    }

    @Test
    fun `disabling the feature cancels the backfill job`() = runTest {
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(healthConnectEnabled = true, backgroundSyncEnabled = true),
        )
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)

        useCase(healthConnectRepo, prefsRepo, scheduleRepo).setHealthConnectEnabled(false)

        assertFalse(prefsRepo.prefs.first().healthConnectEnabled)
        assertTrue(scheduleRepo.cancelCalled)
    }

    @Test
    fun `changing frequency while enabled and active reschedules at the new frequency`() = runTest {
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(healthConnectEnabled = true, backgroundSyncEnabled = true),
        )
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)

        useCase(healthConnectRepo, prefsRepo, scheduleRepo)
            .setRetryFrequency(HealthConnectRetryFrequency.ONE_HOUR)

        assertEquals(HealthConnectRetryFrequency.ONE_HOUR, prefsRepo.prefs.first().retryFrequency)
        assertEquals(HealthConnectRetryFrequency.ONE_HOUR, scheduleRepo.scheduledFrequency)
    }

    @Test
    fun `changing frequency while disabled does not schedule anything`() = runTest {
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(healthConnectEnabled = false),
        )
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)

        useCase(healthConnectRepo, prefsRepo, scheduleRepo)
            .setRetryFrequency(HealthConnectRetryFrequency.ONE_HOUR)

        assertNull(scheduleRepo.scheduledFrequency)
    }
}
