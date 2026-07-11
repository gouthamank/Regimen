package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setWeightUnit(value: UnitSystem)
    suspend fun setDistanceUnit(value: UnitSystem)
    suspend fun setThemeMode(value: ThemeMode)
    suspend fun setDynamicColor(value: Boolean)
    suspend fun setRestDefaultSec(value: Int)
    suspend fun setRestChimeEnabled(value: Boolean)
    suspend fun setOnboarded(value: Boolean)
}
