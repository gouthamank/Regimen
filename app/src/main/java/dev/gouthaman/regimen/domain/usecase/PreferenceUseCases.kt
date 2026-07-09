package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.prefs.PreferencesRepository
import dev.gouthaman.regimen.data.prefs.UserPreferences
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePreferencesUseCase @Inject constructor(
    private val repo: PreferencesRepository,
) {
    operator fun invoke(): Flow<UserPreferences> = repo.preferences
}

/** Grouped preference mutations used by Settings and Onboarding. */
class UpdatePreferencesUseCase @Inject constructor(
    private val repo: PreferencesRepository,
) {
    suspend fun setUnitSystem(value: UnitSystem) = repo.setUnitSystem(value)
    suspend fun setThemeMode(value: ThemeMode) = repo.setThemeMode(value)
    suspend fun setDynamicColor(value: Boolean) = repo.setDynamicColor(value)
    suspend fun setRestDefaultSec(value: Int) = repo.setRestDefaultSec(value)
    suspend fun setOnboarded(value: Boolean) = repo.setOnboarded(value)
}
