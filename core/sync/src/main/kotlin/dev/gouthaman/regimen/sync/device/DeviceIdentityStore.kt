package dev.gouthaman.regimen.sync.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStateDataStore: DataStore<Preferences> by
preferencesDataStore(name = "sync_state")

/** This device's own random per-install identifier - low stakes if it doesn't survive a
 * backup/restore, since the only consequence is a redundant "claim primary" prompt later, not a
 * correctness issue. */
@Singleton
class DeviceIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.syncStateDataStore.data.first()[Keys.DEVICE_ID]
        if (existing != null) return existing

        val generated = UUID.randomUUID().toString()
        context.syncStateDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }
}
