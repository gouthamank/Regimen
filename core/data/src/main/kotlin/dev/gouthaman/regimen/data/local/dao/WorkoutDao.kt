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
    fun observeWorkout(id: Long): Flow<WorkoutWithDetailsEntity?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithDetails(id: Long): WorkoutWithDetailsEntity?

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
    fun observeInProgressId(): Flow<Long?>

    /** Most recent completed session of a routine - source for prefill. */
    @Transaction
    @Query(
        "SELECT * FROM workouts WHERE routineId = :routineId " +
                "AND workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun getMostRecentCompletedForRoutine(routineId: Long): WorkoutWithDetailsEntity?

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
    fun observeBestWeight(exerciseId: Long): Flow<Double?>

    /** Heaviest completed set per exercise across all finished workouts. [excludingWorkoutId]
     * (a sentinel of -1 means "none") lets a just-finished workout be compared against the
     * record it holds excluding its own sets, to tell "beat the old PR" from "merely tied it". */
    @Query(
        "SELECT we.exerciseId AS exerciseId, MAX(se.weightKg) AS bestWeightKg FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.workoutStatus IN ('COMPLETE', 'EDITING') AND se.isComplete = 1 " +
                "AND se.weightKg IS NOT NULL AND w.id != :excludingWorkoutId " +
                "GROUP BY we.exerciseId"
    )
    fun observePersonalRecords(excludingWorkoutId: Long = -1): Flow<List<PersonalRecordRowEntity>>

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
    fun observeBestReps(excludingWorkoutId: Long = -1): Flow<List<RepsRecordRowEntity>>

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
    suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntryEntity?

    /** Every finished session that logged this exercise, most recent first - source for Exercise Detail's History section. */
    @Transaction
    @Query(
        "SELECT we.*, w.startTime AS startTime FROM workout_exercises we " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId " +
                "AND w.workoutStatus IN ('COMPLETE', 'EDITING') " +
                "ORDER BY w.startTime DESC"
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySessionEntity>>

    /** True if any workout references this exercise (active or finished); blocks deletion (cascade risk). */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_exercises WHERE exerciseId = :exerciseId)")
    suspend fun isExerciseUsedInAnyWorkout(exerciseId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Delete
    suspend fun deleteWorkout(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(item: WorkoutExerciseEntity): Long

    @Update
    suspend fun updateWorkoutExercise(item: WorkoutExerciseEntity)

    @Delete
    suspend fun deleteWorkoutExercise(item: WorkoutExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: SetEntryEntity): Long

    @Delete
    suspend fun deleteSet(set: SetEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardio(cardio: CardioEntryEntity): Long

    @Delete
    suspend fun deleteCardio(cardio: CardioEntryEntity)
}
