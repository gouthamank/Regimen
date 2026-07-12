package dev.gouthaman.regimen.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.UpdatePreferencesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observePreferences: ObservePreferencesUseCase,
    private val updatePreferences: UpdatePreferencesUseCase,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setWeightUnit(value: UnitSystem) = viewModelScope.launch {
        updatePreferences.setWeightUnit(value)
    }

    fun setDistanceUnit(value: UnitSystem) = viewModelScope.launch {
        updatePreferences.setDistanceUnit(value)
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch {
        updatePreferences.setThemeMode(value)
    }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch {
        updatePreferences.setDynamicColor(value)
    }

    fun setRestDefaultSec(value: Int) = viewModelScope.launch {
        updatePreferences.setRestDefaultSec(value)
    }

    fun setRestChimeEnabled(value: Boolean) = viewModelScope.launch {
        updatePreferences.setRestChimeEnabled(value)
    }
}
