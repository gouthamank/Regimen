package dev.gouthaman.regimen.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.prefs.UserPreferences
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.UpdatePreferencesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * First-run onboarding (S17). Selections are written to preferences immediately (so the app theme
 * updates live and a skip still keeps whatever was chosen); [finish] flips the onboarded flag,
 * which the app-level gate observes to leave onboarding.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setUnitSystem(value: UnitSystem) = viewModelScope.launch {
        updatePreferences.setUnitSystem(value)
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        updatePreferences.setThemeMode(value)
    }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch {
        updatePreferences.setDynamicColor(value)
    }

    fun finish() = viewModelScope.launch {
        updatePreferences.setOnboarded(true)
    }
}
