package dev.gouthaman.regimen.sync.push

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.data.prefs.PreferencesRepositoryImpl
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import dev.gouthaman.regimen.domain.repository.SyncPushRepository
import dev.gouthaman.regimen.sync.auth.isSessionRevoked
import dev.gouthaman.regimen.sync.device.LOCK_STALE_AFTER_MS
import dev.gouthaman.regimen.sync.device.SyncConfigDto
import dev.gouthaman.regimen.sync.firestore.FirestoreSyncPaths
import dev.gouthaman.regimen.sync.firestore.toDto
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val PUSH_BATCH_CAP = 1000
private const val DELETE_BATCH_CAP = 1000

/** Generous on purpose - a real first-time backfill can be hundreds of sequential Firestore round
 * trips (per-row confirmation, see [pushDirtyBatch]'s doc), which can legitimately take minutes
 * under normal network conditions. This is a backstop against a hang, not a tight SLA. */
private const val PUSH_TIMEOUT_MS = 5 * 60_000L

internal data class DirtyBatchResult(val remainingBudget: Int, val hasMore: Boolean)

/** Shared read-write-clear loop for every list-shaped synced entity type, extracted as a plain
 * function (no Firestore/Room dependency of its own) so its partial-failure/batch-cap behavior is
 * unit-testable with fake lambdas. Clears each row's dirty flag immediately after its own write
 * succeeds - not batched at the end - so an exception partway through (which propagates out and
 * aborts the whole run) leaves only the unconfirmed rows dirty, exactly the partial-batch
 * behavior the change-tracking design calls for. [DirtyBatchResult.hasMore] is set whenever the
 * read returns a full page at the requested limit - a (harmless, self-correcting) signal there
 * may be more work than this call's budget allowed for, not a precise count. */
internal suspend fun <T> pushDirtyBatch(
    budget: Int,
    getDirty: suspend (Int) -> List<T>,
    write: suspend (T) -> Unit,
    idOf: (T) -> String,
    clearDirty: suspend (List<String>) -> Unit,
): DirtyBatchResult {
    if (budget <= 0) return DirtyBatchResult(remainingBudget = 0, hasMore = false)
    val dirty = getDirty(budget)
    for (item in dirty) {
        write(item)
        clearDirty(listOf(idOf(item)))
    }
    return DirtyBatchResult(remainingBudget = budget - dirty.size, hasMore = dirty.size == budget)
}

/**
 * The primary device's incremental sync push - reads every dirty row/tombstone across the synced
 * Room tables and the preferences document, writes/deletes them via Firestore, and clears each
 * one only once its own write is confirmed (never batched-and-cleared-at-the-end, so a
 * mid-run failure leaves exactly the unconfirmed rows dirty for next run, not the whole batch).
 * Re-checks primary status between every entity-type step (not just once at the start) and holds
 * a soft `syncConfig.lockedAt` lease for the run's duration, guarding against a *different* device
 * claiming primary mid-run. Called by [SyncPushWorker] (periodic) and `AccountViewModel`'s manual
 * "Sync now" alike - the same code path either way. Firestore round-trips are verified manually on
 * the AVD, not by automated tests, per this module's existing convention (see `docs/testing.md`).
 */
