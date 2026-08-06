package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.repository.HealthConnectPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHealthConnectPrefsRepository(
    initial: HealthConnectPrefs = HealthConnectPrefs(),
) : HealthConnectPrefsRepository {

    private val state = MutableStateFlow(initial)

    override val prefs: Flow<HealthConnectPrefs> = state

    override suspend fun setHealthConnectEnabled(value: Boolean) {
        state.value = state.value.copy(healthConnectEnabled = value)
    }

    override suspend fun setBackgroundSyncEnabled(value: Boolean) {
        state.value = state.value.copy(backgroundSyncEnabled = value)
    }

    override suspend fun setRetryFrequency(value: HealthConnectRetryFrequency) {
        state.value = state.value.copy(retryFrequency = value)
    }
}
