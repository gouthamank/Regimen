package dev.gouthaman.regimen.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.BiometricTrendRow
import dev.gouthaman.regimen.domain.usecase.GetBiometricTrendRowsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BiometricTrendsUiState(
    val rows: List<BiometricTrendRow> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class BiometricTrendsViewModel @Inject constructor(
    getBiometricTrendRows: GetBiometricTrendRowsUseCase,
) : ViewModel() {

    val uiState: StateFlow<BiometricTrendsUiState> = getBiometricTrendRows()
        .map { rows -> BiometricTrendsUiState(rows = rows, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BiometricTrendsUiState())
}
