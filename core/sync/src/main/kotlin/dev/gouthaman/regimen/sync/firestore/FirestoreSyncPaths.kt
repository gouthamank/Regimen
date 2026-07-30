package dev.gouthaman.regimen.sync.firestore

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.data.local.entity.SyncTombstoneEntity

/** Builds every synced entity's Firestore document path for one user - the single source of
 * truth both the push job's writes and its tombstone deletes use, so the two can never disagree
 * about where a given row lives. */
class FirestoreSyncPaths(firestore: FirebaseFirestore, uid: String) {
    private val userDoc = firestore.collection("users").document(uid)

    fun exercise(id: String): DocumentReference = userDoc.collection("exercises").document(id)

    fun measurementType(id: String): DocumentReference =
        userDoc.collection("measurementTypes").document(id)

    fun bodyMetric(id: String): DocumentReference = userDoc.collection("bodyMetrics").document(id)

    fun routine(id: String): DocumentReference = userDoc.collection("routines").document(id)

    fun routineExercise(routineId: String, id: String): DocumentReference =
        routine(routineId).collection("routineExercises").document(id)

    fun workout(id: String): DocumentReference = userDoc.collection("workouts").document(id)

    fun workoutExercise(workoutId: String, id: String): DocumentReference =
        workout(workoutId).collection("workoutExercises").document(id)

    fun setEntry(workoutId: String, workoutExerciseId: String, id: String): DocumentReference =
        workoutExercise(workoutId, workoutExerciseId).collection("setEntries").document(id)

    fun cardioEntry(workoutId: String, workoutExerciseId: String, id: String): DocumentReference =
        workoutExercise(workoutId, workoutExerciseId).collection("cardioEntries").document(id)

    fun preferences(): DocumentReference = userDoc.collection("preferences").document("current")

    /** Dispatches a tombstone to the exact path a live write of that row would have used, so a
     * delete always targets what a push of that same row would have written. */
    fun forTombstone(tombstone: SyncTombstoneEntity): DocumentReference =
        when (tombstone.entityType) {
            SyncEntityType.EXERCISE -> exercise(tombstone.entityId)
            SyncEntityType.MEASUREMENT_TYPE -> measurementType(tombstone.entityId)
            SyncEntityType.BODY_METRIC -> bodyMetric(tombstone.entityId)
            SyncEntityType.ROUTINE -> routine(tombstone.entityId)
            SyncEntityType.ROUTINE_EXERCISE ->
                routineExercise(requireNotNull(tombstone.parentId), tombstone.entityId)

            SyncEntityType.WORKOUT -> workout(tombstone.entityId)
            SyncEntityType.WORKOUT_EXERCISE ->
                workoutExercise(requireNotNull(tombstone.parentId), tombstone.entityId)

            SyncEntityType.SET_ENTRY -> setEntry(
                requireNotNull(tombstone.grandparentId),
                requireNotNull(tombstone.parentId),
                tombstone.entityId,
            )

            SyncEntityType.CARDIO_ENTRY -> cardioEntry(
                requireNotNull(tombstone.grandparentId),
                requireNotNull(tombstone.parentId),
                tombstone.entityId,
            )
        }
}
