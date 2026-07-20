package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.MaxWorkoutDuration
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePreferencesRepository : PreferencesRepository {

    private val state = MutableStateFlow(UserPreferences())

    override val preferences: Flow<UserPreferences> = state

    override suspend fun setWeightUnit(value: UnitSystem) {
        state.value = state.value.copy(weightUnit = value)
    }

    override suspend fun setDistanceUnit(value: UnitSystem) {
        state.value = state.value.copy(distanceUnit = value)
    }

    override suspend fun setThemeMode(value: ThemeMode) {
        state.value = state.value.copy(themeMode = value)
    }

    override suspend fun setDynamicColor(value: Boolean) {
        state.value = state.value.copy(dynamicColor = value)
    }

    override suspend fun setRestDefaultSec(value: Int) {
        state.value = state.value.copy(restDefaultSec = value)
    }

    override suspend fun setRestChimeEnabled(value: Boolean) {
        state.value = state.value.copy(restChimeEnabled = value)
    }

    override suspend fun setMaxWorkoutDuration(value: MaxWorkoutDuration) {
        state.value = state.value.copy(maxWorkoutDuration = value)
    }

    override suspend fun setOnboarded(value: Boolean) {
        state.value = state.value.copy(onboarded = value)
    }

    fun seed(preferences: UserPreferences) {
        state.value = preferences
    }
}
