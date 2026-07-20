package dev.gouthaman.regimen.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.MaxWorkoutDuration
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferencesRepository {
    private object Keys {
        // Legacy single-axis unit pref; read-only migration fallback for WEIGHT_UNIT/DISTANCE_UNIT below.
        val UNIT = stringPreferencesKey("unit_system")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val REST = intPreferencesKey("rest_default_sec")
        val REST_CHIME = booleanPreferencesKey("rest_chime_enabled")
        val MAX_WORKOUT_DURATION = stringPreferencesKey("max_workout_duration")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    override val preferences: Flow<UserPreferences> = context.dataStore.data.map { p ->
        val legacyUnit = p[Keys.UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
        UserPreferences(
            weightUnit = p[Keys.WEIGHT_UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: legacyUnit ?: UnitSystem.METRIC,
            distanceUnit = p[Keys.DISTANCE_UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: legacyUnit ?: UnitSystem.METRIC,
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            restDefaultSec = p[Keys.REST] ?: 90,
            restChimeEnabled = p[Keys.REST_CHIME] ?: false,
            maxWorkoutDuration = p[Keys.MAX_WORKOUT_DURATION]
                ?.let { runCatching { MaxWorkoutDuration.valueOf(it) }.getOrNull() }
                ?: MaxWorkoutDuration.FOUR_HOURS,
            onboarded = p[Keys.ONBOARDED] ?: false,
        )
    }

    override suspend fun setWeightUnit(value: UnitSystem) =
        edit { it[Keys.WEIGHT_UNIT] = value.name }

    override suspend fun setDistanceUnit(value: UnitSystem) =
        edit { it[Keys.DISTANCE_UNIT] = value.name }

    override suspend fun setThemeMode(value: ThemeMode) =
        edit { it[Keys.THEME] = value.name }

    override suspend fun setDynamicColor(value: Boolean) =
        edit { it[Keys.DYNAMIC] = value }

    override suspend fun setRestDefaultSec(value: Int) =
        edit { it[Keys.REST] = value }

    override suspend fun setRestChimeEnabled(value: Boolean) =
        edit { it[Keys.REST_CHIME] = value }

    override suspend fun setMaxWorkoutDuration(value: MaxWorkoutDuration) =
        edit { it[Keys.MAX_WORKOUT_DURATION] = value.name }

    override suspend fun setOnboarded(value: Boolean) =
        edit { it[Keys.ONBOARDED] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
