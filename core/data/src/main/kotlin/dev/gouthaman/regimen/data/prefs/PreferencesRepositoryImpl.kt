package dev.gouthaman.regimen.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.MaxWorkoutDuration
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Sync push job's read side for the single `preferences` document - `lastModifiedAt` is passed
 * alongside [preferences] since it's DataStore-only bookkeeping, never part of [UserPreferences]
 * itself (same reasoning as Room's `isDirty`/`lastModifiedAt` never reaching a domain model). */
data class DirtyPreferences(
    val preferences: UserPreferences,
    val lastModifiedAt: Long,
)

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

        // Local-only sync bookkeeping, same shape as the isDirty/lastModifiedAt columns on every
        // Room entity - not part of UserPreferences, never read by anything other than a future
        // sync push.
        val IS_DIRTY = booleanPreferencesKey("is_dirty")
        val LAST_MODIFIED_AT = longPreferencesKey("last_modified_at")
    }

    override val preferences: Flow<UserPreferences> =
        context.dataStore.data.map { it.toUserPreferences() }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val legacyUnit = this[Keys.UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
        return UserPreferences(
            weightUnit = this[Keys.WEIGHT_UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: legacyUnit ?: UnitSystem.METRIC,
            distanceUnit = this[Keys.DISTANCE_UNIT]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
                ?: legacyUnit ?: UnitSystem.METRIC,
            themeMode = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = this[Keys.DYNAMIC] ?: true,
            restDefaultSec = this[Keys.REST] ?: 90,
            restChimeEnabled = this[Keys.REST_CHIME] ?: false,
            maxWorkoutDuration = this[Keys.MAX_WORKOUT_DURATION]
                ?.let { runCatching { MaxWorkoutDuration.valueOf(it) }.getOrNull() }
                ?: MaxWorkoutDuration.FOUR_HOURS,
            onboarded = this[Keys.ONBOARDED] ?: false,
        )
    }

    /** `null` if preferences aren't dirty - nothing for the sync push job to do. Checks `== false`
     * rather than `!= true` deliberately, so a device with no `IS_DIRTY` key at all (never called
     * any setter) is treated as dirty too, getting an initial push instead of staying un-synced. */
    suspend fun getDirtyPreferences(): DirtyPreferences? {
        val p = context.dataStore.data.first()
        if (p[Keys.IS_DIRTY] == false) return null
        return DirtyPreferences(
            preferences = p.toUserPreferences(),
            lastModifiedAt = p[Keys.LAST_MODIFIED_AT] ?: 0L,
        )
    }

    suspend fun clearPreferencesDirty() {
        context.dataStore.edit { it[Keys.IS_DIRTY] = false }
    }

    /** "Pull cloud data"'s write side - plain strings/primitives rather than a Dto type, since
     * that type lives in `:core:sync` (wrong dependency direction). Doesn't touch `onboarded` - a
     * per-device concept, never part of what's pulled. Marks clean, not dirty. */
    suspend fun applyPulledPreferences(
        weightUnit: String,
        distanceUnit: String,
        themeMode: String,
        dynamicColor: Boolean,
        restDefaultSec: Int,
        restChimeEnabled: Boolean,
        maxWorkoutDuration: String,
        lastModifiedAt: Long,
    ) {
        context.dataStore.edit {
            it[Keys.WEIGHT_UNIT] = weightUnit
            it[Keys.DISTANCE_UNIT] = distanceUnit
            it[Keys.THEME] = themeMode
            it[Keys.DYNAMIC] = dynamicColor
            it[Keys.REST] = restDefaultSec
            it[Keys.REST_CHIME] = restChimeEnabled
            it[Keys.MAX_WORKOUT_DURATION] = maxWorkoutDuration
            it[Keys.IS_DIRTY] = false
            it[Keys.LAST_MODIFIED_AT] = lastModifiedAt
        }
    }

    /** "Claim primary"'s force-full-upload side - marks preferences dirty regardless of whether
     * they already were, same reasoning as every entity DAO's `markAllDirty`-shaped methods.
     * Leaves `LAST_MODIFIED_AT` untouched - only whether a push includes this document depends on
     * `IS_DIRTY`, and forcing a fresh timestamp isn't needed to force the re-upload. */
    suspend fun markPreferencesDirty() {
        context.dataStore.edit { it[Keys.IS_DIRTY] = true }
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
        context.dataStore.edit {
            block(it)
            it[Keys.IS_DIRTY] = true
            it[Keys.LAST_MODIFIED_AT] = System.currentTimeMillis()
        }
    }
}
