package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseHistorySessionEntity
import dev.gouthaman.regimen.data.local.entity.PersonalRecordRowEntity
import dev.gouthaman.regimen.data.local.entity.RepsRecordRowEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutWithDetailsEntity
import kotlinx.coroutines.flow.Flow

/** One row to insert via [WorkoutDao.insertWorkoutWithExercises] - [workoutExercise]/[sets]/
 * [cardio] already carry their final (caller-generated) ids and workoutId/workoutExerciseId FKs. */
data class NewWorkoutExerciseRow(
    val workoutExercise: WorkoutExerciseEntity,
    val sets: List<SetEntryEntity> = emptyList(),
    val cardio: CardioEntryEntity? = null,
)

@Dao
interface WorkoutDao {
    @Transaction
    @Query(
        "SELECT * FROM workouts WHERE workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY startTime DESC"
    )
    fun observeCompletedWithDetails(): Flow<List<WorkoutWithDetailsEntity>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkout(id: String): Flow<WorkoutWithDetailsEntity?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithDetails(id: String): WorkoutWithDetailsEntity?

    /** The in-progress workout, if any. Used to resume after process death. */
    @Transaction
    @Query(
        "SELECT * FROM workouts WHERE workoutStatus IN ('IN_PROGRESS', 'PAUSED', 'IN_REST_TIME') " +
                "ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun getInProgressWorkout(): WorkoutWithDetailsEntity?

    @Query(
        "SELECT id FROM workouts WHERE workoutStatus IN ('IN_PROGRESS', 'PAUSED', 'IN_REST_TIME') " +
                "ORDER BY startTime DESC LIMIT 1"
    )
    fun observeInProgressId(): Flow<String?>

    /** Most recent completed session of a routine - source for prefill. */
    @Transaction
    @Query(
        "SELECT * FROM workouts WHERE routineId = :routineId " +
                "AND workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun getMostRecentCompletedForRoutine(routineId: String): WorkoutWithDetailsEntity?

    @Query(
        "SELECT * FROM workouts WHERE workoutStatus IN ('COMPLETE', 'EDITING') " +
                "AND startTime BETWEEN :start AND :end ORDER BY startTime"
    )
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<WorkoutEntity>>

    /** Heaviest weight ever lifted for an exercise - the PR definition. */
    @Query(
        "SELECT MAX(se.weightKg) FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId " +
                "AND w.workoutStatus IN ('COMPLETE', 'EDITING') AND se.isComplete = 1"
    )
    fun observeBestWeight(exerciseId: String): Flow<Double?>

    /** Heaviest completed set per exercise across all finished workouts. [excludingWorkoutId]
     * (a sentinel of "" means "none", since a real workout id is never blank) lets a
     * just-finished workout be compared against the record it holds excluding its own sets, to
     * tell "beat the old PR" from "merely tied it". */
    @Query(
        "SELECT we.exerciseId AS exerciseId, MAX(se.weightKg) AS bestWeightKg FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus IN ('COMPLETE', 'EDITING') AND se.isComplete = 1 " +
                "AND se.weightKg IS NOT NULL AND w.id != :excludingWorkoutId " +
                "GROUP BY we.exerciseId"
    )
    fun observePersonalRecords(excludingWorkoutId: String = ""): Flow<List<PersonalRecordRowEntity>>

    /** Best reps per exercise for sets logged without weight (bodyweight) - PR definition
     * when [SetEntryEntity.weightKg] is never stored. See [observePersonalRecords] for
     * [excludingWorkoutId]. */
    @Query(
        "SELECT we.exerciseId AS exerciseId, MAX(se.reps) AS bestReps FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus IN ('COMPLETE', 'EDITING') AND se.isComplete = 1 " +
                "AND se.weightKg IS NULL AND se.reps IS NOT NULL AND w.id != :excludingWorkoutId " +
                "GROUP BY we.exerciseId"
    )
    fun observeBestReps(excludingWorkoutId: String = ""): Flow<List<RepsRecordRowEntity>>

    /** Most recent logged set for an exercise, from any finished workout - prefill source
     * when adding it ad hoc, outside a routine's own history-based prefill. */
    @Query(
        "SELECT se.* FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId " +
                "AND w.workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY w.startTime DESC, se.setNumber DESC LIMIT 1"
    )
    suspend fun getMostRecentSetForExercise(exerciseId: String): SetEntryEntity?

    /** Every finished session that logged this exercise, most recent first - source for Exercise Detail's History section. */
    @Transaction
    @Query(
        "SELECT we.*, w.startTime AS startTime FROM workout_exercises we " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId " +
                "AND w.workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY w.startTime DESC"
    )
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistorySessionEntity>>

    /** True if any workout references this exercise (active or finished); blocks deletion (cascade risk). */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_exercises WHERE exerciseId = :exerciseId)")
    suspend fun isExerciseUsedInAnyWorkout(exerciseId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity)

    /** Inserts [workout] plus every exercise (and its prefilled sets/cardio) in one transaction,
     * instead of one round trip per row - the sequential-await version of this was the dominant
     * latency source when starting a routine-based (or repeated freeform) workout. Every row in
     * [exercises] already carries its final (caller-generated) id and FK columns. */
    @Transaction
    suspend fun insertWorkoutWithExercises(
        workout: WorkoutEntity,
        exercises: List<NewWorkoutExerciseRow>,
    ) {
        insertWorkout(workout)
        exercises.forEach { row ->
            insertWorkoutExercise(row.workoutExercise)
            row.sets.forEach { upsertSet(it) }
            row.cardio?.let { upsertCardio(it) }
        }
    }

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity)

