package dev.gouthaman.regimen.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.HeartRateTrendRow
import dev.gouthaman.regimen.domain.usecase.GetHeartRateTrendRowsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HeartRateTrendsUiState(
    val rows: List<HeartRateTrendRow> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class HeartRateTrendsViewModel @Inject constructor(
    getHeartRateTrendRows: GetHeartRateTrendRowsUseCase,
) : ViewModel() {

    val uiState: StateFlow<HeartRateTrendsUiState> = getHeartRateTrendRows()
        .map { rows -> HeartRateTrendsUiState(rows = rows, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeartRateTrendsUiState())
}
