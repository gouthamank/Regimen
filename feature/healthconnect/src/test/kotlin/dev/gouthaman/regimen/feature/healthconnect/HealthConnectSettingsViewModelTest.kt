package dev.gouthaman.regimen.feature.healthconnect

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.usecase.DeleteHealthConnectDataUseCase
import dev.gouthaman.regimen.domain.usecase.GetHealthConnectStatusUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveHealthConnectPrefsUseCase
import dev.gouthaman.regimen.domain.usecase.PullBiometricsForWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ReconcileHealthConnectScheduleUseCase
import dev.gouthaman.regimen.domain.usecase.RunBiometricsBackfillUseCase
import dev.gouthaman.regimen.domain.usecase.SetHealthConnectPrefsUseCase
import dev.gouthaman.regimen.testing.FakeClock
import dev.gouthaman.regimen.testing.FakeHealthConnectPrefsRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectRepository
import dev.gouthaman.regimen.testing.FakeHealthConnectScheduleRepository
import dev.gouthaman.regimen.testing.FakeWorkoutBiometricsRepository
import dev.gouthaman.regimen.testing.FakeWorkoutRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HealthConnectSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        healthConnectRepo: FakeHealthConnectRepository = FakeHealthConnectRepository(),
        prefsRepo: FakeHealthConnectPrefsRepository = FakeHealthConnectPrefsRepository(),
        scheduleRepo: FakeHealthConnectScheduleRepository = FakeHealthConnectScheduleRepository(),
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
        biometricsRepo: FakeWorkoutBiometricsRepository = FakeWorkoutBiometricsRepository(),
    ): HealthConnectSettingsViewModel {
        val pullUseCase =
            PullBiometricsForWorkoutUseCase(
                healthConnectRepo,
                workoutRepo,
                biometricsRepo,
                FakeClock()
            )
        val reconcileSchedule =
            ReconcileHealthConnectScheduleUseCase(healthConnectRepo, prefsRepo, scheduleRepo)
        return HealthConnectSettingsViewModel(
            observePrefs = ObserveHealthConnectPrefsUseCase(prefsRepo),
            getStatusUseCase = GetHealthConnectStatusUseCase(healthConnectRepo, biometricsRepo),
            setPrefsUseCase = SetHealthConnectPrefsUseCase(prefsRepo, reconcileSchedule),
            runBackfillUseCase = RunBiometricsBackfillUseCase(
                workoutRepo, biometricsRepo, pullUseCase, FakeClock(),
            ),
            reconcileSchedule = reconcileSchedule,
            deleteDataUseCase = DeleteHealthConnectDataUseCase(biometricsRepo),
        )
    }

    @Test
    fun `loads status and prefs on init`() = runTest {
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.NEEDS_PERMISSION)
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(retryFrequency = HealthConnectRetryFrequency.DAILY),
        )
        val viewModel = viewModel(healthConnectRepo = healthConnectRepo, prefsRepo = prefsRepo)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(
                HealthConnectConnectionState.NEEDS_PERMISSION,
                state.status?.connectionState
            )
            assertEquals(HealthConnectRetryFrequency.DAILY, state.prefs.retryFrequency)
        }
    }

    @Test
    fun `enabling the feature schedules the backfill job and refreshes status`() = runTest {
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(backgroundSyncEnabled = true),
        )
        val viewModel = viewModel(
            healthConnectRepo = healthConnectRepo,
            prefsRepo = prefsRepo,
            scheduleRepo = scheduleRepo,
        )

        viewModel.setHealthConnectEnabled(true)

        viewModel.uiState.test {
            assertTrue(awaitItem().prefs.healthConnectEnabled)
        }
        assertEquals(HealthConnectRetryFrequency.SIX_HOURS, scheduleRepo.scheduledFrequency)
    }

    @Test
    fun `pull now while active runs the backfill and clears the busy flag`() = runTest {
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
        val viewModel = viewModel(healthConnectRepo = healthConnectRepo)

        viewModel.pullNow()

        viewModel.uiState.test {
            assertFalse(awaitItem().isPulling)
        }
    }

    @Test
    fun `pull now emits the backfill result even when there was nothing to check`() = runTest {
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
        val viewModel = viewModel(healthConnectRepo = healthConnectRepo)

        // Subscribe before triggering the action - pullResultEvents is a SharedFlow with no
        // replay, so an event emitted before anyone's listening is simply lost.
        viewModel.pullResultEvents.test {
            viewModel.pullNow()
            val result = awaitItem()
            assertEquals(0, result.candidateCount)
            assertEquals(0, result.pulledCount)
        }
    }

    @Test
    fun `refreshStatus re-checks connection state`() = runTest {
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.UNAVAILABLE)
        val viewModel = viewModel(healthConnectRepo = healthConnectRepo)

        healthConnectRepo.connectionState = HealthConnectConnectionState.ACTIVE
        viewModel.refreshStatus()

        viewModel.uiState.test {
            assertEquals(HealthConnectConnectionState.ACTIVE, awaitItem().status?.connectionState)
        }
    }

    @Test
    fun `refreshStatus cancels a stale schedule after permission is revoked`() = runTest {
        val scheduleRepo = FakeHealthConnectScheduleRepository()
        val healthConnectRepo =
            FakeHealthConnectRepository(connectionState = HealthConnectConnectionState.ACTIVE)
        val prefsRepo = FakeHealthConnectPrefsRepository(
            initial = HealthConnectPrefs(healthConnectEnabled = true, backgroundSyncEnabled = true),
        )
        val viewModel = viewModel(
            healthConnectRepo = healthConnectRepo,
            prefsRepo = prefsRepo,
            scheduleRepo = scheduleRepo,
        )
        viewModel.refreshStatus()
        assertEquals(HealthConnectRetryFrequency.SIX_HOURS, scheduleRepo.scheduledFrequency)

        healthConnectRepo.connectionState = HealthConnectConnectionState.NEEDS_PERMISSION
        viewModel.refreshStatus()

        assertTrue(scheduleRepo.cancelCalled)
        assertEquals(null, scheduleRepo.scheduledFrequency)
    }

    @Test
    fun `deleteAllData wipes stored biometrics and refreshes status`() = runTest {
        val biometricsRepo = FakeWorkoutBiometricsRepository()
        biometricsRepo.upsert(WorkoutBiometrics(id = "", workoutId = "w1", fetchedAt = 1_000))
        val viewModel = viewModel(biometricsRepo = biometricsRepo)

        viewModel.deleteAllData()

        assertNull(biometricsRepo.getMostRecentlyFetched())
        viewModel.uiState.test {
            assertNull(awaitItem().status?.lastPulledAt)
        }
    }
}
