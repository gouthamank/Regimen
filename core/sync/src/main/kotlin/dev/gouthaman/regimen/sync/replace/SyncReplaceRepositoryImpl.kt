package dev.gouthaman.regimen.sync.replace

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.prefs.PreferencesRepositoryImpl
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.SyncOperationProgress
import dev.gouthaman.regimen.domain.model.SyncOperationStep
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceException
import dev.gouthaman.regimen.domain.repository.SyncPushRepository
import dev.gouthaman.regimen.domain.repository.SyncReplaceRepository
import dev.gouthaman.regimen.sync.auth.isSessionRevoked
import dev.gouthaman.regimen.sync.device.DeviceIdentityStore
import dev.gouthaman.regimen.sync.device.FreshnessWatermarkStore
import dev.gouthaman.regimen.sync.device.LOCK_STALE_AFTER_MS
import dev.gouthaman.regimen.sync.device.SyncConfigDto
import dev.gouthaman.regimen.sync.device.releaseLock
import dev.gouthaman.regimen.sync.firestore.FirestoreSyncReader
import dev.gouthaman.regimen.sync.network.awaitOrTimeout
import dev.gouthaman.regimen.sync.network.isNetworkAvailable
import dev.gouthaman.regimen.sync.network.isNetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncReplaceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val db: RegimenDatabase,
    private val exerciseDao: ExerciseDao,
    private val measurementDao: MeasurementDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val tombstoneDao: SyncTombstoneDao,
    private val preferencesRepository: PreferencesRepositoryImpl,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val syncPushRepository: SyncPushRepository,
    private val watermarkStore: FreshnessWatermarkStore,
) : SyncReplaceRepository {

    override suspend fun pullCloudData(
        onProgress: suspend (SyncOperationProgress) -> Unit,
    ): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN))
        if (!context.isNetworkAvailable()) {
            return Result.failure(SyncReplaceException(SyncReplaceErrorReason.NETWORK))
        }

        return try {
            // Early exit before the network round-trips below - the authoritative re-check
            // happens inside the wipe's own transaction, since a workout can still start during
            // the cloud read.
            if (workoutDao.hasAnyIncompleteWorkout()) {
                return Result.failure(SyncReplaceException(SyncReplaceErrorReason.WORKOUT_IN_PROGRESS))
            }

            onProgress(SyncOperationProgress(SyncOperationStep.STARTING))
            // Read before the data, not after: a push landing mid-download then leaves this
            // device's watermark older than the cloud's - a safe mismatch the push job refuses on -
            // rather than stamping it as matching data this device never downloaded.
            val cloudLastPushedAt = firestore.collection("users").document(uid)
                .collection("syncConfig").document("current")
                .get(Source.SERVER).awaitOrTimeout()
                .toObject(SyncConfigDto::class.java)?.lastPushedAt

            onProgress(SyncOperationProgress(SyncOperationStep.DOWNLOADING))
            val pulled = FirestoreSyncReader(firestore, uid).readAll { read ->
                onProgress(SyncOperationProgress(SyncOperationStep.DOWNLOADING, done = read))
            }

            onProgress(SyncOperationProgress(SyncOperationStep.SAVING))
            db.withTransaction {
                if (workoutDao.hasAnyIncompleteWorkout()) {
                    throw SyncReplaceException(SyncReplaceErrorReason.WORKOUT_IN_PROGRESS)
                }

                tombstoneDao.deleteAll()
                // Children first, though Room's foreign-key cascades (`onDelete = CASCADE`)
                // would handle this automatically regardless of order - explicit is clearer than
                // relying on cascade ordering.
                workoutDao.deleteAllCompleteWorkouts()
                routineDao.deleteAllRoutines()
                measurementDao.deleteAllMetrics()
                measurementDao.deleteAllCustomTypes()
                exerciseDao.deleteAllCustom()

                exerciseDao.insertAll(pulled.exercises)
                measurementDao.insertAllTypes(pulled.measurementTypes)
                measurementDao.insertAllMetrics(pulled.bodyMetrics)
                routineDao.insertAllRoutines(pulled.routines)
                routineDao.insertRoutineExercises(pulled.routineExercises)
                workoutDao.insertAllWorkouts(pulled.workouts)
                workoutDao.insertAllWorkoutExercises(pulled.workoutExercises)
                workoutDao.insertAllSetEntries(pulled.setEntries)
                workoutDao.insertAllCardioEntries(pulled.cardioEntries)
            }

            pulled.preferences?.let {
                preferencesRepository.applyPulledPreferences(
                    weightUnit = it.weightUnit,
                    distanceUnit = it.distanceUnit,
                    themeMode = it.themeMode,
                    dynamicColor = it.dynamicColor,
                    restDefaultSec = it.restDefaultSec,
                    restChimeEnabled = it.restChimeEnabled,
                    maxWorkoutDuration = it.maxWorkoutDuration,
                    lastModifiedAt = it.lastModifiedAt,
                )
            }

            // Resets the local freshness watermark to match what the cloud says was last
            // pushed - this is what lets a device that failed SyncPushRunner's watermark check
            // resume normal automatic sync afterward, if it's already primary (or becomes primary
            // later without an intervening push from anywhere else).
            watermarkStore.set(cloudLastPushedAt)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toSyncReplaceException())
        }
    }

    override suspend fun claimPrimary(
        onProgress: suspend (SyncOperationProgress) -> Unit,
    ): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN))
        if (!context.isNetworkAvailable()) {
            return Result.failure(SyncReplaceException(SyncReplaceErrorReason.NETWORK))
        }
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")

        try {
            onProgress(SyncOperationProgress(SyncOperationStep.STARTING))
            // Checks lockedAt and claims primary in one transaction, so an in-flight push can't
            // have its destination pulled out from under it mid-claim. Unlike a normal push, this
            // claim holds the lock for its entire duration (through the wipe and force-push
            // below) - SyncPushRunner refuses to start while it's held, closing the reverse race.
            firestore.runTransaction { transaction ->
                val lockedAt =
                    transaction.get(syncConfigRef).toObject(SyncConfigDto::class.java)?.lockedAt
                if (lockedAt != null && System.currentTimeMillis() - lockedAt < LOCK_STALE_AFTER_MS) {
                    throw SyncReplaceException(SyncReplaceErrorReason.PUSH_IN_PROGRESS)
                }
                transaction.set(
                    syncConfigRef,
                    mapOf("primaryDeviceId" to deviceId, "lockedAt" to System.currentTimeMillis()),
                    SetOptions.merge(),
                )
            }.awaitOrTimeout()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e.toSyncReplaceException())
        }

        return try {
            // Only reachable once this device is genuinely primary - wipe what's there now and
            // force a complete re-upload of what's local, rather than a merge. Interrupted
            // anywhere from here, this device is left primary but with a watermark that no longer
            // matches the cloud, so it shows the stale-local-state UI and the claim can simply be
            // run again.
            val reader = FirestoreSyncReader(firestore, uid)
            onProgress(SyncOperationProgress(SyncOperationStep.FINDING_CLOUD_DATA))
            val tree = reader.listAll { listed ->
                onProgress(SyncOperationProgress(SyncOperationStep.FINDING_CLOUD_DATA, listed))
            }
            reader.deleteAll(tree, lockRef = syncConfigRef) { deleted, all ->
                onProgress(
                    SyncOperationProgress(
                        SyncOperationStep.DELETING_CLOUD_DATA,
                        deleted,
                        all
                    )
                )
            }

            val total = db.withTransaction {
                tombstoneDao.deleteAll()
                exerciseDao.markAllCustomDirty() +
                        measurementDao.markAllCustomTypesDirty() +
                        measurementDao.markAllMetricsDirty() +
                        routineDao.markAllRoutinesDirty() +
                        routineDao.markAllRoutineExercisesDirty() +
                        workoutDao.markAllCompleteWorkoutsDirty() +
                        workoutDao.markAllWorkoutExercisesDirty() +
                        workoutDao.markAllSetEntriesDirty() +
                        workoutDao.markAllCardioEntriesDirty()
            }
            preferencesRepository.markPreferencesDirty()

            // Reuses the same incremental push loop, not a separate upload path. Uses
            // forcePush(), not push() - this claim already holds syncConfig.lockedAt itself (set
            // in the transaction above), and push()'s own lock/watermark checks would otherwise
            // see that same fresh lock and refuse to run at all, silently no-op'ing the claim.
            var uploaded = 0
            onProgress(SyncOperationProgress(SyncOperationStep.UPLOADING, 0, total))
            val status = syncPushRepository.forcePush { committed ->
                uploaded += committed
                onProgress(SyncOperationProgress(SyncOperationStep.UPLOADING, uploaded, total))
            }
            status.lastError?.let {
                return Result.failure(SyncReplaceException(it.toSyncReplaceReason()))
            }
            // forcePush's not-primary shape: either the session ended mid-claim, or another device
            // claimed primary mid-upload.
            if (status.lastSyncedAt == null) {
                val reason = if (firebaseAuth.currentUser == null) {
                    SyncReplaceErrorReason.SESSION_REVOKED
                } else {
                    SyncReplaceErrorReason.TAKEN_OVER
                }
                return Result.failure(SyncReplaceException(reason))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toSyncReplaceException())
        } finally {
            // Redundant once forcePush has run to completion - necessary for every failure before
            // that, since otherwise this claim's lock would sit held until it goes stale on its
            // own (LOCK_STALE_AFTER_MS), blocking every push in the meantime.
            withContext(NonCancellable) { releaseLock(syncConfigRef) }
        }
    }

    override suspend fun localWorkoutCount(): Int = workoutDao.countCompleteWorkouts()

    override suspend fun cloudWorkoutCount(): Int {
        val uid = firebaseAuth.currentUser?.uid
            ?: throw SyncReplaceException(SyncReplaceErrorReason.UNKNOWN)
        if (!context.isNetworkAvailable()) {
            throw SyncReplaceException(SyncReplaceErrorReason.NETWORK)
        }
        return try {
            FirestoreSyncReader(firestore, uid).countWorkouts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toSyncReplaceException()
        }
    }

    /** A [SyncReplaceException] thrown inside a Firestore transaction can come back wrapped, so the
     * cause is checked too. A revoked session force signs out locally, same as the push job. */
    private fun Throwable.toSyncReplaceException(): SyncReplaceException {
        (this as? SyncReplaceException ?: cause as? SyncReplaceException)?.let { return it }
        val reason = when {
            isSessionRevoked() -> {
                firebaseAuth.signOut()
                SyncReplaceErrorReason.SESSION_REVOKED
            }

            isNetworkFailure() -> SyncReplaceErrorReason.NETWORK
            else -> SyncReplaceErrorReason.UNKNOWN
        }
        return SyncReplaceException(reason, this)
    }

    private fun AuthErrorReason.toSyncReplaceReason(): SyncReplaceErrorReason = when (this) {
        AuthErrorReason.NETWORK -> SyncReplaceErrorReason.NETWORK
        AuthErrorReason.SESSION_REVOKED -> SyncReplaceErrorReason.SESSION_REVOKED
        else -> SyncReplaceErrorReason.UNKNOWN
    }
}
