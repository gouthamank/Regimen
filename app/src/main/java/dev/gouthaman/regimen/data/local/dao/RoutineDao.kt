package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.Routine
import dev.gouthaman.regimen.data.local.entity.RoutineExercise
import dev.gouthaman.regimen.data.local.entity.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY position ASC")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: Long): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineWithExercises(id: Long): RoutineWithExercises?

    @Query("SELECT COUNT(*) FROM routines")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routines")
    suspend fun maxPosition(): Int

    @Query("SELECT position FROM routines WHERE id = :id")
    suspend fun positionOf(id: Long): Int?

    @Query("UPDATE routines SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    /** Rewrites every routine's position to match the given id order (index = new position). */
    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(items: List<RoutineExercise>)

    /** Replaces a routine's exercise list atomically. */
    @Transaction
    suspend fun replaceRoutineExercises(routineId: Long, items: List<RoutineExercise>) {
        clearRoutineExercises(routineId)
        insertRoutineExercises(items)
    }
}
