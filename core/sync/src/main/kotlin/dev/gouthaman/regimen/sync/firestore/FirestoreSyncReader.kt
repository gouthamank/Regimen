package dev.gouthaman.regimen.sync.firestore

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.sync.device.refreshLock
import dev.gouthaman.regimen.sync.network.awaitOrTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Everything [FirestoreSyncReader.readAll] pulls back for one account, already mapped to Room
 * entities (`isDirty = false` throughout - these rows are the cloud's own record of themselves,
 * not a local edit awaiting push) and ready for a wholesale local replace. [preferences] is
 * `null` if the account has never pushed a preferences document at all. */
data class PulledSyncData(
    val exercises: List<ExerciseEntity>,
    val measurementTypes: List<MeasurementTypeEntity>,
    val bodyMetrics: List<BodyMetricEntity>,
    val routines: List<RoutineEntity>,
    val routineExercises: List<RoutineExerciseEntity>,
    val workouts: List<WorkoutEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val setEntries: List<SetEntryEntity>,
    val cardioEntries: List<CardioEntryEntity>,
    val preferences: PreferencesDto?,
)

/** Every document under one account's synced tree, listed straight from the server. Nested
 * documents carry their parent's id in their own reference path, so no separate parent lists are
 * needed. */
class CloudSyncTree(
    val exercises: List<DocumentSnapshot>,
    val measurementTypes: List<DocumentSnapshot>,
    val bodyMetrics: List<DocumentSnapshot>,
    val routines: List<DocumentSnapshot>,
    val routineExercises: List<DocumentSnapshot>,
    val workouts: List<DocumentSnapshot>,
    val workoutExercises: List<DocumentSnapshot>,
    val setEntries: List<DocumentSnapshot>,
    val cardioEntries: List<DocumentSnapshot>,
) {
    /** Children before their parents at every level: if a delete is interrupted partway, this
     * leaves a harmless "parent with fewer children" state that a later [FirestoreSyncReader.listAll]
     * still finds, while parent-first would orphan children nothing could ever find again. */
    fun referencesDeepestFirst(): List<DocumentReference> =
        (setEntries + cardioEntries + workoutExercises + routineExercises +
                workouts + routines + exercises + measurementTypes + bodyMetrics)
            .map { it.reference }
}

/** Reads and deletes one account's entire synced tree - the mirror image of
 * [FirestoreSyncPaths]/the push job's writes. Subcollections are listed one parent at a time (not a
 * `collectionGroup` query, which would need its own security rules and doesn't naturally scope to
 * one user), but in parallel, bounded by [MAX_PARALLEL_READS]. Every read goes to the server - a
 * cache fallback while offline would silently return stale or partial data. */
class FirestoreSyncReader(private val firestore: FirebaseFirestore, uid: String) {
    private val userDoc = firestore.collection("users").document(uid)

    /** [onListed] hears the running total of documents listed so far, one call at a time. */
    suspend fun listAll(onListed: suspend (Int) -> Unit = {}): CloudSyncTree = coroutineScope {
        val permits = Semaphore(MAX_PARALLEL_READS)
        val progressLock = Mutex()
        var listed = 0

        suspend fun list(collection: CollectionReference): List<DocumentSnapshot> {
            val documents = permits.withPermit {
                collection.get(Source.SERVER).awaitOrTimeout().documents
            }
            progressLock.withLock {
                listed += documents.size
                onListed(listed)
            }
            return documents
        }

        suspend fun List<DocumentSnapshot>.listChildren(name: String): List<DocumentSnapshot> =
            map { async { list(it.reference.collection(name)) } }.awaitAll().flatten()

        val exercises = async { list(userDoc.collection("exercises")) }
        val measurementTypes = async { list(userDoc.collection("measurementTypes")) }
        val bodyMetrics = async { list(userDoc.collection("bodyMetrics")) }
        val routines = async { list(userDoc.collection("routines")) }
        val workouts = async { list(userDoc.collection("workouts")) }

        val routineExercises = async { routines.await().listChildren("routineExercises") }
        val workoutExercises = workouts.await().listChildren("workoutExercises")
        val setEntries = async { workoutExercises.listChildren("setEntries") }
        val cardioEntries = async { workoutExercises.listChildren("cardioEntries") }

        CloudSyncTree(
            exercises = exercises.await(),
            measurementTypes = measurementTypes.await(),
            bodyMetrics = bodyMetrics.await(),
            routines = routines.await(),
            routineExercises = routineExercises.await(),
            workouts = workouts.await(),
            workoutExercises = workoutExercises,
            setEntries = setEntries.await(),
            cardioEntries = cardioEntries.await(),
        )
    }