@Singleton
class SyncPushRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val syncDeviceRepository: SyncDeviceRepository,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val measurementDao: MeasurementDao,
    private val tombstoneDao: SyncTombstoneDao,
    private val preferencesRepository: PreferencesRepositoryImpl,
    private val syncStatusStore: SyncStatusStore,
) : SyncPushRepository {
    /** Set once per [push] call whenever a dirty/tombstone read returns a full page at its
     * requested limit - a (harmless, self-correcting) signal that there may be more work than
     * this run's cap allowed for, not a precise count. */
    private var hasMoreWork = false

    override suspend fun getLastStatus(): SyncStatus = syncStatusStore.get()

    /** Persists whatever [SyncStatus] [runPush] computes, on every branch it returns except the
     * not-primary no-op - so this matches what [getLastStatus] reads back later, regardless of
     * caller (periodic [SyncPushWorker] or manual "Sync now"). Not-primary is deliberately
     * excluded: it means this device isn't the one syncing right now, not that its last real
     * sync stopped being true - persisting it would overwrite a genuinely persisted "Synced at
     * 2:14 PM" with "Not yet synced" the moment this device loses primary status, even though it
     * really did sync before. */
    override suspend fun push(): SyncStatus {
        val status = runPush()
        if (status != notPrimaryStatus()) syncStatusStore.save(status)
        return status
    }

    private suspend fun runPush(): SyncStatus {
        // Fails fast without ever touching Firestore - offline, a bare `.await()` on a Firestore
        // call doesn't reliably fail quickly (it can hang, waiting on a network state that never
        // resolves), which is exactly what left "Sync now" spinning forever in airplane mode
        // before this check existed. The periodic job never hits this, since WorkManager's own
        // `NetworkType.CONNECTED` constraint already stops it from starting offline at all - only
        // the manual "Sync now" path (which calls straight into this, bypassing WorkManager) needs
        // its own check.
        if (!isNetworkAvailable()) {
            return SyncStatus(
                lastSyncedAt = null,
                isFullyUpToDate = false,
                lastError = AuthErrorReason.NETWORK
            )
        }

        return try {
            withTimeout(PUSH_TIMEOUT_MS) { doPush() }
        } catch (e: TimeoutCancellationException) {
            SyncStatus(
                lastSyncedAt = null,
                isFullyUpToDate = false,
                lastError = AuthErrorReason.NETWORK
            )
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as
                ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun doPush(): SyncStatus {
        val uid = firebaseAuth.currentUser?.uid
            ?: return notPrimaryStatus()
        // Not an error - a device that isn't primary anymore has nothing to report beyond "not my
        // turn." The caller (the WorkManager job) is what should notice this and cancel its own
        // periodic schedule, per the Primary-status check item in the doc.
        if (!syncDeviceRepository.isPrimary()) return notPrimaryStatus()

        val paths = FirestoreSyncPaths(firestore, uid)
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")

        // Refuses to start while "Claim primary" is actively wiping/rebuilding this account
        // (holding the same lock for its own, longer-than-a-push duration) - otherwise this run
        // could write fresh data into a destination that's concurrently being deleted out from
        // under it. A plain read-then-write, not a transaction (same rigor as the mid-run
        // ensureStillPrimary() checks below - reactive/best-effort, not perfectly atomic; a claim
        // starting in the narrow gap between this check and this run's own lock write below is an
        // accepted, low-probability residual risk for what's still fundamentally a personal app).
        val existingLock = syncConfigRef.get().await().toObject(SyncConfigDto::class.java)?.lockedAt
        if (existingLock != null && System.currentTimeMillis() - existingLock < LOCK_STALE_AFTER_MS) {
            return notPrimaryStatus()
        }

        hasMoreWork = false
        var budget = PUSH_BATCH_CAP

        // A soft lease for the duration of this run, shared with "Claim primary" (which also
        // checks and holds it, for its own longer wipe-and-force-push duration) - see
        // SyncConfigDto.lockedAt's own doc for the full picture.
        syncConfigRef.set(mapOf("lockedAt" to System.currentTimeMillis()), SetOptions.merge())
            .await()
        try {
            budget = pushExercises(paths, budget); ensureStillPrimary()
            budget = pushMeasurementTypes(paths, budget); ensureStillPrimary()
            budget = pushBodyMetrics(paths, budget); ensureStillPrimary()
            budget = pushRoutines(paths, budget); ensureStillPrimary()
            budget = pushRoutineExercises(paths, budget); ensureStillPrimary()
            budget = pushWorkouts(paths, budget); ensureStillPrimary()
            budget = pushWorkoutExercises(paths, budget); ensureStillPrimary()
            budget = pushSetEntries(paths, budget); ensureStillPrimary()
            budget = pushCardioEntries(paths, budget); ensureStillPrimary()
            pushPreferences(paths); ensureStillPrimary()
            // Firestore deletes have their own daily quota, separate from writes - its own cap
            // rather than sharing the write-side budget.
            processTombstones(paths, DELETE_BATCH_CAP)
        } catch (e: NotPrimaryAnymoreException) {
            return notPrimaryStatus()
        } catch (e: Exception) {
            return SyncStatus(
                lastSyncedAt = null,
                isFullyUpToDate = false,
                lastError = e.toReason()
            )
        } finally {
            // NonCancellable: this must still complete even when `push()`'s withTimeout is what
            // triggered this finally block - otherwise a timeout would abandon the lease
            // mid-cleanup instead of actually releasing it.
            withContext(NonCancellable) {
                syncConfigRef.set(mapOf("lockedAt" to null), SetOptions.merge()).await()
            }
        }

        val now = System.currentTimeMillis()
        syncConfigRef.set(mapOf("lastPushedAt" to now), SetOptions.merge()).await()
        return SyncStatus(lastSyncedAt = now, isFullyUpToDate = !hasMoreWork, lastError = null)
    }

    private fun notPrimaryStatus() =
        SyncStatus(lastSyncedAt = null, isFullyUpToDate = false, lastError = null)

    /** Re-checked between every entity-type step, not just once at the start - guards against a
     * *different* device claiming primary (wiping the Firestore destination and writing its own
     * data over it) mid-run, which could otherwise interleave into a corrupted mix of both
     * devices' data. Throws rather than returning a flag so every call site downstream aborts
     * automatically without needing its own check. */
    private suspend fun ensureStillPrimary() {
        if (!syncDeviceRepository.isPrimary()) throw NotPrimaryAnymoreException()
    }

    private class NotPrimaryAnymoreException : Exception()

    private suspend fun <T> pushDirty(
        budget: Int,
        getDirty: suspend (Int) -> List<T>,
        write: suspend (T) -> Unit,
        idOf: (T) -> String,
        clearDirty: suspend (List<String>) -> Unit,
    ): Int {
        val result = pushDirtyBatch(budget, getDirty, write, idOf, clearDirty)
        if (result.hasMore) hasMoreWork = true
        return result.remainingBudget
    }

    private suspend fun pushExercises(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = exerciseDao::getDirty,
        write = { e: ExerciseEntity -> paths.exercise(e.id).set(e.toDto()).await() },
        idOf = ExerciseEntity::id,
        clearDirty = exerciseDao::clearDirty,
    )

    private suspend fun pushMeasurementTypes(paths: FirestoreSyncPaths, budget: Int): Int =
        pushDirty(
            budget = budget,
            getDirty = measurementDao::getDirtyTypes,
            write = { t: MeasurementTypeEntity ->
                paths.measurementType(t.id).set(t.toDto()).await()
            },
            idOf = MeasurementTypeEntity::id,
            clearDirty = measurementDao::clearDirtyTypes,
        )

    private suspend fun pushBodyMetrics(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = measurementDao::getDirtyMetrics,
        write = { m: BodyMetricEntity -> paths.bodyMetric(m.id).set(m.toDto()).await() },
        idOf = BodyMetricEntity::id,
        clearDirty = measurementDao::clearDirtyMetrics,
    )

    private suspend fun pushRoutines(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = routineDao::getDirtyRoutines,
        write = { r: RoutineEntity -> paths.routine(r.id).set(r.toDto()).await() },
        idOf = RoutineEntity::id,
        clearDirty = routineDao::clearDirtyRoutines,
    )

    private suspend fun pushRoutineExercises(paths: FirestoreSyncPaths, budget: Int): Int =
        pushDirty(
            budget = budget,
            getDirty = routineDao::getDirtyRoutineExercises,
            write = { re: RoutineExerciseEntity ->
                paths.routineExercise(re.routineId, re.id).set(re.toDto()).await()
            },
            idOf = RoutineExerciseEntity::id,
            clearDirty = routineDao::clearDirtyRoutineExercises,
        )

    private suspend fun pushWorkouts(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = workoutDao::getDirtyWorkouts,
        write = { w: WorkoutEntity -> paths.workout(w.id).set(w.toDto()).await() },
        idOf = WorkoutEntity::id,
        clearDirty = workoutDao::clearDirtyWorkouts,
    )

    private suspend fun pushWorkoutExercises(paths: FirestoreSyncPaths, budget: Int): Int =
        pushDirty(
            budget = budget,
            getDirty = workoutDao::getDirtyWorkoutExercises,
            write = { we: WorkoutExerciseEntity ->
                paths.workoutExercise(we.workoutId, we.id).set(we.toDto()).await()
            },
            idOf = WorkoutExerciseEntity::id,
            clearDirty = workoutDao::clearDirtyWorkoutExercises,
        )

    /** [SetEntryEntity] only stores its direct parent (`workoutExerciseId`), not the workout id
     * its Firestore path also needs - one extra local lookup per row, same as the tombstone write
     * path already needed for the same reason. */
    private suspend fun pushSetEntries(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = workoutDao::getDirtySetEntries,
        write = { s: SetEntryEntity ->
            val workoutId = requireNotNull(workoutDao.workoutIdOf(s.workoutExerciseId))
            paths.setEntry(workoutId, s.workoutExerciseId, s.id).set(s.toDto()).await()
        },
        idOf = SetEntryEntity::id,
        clearDirty = workoutDao::clearDirtySetEntries,
    )

    /** See [pushSetEntries] - same shape, for the cardio side. */
    private suspend fun pushCardioEntries(paths: FirestoreSyncPaths, budget: Int): Int = pushDirty(
        budget = budget,
        getDirty = workoutDao::getDirtyCardioEntries,
        write = { c: CardioEntryEntity ->
            val workoutId = requireNotNull(workoutDao.workoutIdOf(c.workoutExerciseId))
            paths.cardioEntry(workoutId, c.workoutExerciseId, c.id).set(c.toDto()).await()
        },
        idOf = CardioEntryEntity::id,
        clearDirty = workoutDao::clearDirtyCardioEntries,
    )

    /** The single preferences document - not list-shaped, so it doesn't go through [pushDirty]. */
    private suspend fun pushPreferences(paths: FirestoreSyncPaths) {
        val dirty = preferencesRepository.getDirtyPreferences() ?: return
        paths.preferences().set(dirty.preferences.toDto(dirty.lastModifiedAt)).await()
        preferencesRepository.clearPreferencesDirty()
    }

    private suspend fun processTombstones(paths: FirestoreSyncPaths, limit: Int) {
        val tombstones = tombstoneDao.getOldest(limit)
        if (tombstones.size == limit) hasMoreWork = true
        for (tombstone in tombstones) {
            paths.forTombstone(tombstone).delete().await()
            tombstoneDao.clear(tombstone.entityType, tombstone.entityId)
        }
    }

    /** [dev.gouthaman.regimen.sync.auth.isSessionRevoked] checked first: a revoked Google grant
     * can surface as any number of underlying exception shapes depending on which Firestore call
     * hit it, so this takes priority over the plain network check rather than risking it getting
     * misclassified as a transient `NETWORK` failure that a retry would never actually fix. Force
     * signs out locally the moment this is detected, so the UI falls back to the signed-out state
     * instead of silently retrying a session that can never succeed again. */
    private fun Throwable.toReason(): AuthErrorReason = when {
        isSessionRevoked() -> {
            firebaseAuth.signOut()
            AuthErrorReason.SESSION_REVOKED
        }

        this is FirebaseNetworkException -> AuthErrorReason.NETWORK
        else -> AuthErrorReason.UNKNOWN
    }
}
