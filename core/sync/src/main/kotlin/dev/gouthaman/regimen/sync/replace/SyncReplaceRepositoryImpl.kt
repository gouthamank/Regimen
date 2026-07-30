package dev.gouthaman.regimen.sync.replace

import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.prefs.PreferencesRepositoryImpl
import dev.gouthaman.regimen.domain.model.AuthErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason
import dev.gouthaman.regimen.domain.model.SyncReplaceException
import dev.gouthaman.regimen.domain.repository.SyncPushRepository
import dev.gouthaman.regimen.domain.repository.SyncReplaceRepository
import dev.gouthaman.regimen.sync.device.DeviceIdentityStore
import dev.gouthaman.regimen.sync.device.LOCK_STALE_AFTER_MS
import dev.gouthaman.regimen.sync.device.SyncConfigDto
import dev.gouthaman.regimen.sync.firestore.FirestoreSyncReader
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncReplaceRepositoryImpl @Inject constructor(
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
) : SyncReplaceRepository {

    override suspend fun pullCloudData(): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN))

        return try {
            // Early exit before the network round-trip below - the authoritative re-check
            // happens inside the wipe's own transaction, since a workout can still start during
            // what might be a lengthy cloud read.
            if (workoutDao.hasAnyIncompleteWorkout()) {
                return Result.failure(SyncReplaceException(SyncReplaceErrorReason.WORKOUT_IN_PROGRESS))
            }

            val pulled = FirestoreSyncReader(firestore, uid).readAll()

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

            // Deliberately not resetting a local freshness watermark here - that local store
            // doesn't exist as its own feature yet (see docs/todo-remote-sync.md's Freshness
            // watermark item, Phase 2). Wire this in once it does.
            Result.success(Unit)
        } catch (e: SyncReplaceException) {
            Result.failure(e)
        } catch (e: FirebaseNetworkException) {
            Result.failure(SyncReplaceException(SyncReplaceErrorReason.NETWORK, e))
        } catch (e: Exception) {
            Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN, e))
        }
    }

    override suspend fun claimPrimary(): Result<Unit> {
        val uid = firebaseAuth.currentUser?.uid
            ?: return Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN))
        val deviceId = deviceIdentityStore.getOrCreateDeviceId()
        val syncConfigRef = firestore.collection("users").document(uid)
            .collection("syncConfig").document("current")

        return try {
            // Checks lockedAt and claims primary - setting lockedAt again ourselves in the same
            // write, not just primaryDeviceId - inside one transaction, so a push that's
            // genuinely in flight right now can't have its destination pulled out from under it
            // mid-claim. Unlike a normal push (which only holds the lock for its own run), this
            // claim holds it for its *entire* duration - through the wipe and force-push below,
            // not just this initial flip - and SyncPushRunner now refuses to start at all while
            // it's held (see its own doc), closing the reverse race too: an old primary device's
            // push landing writes into a cloud this claim is concurrently wiping/rebuilding.
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
            }.await()

            try {
                // Only reachable once this device is genuinely primary - wipe what's there now
                // and force a complete re-upload of what's local, rather than a merge.
                FirestoreSyncReader(firestore, uid).deleteAll()

                db.withTransaction {
                    tombstoneDao.deleteAll()
                    exerciseDao.markAllCustomDirty()
                    measurementDao.markAllCustomTypesDirty()
                    measurementDao.markAllMetricsDirty()
                    routineDao.markAllRoutinesDirty()
                    routineDao.markAllRoutineExercisesDirty()
                    workoutDao.markAllCompleteWorkoutsDirty()
                    workoutDao.markAllWorkoutExercisesDirty()
                    workoutDao.markAllSetEntriesDirty()
                    workoutDao.markAllCardioEntriesDirty()
                }
                preferencesRepository.markPreferencesDirty()

                // Reuses the exact same incremental push loop Phase 1 built, not a separate
                // upload path - a full initial backfill can easily exceed one run's batch cap
                // (unlike a typical incremental sync), but that's not a failure here either:
                // whatever's left dirty drains on the next periodic/manual run, same as any
                // capped-partial push. This call also releases the lock above as part of its own
                // normal start-of-run/finally handling, once it actually runs.
                val status = syncPushRepository.push()
                status.lastError?.let {
                    return Result.failure(SyncReplaceException(it.toSyncReplaceReason()))
                }
                Result.success(Unit)
            } finally {
                // Redundant once push() above has actually run (it clears its own lock in its own
                // finally) - necessary if the wipe or dirty-marking failed before ever reaching
                // it, since otherwise this claim's lock would sit held until it goes stale on its
                // own (LOCK_STALE_AFTER_MS), blocking every push in the meantime.
                syncConfigRef.set(mapOf("lockedAt" to null), SetOptions.merge()).await()
            }
        } catch (e: SyncReplaceException) {
            Result.failure(e)
        } catch (e: FirebaseNetworkException) {
            Result.failure(SyncReplaceException(SyncReplaceErrorReason.NETWORK, e))
        } catch (e: Exception) {
            Result.failure(SyncReplaceException(SyncReplaceErrorReason.UNKNOWN, e))
        }
    }

    private fun AuthErrorReason.toSyncReplaceReason(): SyncReplaceErrorReason = when (this) {
        AuthErrorReason.NETWORK -> SyncReplaceErrorReason.NETWORK
        else -> SyncReplaceErrorReason.UNKNOWN
    }

    override suspend fun localWorkoutCount(): Int = workoutDao.countCompleteWorkouts()

    override suspend fun cloudWorkoutCount(): Int {
        val uid = firebaseAuth.currentUser?.uid ?: return 0
        return FirestoreSyncReader(firestore, uid).countWorkouts()
    }
}
