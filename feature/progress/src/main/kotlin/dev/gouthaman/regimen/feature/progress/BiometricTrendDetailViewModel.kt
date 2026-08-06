package dev.gouthaman.regimen.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.BiometricTrendEntry
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.usecase.GetBiometricTrendDetailUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.navigation.BiometricTrendDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class BiometricTrendMetric { HEART_RATE, CALORIES }

data class BiometricTrendDetailUiState(
    val routineId: String? = null,
    val routineName: String? = null,
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    val metric: BiometricTrendMetric = BiometricTrendMetric.HEART_RATE,
    /** Chronological, for the chart - already filtered to [metric]. */
    val trend: List<Float> = emptyList(),
    /** Newest-first, for the list - already filtered to [metric]. */
    val entries: List<BiometricTrendEntry> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BiometricTrendDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRoutines: ObserveRoutinesUseCase,
    getBiometricTrendDetail: GetBiometricTrendDetailUseCase,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<BiometricTrendDetailRoute>().routineId
    private val _range = MutableStateFlow(HistoryRange.THREE_MONTHS)
    private val _metric = MutableStateFlow(BiometricTrendMetric.HEART_RATE)

    val uiState: StateFlow<BiometricTrendDetailUiState> = combine(
        observeRoutines(),
        _range.flatMapLatest { getBiometricTrendDetail(routineId, it) },
        _range,
        _metric,
    ) { routines, allEntries, range, metric ->
        val entries = when (metric) {
            BiometricTrendMetric.HEART_RATE -> allEntries.filter { it.avgBpm != null }
            BiometricTrendMetric.CALORIES -> allEntries.filter { it.activeCaloriesKcal != null }
        }
        val trend = when (metric) {
            BiometricTrendMetric.HEART_RATE -> entries.map { it.avgBpm!!.toFloat() }
            BiometricTrendMetric.CALORIES -> entries.map { it.activeCaloriesKcal!!.toFloat() }
        }
        BiometricTrendDetailUiState(
            routineId = routineId,
            routineName = routineId?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine?.name },
            range = range,
            metric = metric,
            trend = trend,
            entries = entries.sortedByDescending { it.startTime },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BiometricTrendDetailUiState())

    fun setRange(value: HistoryRange) {
        _range.value = value
    }

    fun setMetric(value: BiometricTrendMetric) {
        _metric.value = value
    }
}
