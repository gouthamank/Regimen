package dev.gouthaman.regimen.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * [loaded] distinguishes "preferences not read from disk yet" from a genuine default so the
 * onboarding gate doesn't flash on top of a returning (already-onboarded) user's first frame.
 */
data class MainUiState(
    val prefs: UserPreferences = UserPreferences(),
    val loaded: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
) : ViewModel() {
    // Eagerly so uiState.value advances to loaded=true even before Compose subscribes - the splash
    // keep-condition in MainActivity reads .value directly rather than collecting.
    val uiState: StateFlow<MainUiState> = observePreferences()
        .map { MainUiState(prefs = it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())
}
