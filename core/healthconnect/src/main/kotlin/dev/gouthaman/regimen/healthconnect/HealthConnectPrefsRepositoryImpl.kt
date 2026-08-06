package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.HealthConnectBackfillWindow
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
        val AUTO_PULL_ENABLED = booleanPreferencesKey("auto_pull_enabled")
        val RETRY_FREQUENCY = stringPreferencesKey("retry_frequency")
        val BACKFILL_WINDOW = stringPreferencesKey("backfill_window")
    }

    override val prefs: Flow<HealthConnectPrefs> = context.healthConnectDataStore.data.map {
        HealthConnectPrefs(
            autoPullEnabled = it[Keys.AUTO_PULL_ENABLED] ?: false,
            retryFrequency = it[Keys.RETRY_FREQUENCY]
                ?.let { name -> runCatching { HealthConnectRetryFrequency.valueOf(name) }.getOrNull() }
                ?: HealthConnectRetryFrequency.SIX_HOURS,
            backfillWindow = it[Keys.BACKFILL_WINDOW]
                ?.let { name -> runCatching { HealthConnectBackfillWindow.valueOf(name) }.getOrNull() }
                ?: HealthConnectBackfillWindow.SEVEN,
        )
    }

    override suspend fun setAutoPullEnabled(value: Boolean) {
        context.healthConnectDataStore.edit { it[Keys.AUTO_PULL_ENABLED] = value }
    }

    override suspend fun setRetryFrequency(value: HealthConnectRetryFrequency) {
        context.healthConnectDataStore.edit { it[Keys.RETRY_FREQUENCY] = value.name }
    }

    override suspend fun setBackfillWindow(value: HealthConnectBackfillWindow) {
        context.healthConnectDataStore.edit { it[Keys.BACKFILL_WINDOW] = value.name }
    }
}
