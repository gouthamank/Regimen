package dev.gouthaman.regimen.sync.device

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** A lock older than this is treated as abandoned (a crashed/killed push or claim that never
 * cleared it), not genuinely in flight - 2x [dev.gouthaman.regimen.sync.push.SyncPushRunner]'s
 * own 5-minute push timeout, as a safety margin for clock skew rather than a tight threshold.
 * Shared by every [SyncConfigDto.lockedAt] consumer (`SyncPushRunner`,
 * `dev.gouthaman.regimen.sync.replace.SyncReplaceRepositoryImpl`) so they agree on what "stale"
 * means. */
internal const val LOCK_STALE_AFTER_MS = 10 * 60_000L

/** Firestore shape for `users/{uid}/syncConfig/current` - the live, authoritative record of
 * which device is primary for this account. Every device reads this directly rather than
 * comparing against its own local bookkeeping, which is what makes this design immune to
 * stale-local-state problems (e.g. Auto Backup restoring an outdated value). [lastPushedAt] is
 * the freshness watermark - updated by the push job after every run that completes without error
 * (full or capped-partial alike), read-and-compared-on-launch is still unbuilt. [lockedAt] is a
 * soft lease shared by both the push job and "Claim primary" - set for the duration of either
 * one's work and cleared (`null`) when it ends (success, failure, or aborted), a timestamp rather
 * than a plain boolean so a crashed/killed run that never clears it doesn't lock the account out
 * forever (see [LOCK_STALE_AFTER_MS]). Both `SyncPushRunner` (refuses to start while it's held)
 * and `SyncReplaceRepositoryImpl.claimPrimary()` (checks it before claiming, then holds it for
 * its own entire wipe-and-force-push duration) read and write it. */
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

    override suspend fun hasCompetingPrimary(): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")
        val primaryDeviceId = syncConfigRef.get().await()
            .toObject(SyncConfigDto::class.java)?.primaryDeviceId
        return primaryDeviceId != null && primaryDeviceId != deviceId
    }
}