    /** "Pull cloud data"'s read side. [onRead] as [listAll]'s `onListed`. */
    suspend fun readAll(onRead: suspend (Int) -> Unit = {}): PulledSyncData {
        val tree = listAll(onRead)
        val preferences = userDoc.collection("preferences").document("current")
            .get(Source.SERVER).awaitOrTimeout().toObject(PreferencesDto::class.java)

        return PulledSyncData(
            exercises = tree.exercises.map {
                requireNotNull(it.toObject(ExerciseDto::class.java)).toEntity(it.id)
            },
            measurementTypes = tree.measurementTypes.map {
                requireNotNull(it.toObject(MeasurementTypeDto::class.java)).toEntity(it.id)
            },
            bodyMetrics = tree.bodyMetrics.map {
                requireNotNull(it.toObject(BodyMetricDto::class.java)).toEntity(it.id)
            },
            routines = tree.routines.map {
                requireNotNull(it.toObject(RoutineDto::class.java)).toEntity(it.id)
            },
            routineExercises = tree.routineExercises.map {
                requireNotNull(it.toObject(RoutineExerciseDto::class.java))
                    .toEntity(it.id, it.parentId())
            },
            workouts = tree.workouts.map {
                requireNotNull(it.toObject(WorkoutDto::class.java)).toEntity(it.id)
            },
            workoutExercises = tree.workoutExercises.map {
                requireNotNull(it.toObject(WorkoutExerciseDto::class.java))
                    .toEntity(it.id, it.parentId())
            },
            setEntries = tree.setEntries.map {
                requireNotNull(it.toObject(SetEntryDto::class.java)).toEntity(it.id, it.parentId())
            },
            cardioEntries = tree.cardioEntries.map {
                requireNotNull(it.toObject(CardioEntryDto::class.java))
                    .toEntity(it.id, it.parentId())
            },
            preferences = preferences,
        )
    }

    /** Server-side `count()` aggregation, not a full fetch - a confirmation dialog's "N workouts"
     * shouldn't burn read quota proportional to history size. Every document in `workouts` is
     * already `COMPLETE` by construction (the push job never writes anything else there), so no
     * status filter is needed. */
    suspend fun countWorkouts(): Int =
        userDoc.collection("workouts").count().get(AggregateSource.SERVER).awaitOrTimeout()
            .count.toInt()

    /** Deletes everything [tree] lists, plus the preferences document, in [WRITE_BATCH_LIMIT]-sized
     * batches, deepest first (see [CloudSyncTree.referencesDeepestFirst]). [lockRef], if given,
     * has its lease refreshed in every batch. [onDeleted] hears (deleted so far, total). */
    suspend fun deleteAll(
        tree: CloudSyncTree,
        lockRef: DocumentReference? = null,
        onDeleted: suspend (Int, Int) -> Unit = { _, _ -> },
    ) {
        val references = tree.referencesDeepestFirst() +
                userDoc.collection("preferences").document("current")
        var deleted = 0
        for (chunk in references.chunked(WRITE_BATCH_LIMIT)) {
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            lockRef?.let { batch.refreshLock(it) }
            batch.commit().awaitOrTimeout()
            deleted += chunk.size
            onDeleted(deleted, references.size)
        }
    }

    private fun DocumentSnapshot.parentId(): String = requireNotNull(reference.parent.parent).id
}

/** Firestore's 500-writes-per-batch limit, minus one slot for a lock-lease refresh. */
internal const val WRITE_BATCH_LIMIT = 499

/** Enough to hide per-request latency on a large tree without flooding the connection. */
private const val MAX_PARALLEL_READS = 16
