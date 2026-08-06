package dev.gouthaman.regimen.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.HeartRateTrendEntry
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.usecase.GetHeartRateTrendDetailUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.navigation.HeartRateTrendDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HeartRateTrendDetailUiState(
    val routineId: String? = null,
    val routineName: String? = null,
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    /** Chronological, for the chart. */
    val trend: List<Float> = emptyList(),
    /** Newest-first, for the list. */
    val entries: List<HeartRateTrendEntry> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HeartRateTrendDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRoutines: ObserveRoutinesUseCase,
    getHeartRateTrendDetail: GetHeartRateTrendDetailUseCase,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<HeartRateTrendDetailRoute>().routineId
    private val _range = MutableStateFlow(HistoryRange.THREE_MONTHS)

    val uiState: StateFlow<HeartRateTrendDetailUiState> = combine(
        observeRoutines(),
        _range.flatMapLatest { getHeartRateTrendDetail(routineId, it) },
        _range,
    ) { routines, entries, range ->
        HeartRateTrendDetailUiState(
            routineId = routineId,
            routineName = routineId?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine?.name },
            range = range,
            trend = entries.map { it.avgBpm.toFloat() },
            entries = entries.sortedByDescending { it.startTime },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeartRateTrendDetailUiState())

    fun setRange(value: HistoryRange) {
        _range.value = value
    }
}
