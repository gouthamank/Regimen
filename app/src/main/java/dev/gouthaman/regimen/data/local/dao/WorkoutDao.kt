package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.ExerciseHistorySession
import dev.gouthaman.regimen.data.local.entity.PersonalRecordRow
import dev.gouthaman.regimen.data.local.entity.RepsRecordRow
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.Workout
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise
import dev.gouthaman.regimen.data.local.entity.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts WHERE endTime IS NOT NULL ORDER BY startTime DESC")
    fun observeCompletedWithDetails(): Flow<List<WorkoutWithDetails>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutWithDetails?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getWorkoutWithDetails(id: Long): WorkoutWithDetails?

    /** The in-progress workout, if any (endTime null). Used to resume after process death. */
    @Transaction
    @Query("SELECT * FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getInProgressWorkout(): WorkoutWithDetails?

    @Query("SELECT id FROM workouts WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeInProgressId(): Flow<Long?>

    /** Most recent completed session of a routine — source for prefill. */
    @Transaction
    @Query(
        "SELECT * FROM workouts WHERE routineId = :routineId AND endTime IS NOT NULL " +
                "ORDER BY startTime DESC LIMIT 1"
    )
    suspend fun getMostRecentCompletedForRoutine(routineId: Long): WorkoutWithDetails?

    @Query("SELECT * FROM workouts WHERE endTime IS NOT NULL AND startTime BETWEEN :start AND :end ORDER BY startTime")
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>>

    /** Heaviest weight ever lifted for an exercise — the PR definition. */
    @Query(
        "SELECT MAX(se.weightKg) FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId AND w.endTime IS NOT NULL AND se.isComplete = 1"
    )
    fun observeBestWeight(exerciseId: Long): Flow<Double?>

    /** Heaviest completed set per exercise across all finished workouts. */
    @Query(
        "SELECT we.exerciseId AS exerciseId, MAX(se.weightKg) AS bestWeightKg FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.endTime IS NOT NULL AND se.isComplete = 1 AND se.weightKg IS NOT NULL " +
                "GROUP BY we.exerciseId"
    )
    fun observePersonalRecords(): Flow<List<PersonalRecordRow>>

    /** Best reps per exercise for sets logged without weight (bodyweight) — PR definition
     * when [SetEntry.weightKg] is never stored. */
    @Query(
        "SELECT we.exerciseId AS exerciseId, MAX(se.reps) AS bestReps FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE w.endTime IS NOT NULL AND se.isComplete = 1 AND se.weightKg IS NULL " +
                "AND se.reps IS NOT NULL " +
                "GROUP BY we.exerciseId"
    )
    fun observeBestReps(): Flow<List<RepsRecordRow>>

    /** Most recent logged set for an exercise, from any finished workout — prefill source
     * when adding it ad hoc, outside a routine's own history-based prefill. */
    @Query(
        "SELECT se.* FROM set_entries se " +
                "JOIN workout_exercises we ON se.workoutExerciseId = we.id " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId AND w.endTime IS NOT NULL " +
                "ORDER BY w.startTime DESC, se.setNumber DESC LIMIT 1"
    )
    suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntry?

    /** Every finished session that logged this exercise, most recent first — source for Exercise Detail's History section. */
    @Transaction
    @Query(
        "SELECT we.*, w.startTime AS startTime FROM workout_exercises we " +
                "JOIN workouts w ON we.workoutId = w.id " +
                "WHERE we.exerciseId = :exerciseId AND w.endTime IS NOT NULL " +
                "ORDER BY w.startTime DESC"
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySession>>

    /** True if any workout references this exercise (active or finished); blocks deletion (cascade risk). */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_exercises WHERE exerciseId = :exerciseId)")
    suspend fun isExerciseUsedInAnyWorkout(exerciseId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Query("UPDATE workouts SET pausedAt = :pausedAt WHERE id = :id")
    suspend fun setPausedAt(id: Long, pausedAt: Long?)

    @Query("UPDATE workouts SET pausedAt = NULL, accumulatedPausedMs = :accumulatedMs WHERE id = :id")
    suspend fun clearPause(id: Long, accumulatedMs: Long)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(item: WorkoutExercise): Long

    @Update
    suspend fun updateWorkoutExercise(item: WorkoutExercise)

    @Delete
    suspend fun deleteWorkoutExercise(item: WorkoutExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSet(set: SetEntry): Long

    @Delete
    suspend fun deleteSet(set: SetEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardio(cardio: CardioEntry): Long

    @Delete
    suspend fun deleteCardio(cardio: CardioEntry)
}
