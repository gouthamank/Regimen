package dev.gouthaman.regimen.sync.device

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore shape for `users/{uid}/syncConfig/current` - the live, authoritative record of
 * which device is primary for this account. Every device reads this directly rather than
 * comparing against its own local bookkeeping, which is what makes this design immune to
 * stale-local-state problems (e.g. Auto Backup restoring an outdated value). */
data class SyncConfigDto(
    val primaryDeviceId: String? = null,
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
}
