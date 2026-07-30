package dev.gouthaman.regimen.sync.push

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.sync.device.syncStateDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the push job's last-run outcome across app restarts, in the same `sync_state`
 * DataStore [dev.gouthaman.regimen.sync.device.DeviceIdentityStore] already uses - without this,
 * [SyncStatus] only ever lived in [SyncPushRunner]'s caller's in-memory `StateFlow`, so any
 * restart (crash, force-kill, or just closing the app) showed "Not yet synced" regardless of
 * whether the last run actually succeeded. */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val IS_FULLY_UP_TO_DATE = booleanPreferencesKey("is_fully_up_to_date")
        val LAST_ERROR = stringPreferencesKey("last_error")
    }

    suspend fun save(status: SyncStatus) {
        val lastSyncedAt = status.lastSyncedAt
        val lastError = status.lastError
        context.syncStateDataStore.edit { prefs ->
            if (lastSyncedAt != null) {
                prefs[Keys.LAST_SYNCED_AT] = lastSyncedAt
            } else {
                prefs.remove(Keys.LAST_SYNCED_AT)
            }
            prefs[Keys.IS_FULLY_UP_TO_DATE] = status.isFullyUpToDate
            if (lastError != null) {
                prefs[Keys.LAST_ERROR] = lastError.name
            } else {
                prefs.remove(Keys.LAST_ERROR)
            }
        }
    }

    suspend fun get(): SyncStatus {
        val prefs = context.syncStateDataStore.data.first()
        return SyncStatus(
            lastSyncedAt = prefs[Keys.LAST_SYNCED_AT],
            isFullyUpToDate = prefs[Keys.IS_FULLY_UP_TO_DATE] ?: false,
            lastError = prefs[Keys.LAST_ERROR]?.let { name ->
                runCatching { AuthErrorReason.valueOf(name) }.getOrNull()
            },
        )
    }
}
