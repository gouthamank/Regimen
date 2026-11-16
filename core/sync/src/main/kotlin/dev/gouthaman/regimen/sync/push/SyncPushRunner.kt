package dev.gouthaman.regimen.sync.push

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
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
import dev.gouthaman.regimen.sync.device.FreshnessWatermarkStore
import dev.gouthaman.regimen.sync.device.LOCK_STALE_AFTER_MS
import dev.gouthaman.regimen.sync.device.SyncConfigDto
import dev.gouthaman.regimen.sync.device.refreshLock
import dev.gouthaman.regimen.sync.device.releaseLock
import dev.gouthaman.regimen.sync.firestore.FirestoreSyncPaths
import dev.gouthaman.regimen.sync.firestore.WRITE_BATCH_LIMIT
import dev.gouthaman.regimen.sync.firestore.toDto
import dev.gouthaman.regimen.sync.network.awaitOrTimeout
import dev.gouthaman.regimen.sync.network.isNetworkAvailable
import dev.gouthaman.regimen.sync.network.isNetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val PUSH_BATCH_CAP = 1000
private const val DELETE_BATCH_CAP = 1000

/** A backstop against a hang, not a tight SLA - each batch commit already has its own timeout
 * (see [awaitOrTimeout]), so this only catches something stuck outside of those. */
private const val PUSH_TIMEOUT_MS = 5 * 60_000L

internal data class DirtyBatchResult(val remainingBudget: Int, val hasMore: Boolean)

/** Shared read-commit-clear loop for every list-shaped synced entity type, kept dependency-free so
 * it's unit-testable with fake lambdas. Commits the dirty rows in [chunkSize] batches and clears
 * each batch's dirty flags only once that batch's commit succeeds (a Firestore batch is atomic), so
 * a mid-run exception leaves exactly the unconfirmed rows dirty. [onCommitted] hears each
 * confirmed batch's size. [DirtyBatchResult.hasMore] just signals a full page was read, not a
 * precise count. */
internal suspend fun <T> pushDirtyBatch(
    budget: Int,
    getDirty: suspend (Int) -> List<T>,
    commit: suspend (List<T>) -> Unit,
    idOf: (T) -> String,
    clearDirty: suspend (List<String>) -> Unit,
    chunkSize: Int = WRITE_BATCH_LIMIT,
    onCommitted: suspend (Int) -> Unit = {},
): DirtyBatchResult {
    if (budget <= 0) return DirtyBatchResult(remainingBudget = 0, hasMore = false)
    val dirty = getDirty(budget)
    for (chunk in dirty.chunked(chunkSize)) {
        commit(chunk)
        clearDirty(chunk.map(idOf))
        onCommitted(chunk.size)
    }
    return DirtyBatchResult(remainingBudget = budget - dirty.size, hasMore = dirty.size == budget)
}

/** One push run's fixed context, handed to every per-entity step. */
private class PushRun(
    val paths: FirestoreSyncPaths,
    val syncConfigRef: DocumentReference,
    val onCommitted: suspend (Int) -> Unit,
)

