package dev.gouthaman.regimen.sync.firestore

import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import kotlinx.coroutines.tasks.await

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

/** Reads one account's entire synced state back out of Firestore for "Pull cloud data" - the
 * mirror image of [FirestoreSyncPaths]/the push job's per-row writes. Subcollections are read one
 * parent at a time (not a `collectionGroup` query, which would need its own security rules and
 * doesn't naturally scope to one user), matching the push job's own per-row-round-trip design -
 * acceptable here since this runs once, on explicit user action, not on a schedule. */
class FirestoreSyncReader(firestore: FirebaseFirestore, uid: String) {
    private val userDoc = firestore.collection("users").document(uid)

    suspend fun readAll(): PulledSyncData {
        val exercises = userDoc.collection("exercises").get().await().documents.map {
            requireNotNull(it.toObject(ExerciseDto::class.java)).toEntity(it.id)
        }
        val measurementTypes = userDoc.collection("measurementTypes").get().await().documents.map {
            requireNotNull(it.toObject(MeasurementTypeDto::class.java)).toEntity(it.id)
        }
        val bodyMetrics = userDoc.collection("bodyMetrics").get().await().documents.map {
            requireNotNull(it.toObject(BodyMetricDto::class.java)).toEntity(it.id)
        }

        val routines = mutableListOf<RoutineEntity>()
        val routineExercises = mutableListOf<RoutineExerciseEntity>()
        for (routineDoc in userDoc.collection("routines").get().await().documents) {
            routines += requireNotNull(routineDoc.toObject(RoutineDto::class.java)).toEntity(
                routineDoc.id
            )
            val reSnapshot = routineDoc.reference.collection("routineExercises").get().await()
            for (reDoc in reSnapshot.documents) {
                routineExercises += requireNotNull(reDoc.toObject(RoutineExerciseDto::class.java))
                    .toEntity(reDoc.id, routineDoc.id)
            }
        }

        val workouts = mutableListOf<WorkoutEntity>()
        val workoutExercises = mutableListOf<WorkoutExerciseEntity>()
        val setEntries = mutableListOf<SetEntryEntity>()
        val cardioEntries = mutableListOf<CardioEntryEntity>()
        for (workoutDoc in userDoc.collection("workouts").get().await().documents) {
            workouts += requireNotNull(workoutDoc.toObject(WorkoutDto::class.java)).toEntity(
                workoutDoc.id
            )
            val weSnapshot = workoutDoc.reference.collection("workoutExercises").get().await()
            for (weDoc in weSnapshot.documents) {
                workoutExercises += requireNotNull(weDoc.toObject(WorkoutExerciseDto::class.java))
                    .toEntity(weDoc.id, workoutDoc.id)
                val seSnapshot = weDoc.reference.collection("setEntries").get().await()
                for (seDoc in seSnapshot.documents) {
                    setEntries += requireNotNull(seDoc.toObject(SetEntryDto::class.java))
                        .toEntity(seDoc.id, weDoc.id)
                }
                val ceSnapshot = weDoc.reference.collection("cardioEntries").get().await()
                for (ceDoc in ceSnapshot.documents) {
                    cardioEntries += requireNotNull(ceDoc.toObject(CardioEntryDto::class.java))
                        .toEntity(ceDoc.id, weDoc.id)
                }
            }
        }

        val preferences = userDoc.collection("preferences").document("current")
            .get().await().toObject(PreferencesDto::class.java)

        return PulledSyncData(
            exercises = exercises,
            measurementTypes = measurementTypes,
            bodyMetrics = bodyMetrics,
            routines = routines,
            routineExercises = routineExercises,
            workouts = workouts,
            workoutExercises = workoutExercises,
            setEntries = setEntries,
            cardioEntries = cardioEntries,
            preferences = preferences,
        )
    }

    /** Server-side `count()` aggregation, not a full fetch - a confirmation dialog's "N workouts"
     * shouldn't burn read quota proportional to history size. Every document in `workouts` is
     * already `COMPLETE` by construction (the push job never writes anything else there), so no
     * status filter is needed. */
    suspend fun countWorkouts(): Int =
        userDoc.collection("workouts").count().get(AggregateSource.SERVER).await().count.toInt()

    /** "Claim primary"'s wipe side, before its forced full re-push - deletes every document this
     * account's push job ever wrote, recursively including every nested subcollection. Firestore
     * has no cascade delete, so deleting only the top-level `workouts`/`routines` documents (the
     * way [dev.gouthaman.regimen.sync.auth.AuthRepositoryImpl.deleteCloudData] currently does)
     * would orphan their `workoutExercises`/`routineExercises`/`setEntries`/`cardioEntries`
     * subcollections rather than actually removing them - a separate, pre-existing gap in that
     * method, not something this class copies. Deletes children before their parent at every
     * level, not just for tidiness - Firestore has no referential integrity either, so deleting a
     * parent first wouldn't error, it would silently orphan its children. If this gets
     * interrupted partway (e.g. a network drop), children-first leaves a harmless "parent with no
     * children yet" state, safely re-deletable on retry; parent-first would leave genuinely
     * orphaned children with no parent - invisible garbage a future call would never even find,
     * since discovery here always starts from the top-level collections and descends. */
    suspend fun deleteAll() {
        for (routineDoc in userDoc.collection("routines").get().await().documents) {
            for (reDoc in routineDoc.reference.collection("routineExercises").get()
                .await().documents) {
                reDoc.reference.delete().await()
            }
            routineDoc.reference.delete().await()
        }
        for (workoutDoc in userDoc.collection("workouts").get().await().documents) {
            for (weDoc in workoutDoc.reference.collection("workoutExercises").get()
                .await().documents) {
                for (seDoc in weDoc.reference.collection("setEntries").get().await().documents) {
                    seDoc.reference.delete().await()
                }
                for (ceDoc in weDoc.reference.collection("cardioEntries").get().await().documents) {
                    ceDoc.reference.delete().await()
                }
                weDoc.reference.delete().await()
            }
            workoutDoc.reference.delete().await()
        }
        for (doc in userDoc.collection("exercises").get().await().documents) doc.reference.delete()
            .await()
        for (doc in userDoc.collection("measurementTypes").get()
            .await().documents) doc.reference.delete().await()
        for (doc in userDoc.collection("bodyMetrics").get()
            .await().documents) doc.reference.delete().await()
        userDoc.collection("preferences").document("current").delete().await()
    }
}
