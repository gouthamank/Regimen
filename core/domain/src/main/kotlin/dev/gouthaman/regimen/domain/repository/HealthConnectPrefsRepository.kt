package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import kotlinx.coroutines.flow.Flow

/** Local-only, never synced - deliberately separate from [PreferencesRepository]/
 * `UserPreferences`, which is pushed to Firestore wholesale; these settings must never end up in
 * that scope by accident. */
interface HealthConnectPrefsRepository {
    val prefs: Flow<HealthConnectPrefs>

    suspend fun setHealthConnectEnabled(value: Boolean)
    suspend fun setBackgroundSyncEnabled(value: Boolean)
    suspend fun setRetryFrequency(value: HealthConnectRetryFrequency)
}