    @Update
    suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity)

    @Delete
    suspend fun deleteWorkoutExercise(item: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: SetEntryEntity)

    @Delete
    suspend fun deleteSet(set: SetEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardio(cardio: CardioEntryEntity)

    @Delete
    suspend fun deleteCardio(cardio: CardioEntryEntity)

    /** Cascade/ancestor-id lookups for the sync tombstone write, which lives in
     * `WorkoutRepositoryImpl` (see [dev.gouthaman.regimen.data.repository.WorkoutRepositoryImpl]) -
     * these DAO methods only expose the raw queries, since only a DAO can run a typed Room query;
     * deciding what to tombstone is the repository's job. */
    @Query("SELECT id FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun workoutExerciseIdsFor(workoutId: String): List<String>

    @Query("SELECT id FROM set_entries WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun setEntryIdsFor(workoutExerciseId: String): List<String>

    @Query("SELECT id FROM cardio_entries WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun cardioEntryIdFor(workoutExerciseId: String): String?

    @Query("SELECT workoutId FROM workout_exercises WHERE id = :workoutExerciseId")
    suspend fun workoutIdOf(workoutExerciseId: String): String?

    /** Sync push job's read/clear side. Only `COMPLETE` workouts are in sync scope - a session
     * that's still mid-flight (`IN_PROGRESS`/`IN_REST_TIME`/`PAUSED`) or reopened for editing
     * (`EDITING`) has no business being pushed until it settles back to `COMPLETE`, so
     * `WorkoutExercise`/`SetEntry`/`CardioEntry` rows are scoped through a join on their workout's
     * status too, not just their own `isDirty` flag. */
    @Query(
        "SELECT * FROM workouts WHERE workoutStatus = 'COMPLETE' AND isDirty = 1 " +
                "ORDER BY lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirtyWorkouts(limit: Int): List<WorkoutEntity>

    @Query("UPDATE workouts SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtyWorkouts(ids: List<String>)

    @Query(
        "SELECT we.* FROM workout_exercises we " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus = 'COMPLETE' AND we.isDirty = 1 " +
                "ORDER BY we.lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirtyWorkoutExercises(limit: Int): List<WorkoutExerciseEntity>

    @Query("UPDATE workout_exercises SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtyWorkoutExercises(ids: List<String>)

    @Query(
        "SELECT se.* FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus = 'COMPLETE' AND se.isDirty = 1 " +
                "ORDER BY se.lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirtySetEntries(limit: Int): List<SetEntryEntity>

    @Query("UPDATE set_entries SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtySetEntries(ids: List<String>)

    @Query(
        "SELECT ce.* FROM cardio_entries ce " +
                "JOIN workout_exercises we ON ce.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus = 'COMPLETE' AND ce.isDirty = 1 " +
                "ORDER BY ce.lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirtyCardioEntries(limit: Int): List<CardioEntryEntity>

    @Query("UPDATE cardio_entries SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtyCardioEntries(ids: List<String>)

    /** "Pull cloud data"'s guard - refuses to wipe local sync-scoped state out from under a live,
     * foreground-service-backed workout session. Checked both before starting the (potentially
     * lengthy) cloud read and again inside the wipe's own transaction, since a workout can start
     * in between. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM workouts WHERE workoutStatus IN " +
                "('IN_PROGRESS', 'IN_REST_TIME', 'PAUSED', 'EDITING'))"
    )
    suspend fun hasAnyIncompleteWorkout(): Boolean

    /** "Claim primary"'s confirmation copy ("N workouts currently on this device") - only
     * `COMPLETE` workouts are ever in sync scope, so this counts what would actually be uploaded,
     * not every local workout regardless of status. */
    @Query("SELECT COUNT(*) FROM workouts WHERE workoutStatus = 'COMPLETE'")
    suspend fun countCompleteWorkouts(): Int

    /** "Pull cloud data"'s wipe/insert side. Only `COMPLETE` workouts are ever in sync scope -
     * after [hasAnyIncompleteWorkout] has confirmed none exist, every local workout is
     * necessarily `COMPLETE`, so this is equivalent to wiping all of them. Cascades
     * `workout_exercises` (then `set_entries`/`cardio_entries`) via their `onDelete = CASCADE`
     * foreign keys. */
    @Query("DELETE FROM workouts WHERE workoutStatus = 'COMPLETE'")
    suspend fun deleteAllCompleteWorkouts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkouts(workouts: List<WorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllWorkoutExercises(items: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSetEntries(sets: List<SetEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCardioEntries(cardio: List<CardioEntryEntity>)

    /** "Claim primary"'s force-full-upload side - see [dev.gouthaman.regimen.data.local.dao.ExerciseDao.markAllCustomDirty].
     * Scoped through a join on the workout's status, same reasoning as [getDirtyWorkoutExercises]
     * et al. - a `workout_exercises`/`set_entries`/`cardio_entries` row under a non-`COMPLETE`
     * workout was never in sync scope to begin with. */
    @Query("UPDATE workouts SET isDirty = 1 WHERE workoutStatus = 'COMPLETE'")
    suspend fun markAllCompleteWorkoutsDirty()

    @Query(
        "UPDATE workout_exercises SET isDirty = 1 WHERE workoutId IN " +
                "(SELECT id FROM workouts WHERE workoutStatus = 'COMPLETE')"
    )
    suspend fun markAllWorkoutExercisesDirty()

    @Query(
        "UPDATE set_entries SET isDirty = 1 WHERE workoutExerciseId IN " +
                "(SELECT we.id FROM workout_exercises we JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus = 'COMPLETE')"
    )
    suspend fun markAllSetEntriesDirty()

    @Query(
        "UPDATE cardio_entries SET isDirty = 1 WHERE workoutExerciseId IN " +
                "(SELECT we.id FROM workout_exercises we JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus = 'COMPLETE')"
    )
    suspend fun markAllCardioEntriesDirty()
}
