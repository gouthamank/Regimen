package dev.gouthaman.regimen.sync.device

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** This device's local copy of `syncConfig.lastPushedAt`, in the same `sync_state` DataStore
 * [DeviceIdentityStore] uses (covered by Auto Backup, same as the device id) - the freshness
 * watermark's read side. Written only after a genuinely successful push (see
 * `SyncPushRunner.runPush()`) or a successful Pull cloud data (which resets it to match
 * whatever it just read from the cloud). Compared against the cloud's live `lastPushedAt` before
 * every push attempt - a mismatch means this device's local state can't be trusted to push from
 * (e.g. an Auto Backup restore landed with a stale snapshot), even though its device ID still
 * says primary. */
@Singleton
class FreshnessWatermarkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val LAST_PUSHED_AT = longPreferencesKey("watermark_last_pushed_at")
    }

    suspend fun get(): Long? = context.syncStateDataStore.data.first()[Keys.LAST_PUSHED_AT]

    suspend fun set(value: Long?) {
        context.syncStateDataStore.edit {
            if (value != null) it[Keys.LAST_PUSHED_AT] = value else it.remove(Keys.LAST_PUSHED_AT)
        }
    }
}
