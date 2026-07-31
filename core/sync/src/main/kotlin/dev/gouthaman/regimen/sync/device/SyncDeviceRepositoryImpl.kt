package dev.gouthaman.regimen.sync.device

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A lock older than this is treated as abandoned, not in flight - 2x SyncPushRunner's 5-minute
 * push timeout, as a clock-skew safety margin. Shared by every [SyncConfigDto.lockedAt] consumer
 * so they agree on what "stale" means. */
internal const val LOCK_STALE_AFTER_MS = 10 * 60_000L

/** Firestore shape for `users/{uid}/syncConfig/current` - the live, authoritative record of which
 * device is primary, read directly by every device rather than compared against local bookkeeping.
 * [lastPushedAt] is the freshness watermark, checked against [FreshnessWatermarkStore] before every
 * push. [lockedAt] is a soft lease shared by the push job and "Claim primary", a timestamp (not a
 * boolean) so a crashed run doesn't lock the account out forever (see [LOCK_STALE_AFTER_MS]). */
data class SyncConfigDto(
    val primaryDeviceId: String? = null,
    val lastPushedAt: Long? = null,
    val lockedAt: Long? = null,
)

@Singleton
class SyncDeviceRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val watermarkStore: FreshnessWatermarkStore,
) : SyncDeviceRepository {

    override suspend fun ensurePrimaryClaimed(): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")

        // A transaction, not a plain read-then-write, so two devices signing in for the very
        // first time at the same moment can't both see "unset" and both think they claimed it.
        return firestore.runTransaction { transaction ->
            val existing = transaction.get(syncConfigRef).toObject(SyncConfigDto::class.java)
                ?.primaryDeviceId
            when (existing) {
                null -> {
                    transaction.set(syncConfigRef, SyncConfigDto(primaryDeviceId = deviceId))
                    true
                }

                deviceId -> true
                else -> false
            }
        }.await()
    }

    override suspend fun isPrimary(): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")
        val snapshot = syncConfigRef.get().await()
        return snapshot.toObject(SyncConfigDto::class.java)?.primaryDeviceId == deviceId
    }

    override suspend fun secondaryDeviceReason(): SecondaryDeviceReason? {
        val uid = firebaseAuth.currentUser?.uid ?: return null
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")
        val config = syncConfigRef.get().await().toObject(SyncConfigDto::class.java)
        return when {
            config?.primaryDeviceId == null -> null
            config.primaryDeviceId != deviceId -> SecondaryDeviceReason.COMPETING_PRIMARY
            watermarkStore.get() != config.lastPushedAt -> SecondaryDeviceReason.STALE_LOCAL_STATE
            else -> null
        }
    }
}
