package dev.gouthaman.regimen.feature.healthconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.BiometricsBackfillResult
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.model.HealthConnectStatus
import dev.gouthaman.regimen.domain.usecase.DeleteHealthConnectDataUseCase
import dev.gouthaman.regimen.domain.usecase.GetHealthConnectStatusUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveHealthConnectPrefsUseCase
import dev.gouthaman.regimen.domain.usecase.ReconcileHealthConnectScheduleUseCase
import dev.gouthaman.regimen.domain.usecase.RunBiometricsBackfillUseCase
import dev.gouthaman.regimen.domain.usecase.SetHealthConnectPrefsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HealthConnectSettingsUiState(
    val status: HealthConnectStatus? = null,
    val prefs: HealthConnectPrefs = HealthConnectPrefs(),
    val isPulling: Boolean = false,
)

@HiltViewModel
class HealthConnectSettingsViewModel @Inject constructor(
    observePrefs: ObserveHealthConnectPrefsUseCase,
    private val getStatusUseCase: GetHealthConnectStatusUseCase,
    private val setPrefsUseCase: SetHealthConnectPrefsUseCase,
    private val runBackfillUseCase: RunBiometricsBackfillUseCase,
    private val reconcileSchedule: ReconcileHealthConnectScheduleUseCase,
    private val deleteDataUseCase: DeleteHealthConnectDataUseCase,
) : ViewModel() {

    private val status = MutableStateFlow<HealthConnectStatus?>(null)
    private val isPulling = MutableStateFlow(false)
    private val pullResult = MutableSharedFlow<BiometricsBackfillResult>(extraBufferCapacity = 1)

    /** One-shot per completed pull, regardless of outcome - drives the screen's Snackbar. */
    val pullResultEvents: SharedFlow<BiometricsBackfillResult> = pullResult.asSharedFlow()

    init {
        refreshStatus()
    }

    val uiState: StateFlow<HealthConnectSettingsUiState> = combine(
        observePrefs(), status, isPulling,
    ) { prefs, status, pulling ->
        HealthConnectSettingsUiState(status = status, prefs = prefs, isPulling = pulling)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthConnectSettingsUiState())

    /** Re-checks connection state and reconciles the periodic job's schedule against it - on
     * init, on resume, and after the permission launcher returns. Reconciling here (not just in
     * [setHealthConnectEnabled]) is what cancels the job if permission gets revoked out from
     * under an already-enabled feature. */
    fun refreshStatus() {
        viewModelScope.launch {
            status.value = getStatusUseCase()
            reconcileSchedule()
        }
    }

    fun setHealthConnectEnabled(value: Boolean) {
        viewModelScope.launch {
            setPrefsUseCase.setHealthConnectEnabled(value)
            refreshStatus()
        }
    }

    fun setBackgroundSyncEnabled(value: Boolean) {
        viewModelScope.launch {
            setPrefsUseCase.setBackgroundSyncEnabled(value)
            refreshStatus()
        }
    }

    fun setRetryFrequency(value: HealthConnectRetryFrequency) {
        viewModelScope.launch { setPrefsUseCase.setRetryFrequency(value) }
    }

    /** Only reachable from the opted-out/unavailable states, where no active pull is running. */
    fun deleteAllData() {
        viewModelScope.launch {
            deleteDataUseCase()
            refreshStatus()
        }
    }

    /** Only meaningful while `ACTIVE` - *Needs permission* is handled by the screen's launcher. */
    fun pullNow() {
        if (isPulling.value) return
        viewModelScope.launch {
            isPulling.value = true
            val result = runBackfillUseCase()
            refreshStatus()
            isPulling.value = false
            pullResult.emit(result)
        }
    }
}
