package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.repository.HealthConnectPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Its own DataStore file, separate from :core:data's PreferencesRepositoryImpl - these settings
// are local-only and must never end up in that repository's Firestore push scope.
private val Context.healthConnectDataStore: DataStore<Preferences> by
preferencesDataStore(name = "health_connect_settings")

@Singleton
class HealthConnectPrefsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectPrefsRepository {

    private object Keys {
        val HEALTH_CONNECT_ENABLED = booleanPreferencesKey("auto_pull_enabled")
        val BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        val RETRY_FREQUENCY = stringPreferencesKey("retry_frequency")
    }

    override val prefs: Flow<HealthConnectPrefs> = context.healthConnectDataStore.data.map {
        HealthConnectPrefs(
            healthConnectEnabled = it[Keys.HEALTH_CONNECT_ENABLED] ?: false,
            backgroundSyncEnabled = it[Keys.BACKGROUND_SYNC_ENABLED] ?: false,
            retryFrequency = it[Keys.RETRY_FREQUENCY]
                ?.let { name -> runCatching { HealthConnectRetryFrequency.valueOf(name) }.getOrNull() }
                ?: HealthConnectRetryFrequency.SIX_HOURS,
        )
    }

    override suspend fun setHealthConnectEnabled(value: Boolean) {
        context.healthConnectDataStore.edit { it[Keys.HEALTH_CONNECT_ENABLED] = value }
    }

    override suspend fun setBackgroundSyncEnabled(value: Boolean) {
        context.healthConnectDataStore.edit { it[Keys.BACKGROUND_SYNC_ENABLED] = value }
    }

    override suspend fun setRetryFrequency(value: HealthConnectRetryFrequency) {
        context.healthConnectDataStore.edit { it[Keys.RETRY_FREQUENCY] = value.name }
    }
}