/**
 * The primary device's incremental sync push - reads every dirty row/tombstone, writes/deletes
 * them via batched Firestore commits, and clears each batch only once its own commit is confirmed
 * (so a mid-run failure leaves only the unconfirmed rows dirty). Re-checks primary status between
 * every entity-type step and holds a soft `syncConfig.lockedAt` lease for the run's duration
 * (refreshed in every batch), guarding against a different device claiming primary mid-run.
 * Shared by [SyncPushWorker], `AccountViewModel`'s manual "Sync now", and "Claim primary"'s forced
 * re-upload.
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
    private val watermarkStore: FreshnessWatermarkStore,
) : SyncPushRepository {
    /** Set once per [push] call whenever a dirty/tombstone read returns a full page at its
     * requested limit - a (harmless, self-correcting) signal that there may be more work than
     * this run's cap allowed for, not a precise count. */
    private var hasMoreWork = false

    override suspend fun getLastStatus(): SyncStatus = syncStatusStore.get()

    /** Persists every [SyncStatus] [runPush] returns except the not-primary no-op - persisting
     * that would overwrite a genuinely-synced status with "Not yet synced" just because this
     * device lost primary status, even though it really did sync before. */
    override suspend fun push(): SyncStatus {
        val status = runPush(bypassLock = false, onCommitted = {})
        if (status != notPrimaryStatus()) syncStatusStore.save(status)
        return status
    }

    /** See the interface doc on [dev.gouthaman.regimen.domain.repository.SyncPushRepository.forcePush]
     * for why this exists and skips both the lock and watermark checks. Runs pass after pass until
     * a pass leaves nothing behind, fails, or finds this device isn't primary anymore. Persists
     * its final result, same as [push]. */
    override suspend fun forcePush(onCommitted: suspend (Int) -> Unit): SyncStatus {
        var status: SyncStatus
        do {
            status = runPush(bypassLock = true, onCommitted = onCommitted)
        } while (status.lastError == null && status.lastSyncedAt != null && !status.isFullyUpToDate)
        if (status != notPrimaryStatus()) syncStatusStore.save(status)
        return status
    }

    private suspend fun runPush(
        bypassLock: Boolean,
        onCommitted: suspend (Int) -> Unit,
    ): SyncStatus {
        // Fails fast without touching Firestore - offline, a bare `.await()` doesn't reliably
        // fail quickly, which left "Sync now" spinning forever in airplane mode. The periodic job
        // never hits this (WorkManager's own NetworkType.CONNECTED constraint covers it); only the
        // manual path needs its own check.
        if (!context.isNetworkAvailable()) {
            return SyncStatus(
                lastSyncedAt = null,
                isFullyUpToDate = false,
                lastError = AuthErrorReason.NETWORK
            )
        }

        return try {
            withTimeout(PUSH_TIMEOUT_MS) { doPush(bypassLock, onCommitted) }
        } catch (e: TimeoutCancellationException) {
            SyncStatus(
                lastSyncedAt = null,
                isFullyUpToDate = false,
                lastError = AuthErrorReason.NETWORK
            )
        }
    }

    /** [bypassLock] means the caller ("Claim primary") already holds the lock - this run then
     * neither checks, takes, nor releases it, only refreshes it per batch. */
    private suspend fun doPush(
        bypassLock: Boolean,
        onCommitted: suspend (Int) -> Unit
    ): SyncStatus {
        val uid = firebaseAuth.currentUser?.uid
            ?: return notPrimaryStatus()
        // Not an error - a device that isn't primary anymore has nothing to report beyond "not my
        // turn." The caller (the WorkManager job) is what should notice this and cancel its own
        // periodic schedule, per the Primary-status check item in the doc.
        if (!syncDeviceRepository.isPrimary()) return notPrimaryStatus()

        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")
        val run = PushRun(FirestoreSyncPaths(firestore, uid), syncConfigRef, onCommitted)

        if (!bypassLock) {
            // One read serves both checks below - not a transaction, so a claim or watermark
            // restore landing in the narrow gap after this read is an accepted, low-probability
            // risk.
            val config = syncConfigRef.get().awaitOrTimeout().toObject(SyncConfigDto::class.java)

            // Refuses to start while "Claim primary" is actively wiping/rebuilding this account
            // (holding the same lock for its own, longer-than-a-push duration) - otherwise this
            // run could write fresh data into a destination that's concurrently being deleted out
            // from under it.
            val existingLock = config?.lockedAt
            if (existingLock != null &&
                System.currentTimeMillis() - existingLock < LOCK_STALE_AFTER_MS
            ) {
                return notPrimaryStatus()
            }

            // Freshness watermark: device-ID match alone isn't enough - Auto Backup can restore a
            // snapshot older than the last successful push, carrying a stale isDirty=true that
            // would overwrite the cloud's newer state. A mismatch here means refuse rather than
            // trust it; a successful pull resets the watermark to match what it read.
            if (watermarkStore.get() != config?.lastPushedAt) {
                return notPrimaryStatus()
            }
        }

        hasMoreWork = false
        var budget = PUSH_BATCH_CAP

        // A soft lease for the duration of this run, shared with "Claim primary" (which also
        // checks and holds it, for its own longer wipe-and-force-push duration) - see
        // SyncConfigDto's own doc for the full picture.
        if (!bypassLock) {
            syncConfigRef.set(mapOf("lockedAt" to System.currentTimeMillis()), SetOptions.merge())
                .awaitOrTimeout()
        }
        try {
            budget = pushExercises(run, budget); ensureStillPrimary()
            budget = pushMeasurementTypes(run, budget); ensureStillPrimary()
            budget = pushBodyMetrics(run, budget); ensureStillPrimary()
            budget = pushRoutines(run, budget); ensureStillPrimary()
            budget = pushRoutineExercises(run, budget); ensureStillPrimary()
            budget = pushWorkouts(run, budget); ensureStillPrimary()
            budget = pushWorkoutExercises(run, budget); ensureStillPrimary()
            budget = pushSetEntries(run, budget); ensureStillPrimary()
            budget = pushCardioEntries(run, budget); ensureStillPrimary()
            pushPreferences(run); ensureStillPrimary()
            // Firestore deletes have their own daily quota, separate from writes - its own cap
            // rather than sharing the write-side budget.
            processTombstones(run, DELETE_BATCH_CAP)
        } catch (e: NotPrimaryAnymoreException) {
            return notPrimaryStatus()
        } catch (e: CancellationException) {
            throw e
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
            if (!bypassLock) withContext(NonCancellable) { releaseLock(syncConfigRef) }
        }

        val now = System.currentTimeMillis()
        syncConfigRef.set(mapOf("lastPushedAt" to now), SetOptions.merge()).awaitOrTimeout()
        // The freshness watermark's write side - keeps this device's local record in lockstep
        // with what it just told the cloud, so the next run's comparison above matches.
        watermarkStore.set(now)
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

    /** Commits [items] as one atomic batch, with a lease refresh riding along. */
    private suspend fun <T> commitBatch(
        run: PushRun,
        items: List<T>,
        add: suspend WriteBatch.(T) -> Unit,
    ) {
        val batch = firestore.batch()
        for (item in items) batch.add(item)
        batch.refreshLock(run.syncConfigRef)
        batch.commit().awaitOrTimeout()
    }

    private suspend fun <T> pushDirty(
        run: PushRun,
        budget: Int,
        getDirty: suspend (Int) -> List<T>,
        write: suspend WriteBatch.(T) -> Unit,
        idOf: (T) -> String,
        clearDirty: suspend (List<String>) -> Unit,
    ): Int {
        val result = pushDirtyBatch(
            budget = budget,
            getDirty = getDirty,
            commit = { items -> commitBatch(run, items, write) },
            idOf = idOf,
            clearDirty = clearDirty,
            onCommitted = run.onCommitted,
        )
        if (result.hasMore) hasMoreWork = true
        return result.remainingBudget
    }

    private suspend fun pushExercises(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = exerciseDao::getDirty,
        write = { e: ExerciseEntity -> set(run.paths.exercise(e.id), e.toDto()) },
        idOf = ExerciseEntity::id,
        clearDirty = exerciseDao::clearDirty,
    )

    private suspend fun pushMeasurementTypes(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = measurementDao::getDirtyTypes,
        write = { t: MeasurementTypeEntity -> set(run.paths.measurementType(t.id), t.toDto()) },
        idOf = MeasurementTypeEntity::id,
        clearDirty = measurementDao::clearDirtyTypes,
    )

    private suspend fun pushBodyMetrics(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = measurementDao::getDirtyMetrics,
        write = { m: BodyMetricEntity -> set(run.paths.bodyMetric(m.id), m.toDto()) },
        idOf = BodyMetricEntity::id,
        clearDirty = measurementDao::clearDirtyMetrics,
    )

    private suspend fun pushRoutines(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = routineDao::getDirtyRoutines,
        write = { r: RoutineEntity -> set(run.paths.routine(r.id), r.toDto()) },
        idOf = RoutineEntity::id,
        clearDirty = routineDao::clearDirtyRoutines,
    )

    private suspend fun pushRoutineExercises(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = routineDao::getDirtyRoutineExercises,
        write = { re: RoutineExerciseEntity ->
            set(run.paths.routineExercise(re.routineId, re.id), re.toDto())
        },
        idOf = RoutineExerciseEntity::id,
        clearDirty = routineDao::clearDirtyRoutineExercises,
    )

    private suspend fun pushWorkouts(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = workoutDao::getDirtyWorkouts,
        write = { w: WorkoutEntity -> set(run.paths.workout(w.id), w.toDto()) },
        idOf = WorkoutEntity::id,
        clearDirty = workoutDao::clearDirtyWorkouts,
    )

    private suspend fun pushWorkoutExercises(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = workoutDao::getDirtyWorkoutExercises,
        write = { we: WorkoutExerciseEntity ->
            set(run.paths.workoutExercise(we.workoutId, we.id), we.toDto())
        },
        idOf = WorkoutExerciseEntity::id,
        clearDirty = workoutDao::clearDirtyWorkoutExercises,
    )

    /** [SetEntryEntity] only stores its direct parent (`workoutExerciseId`), not the workout id
     * its Firestore path also needs - one extra local lookup per row, same as the tombstone write
     * path already needed for the same reason. */
    private suspend fun pushSetEntries(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = workoutDao::getDirtySetEntries,
        write = { s: SetEntryEntity ->
            val workoutId = requireNotNull(workoutDao.workoutIdOf(s.workoutExerciseId))
            set(run.paths.setEntry(workoutId, s.workoutExerciseId, s.id), s.toDto())
        },
        idOf = SetEntryEntity::id,
        clearDirty = workoutDao::clearDirtySetEntries,
    )

    /** See [pushSetEntries] - same shape, for the cardio side. */
    private suspend fun pushCardioEntries(run: PushRun, budget: Int): Int = pushDirty(
        run = run,
        budget = budget,
        getDirty = workoutDao::getDirtyCardioEntries,
        write = { c: CardioEntryEntity ->
            val workoutId = requireNotNull(workoutDao.workoutIdOf(c.workoutExerciseId))
            set(run.paths.cardioEntry(workoutId, c.workoutExerciseId, c.id), c.toDto())
        },
        idOf = CardioEntryEntity::id,
        clearDirty = workoutDao::clearDirtyCardioEntries,
    )

    /** The single preferences document - not list-shaped, so it doesn't go through [pushDirty]. */
    private suspend fun pushPreferences(run: PushRun) {
        val dirty = preferencesRepository.getDirtyPreferences() ?: return
        run.paths.preferences().set(dirty.preferences.toDto(dirty.lastModifiedAt)).awaitOrTimeout()
        preferencesRepository.clearPreferencesDirty()
    }

    private suspend fun processTombstones(run: PushRun, limit: Int) {
        val tombstones = tombstoneDao.getOldest(limit)
        if (tombstones.size == limit) hasMoreWork = true
        for (chunk in tombstones.chunked(WRITE_BATCH_LIMIT)) {
            commitBatch(run, chunk) { tombstone -> delete(run.paths.forTombstone(tombstone)) }
            chunk.forEach { tombstoneDao.clear(it.entityType, it.entityId) }
        }
    }

    /** Checks [dev.gouthaman.regimen.sync.auth.isSessionRevoked] first - a revoked grant can
     * surface as many different exception shapes, so it takes priority over misclassifying it as
     * a retriable `NETWORK` failure. Force signs out locally so the UI falls back correctly. */
    private fun Throwable.toReason(): AuthErrorReason = when {
        isSessionRevoked() -> {
            firebaseAuth.signOut()
            AuthErrorReason.SESSION_REVOKED
        }

        isNetworkFailure() -> AuthErrorReason.NETWORK
        else -> AuthErrorReason.UNKNOWN
    }
}
