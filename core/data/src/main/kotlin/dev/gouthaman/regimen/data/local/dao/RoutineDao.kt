package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.RoutineWithExercisesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY position ASC")
    fun observeRoutinesWithExercises(): Flow<List<RoutineWithExercisesEntity>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: String): Flow<RoutineWithExercisesEntity?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutineWithExercises(id: String): RoutineWithExercisesEntity?

    @Query("SELECT COUNT(*) FROM routines")
    fun observeCount(): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) FROM routines")
    suspend fun maxPosition(): Int

    @Query("SELECT position FROM routines WHERE id = :id")
    suspend fun positionOf(id: String): Int?

    /** True if any routine references this exercise; used to block deletion (cascade risk). */
    @Query("SELECT EXISTS(SELECT 1 FROM routine_exercises WHERE exerciseId = :exerciseId)")
    suspend fun isExerciseUsedInAnyRoutine(exerciseId: String): Boolean

    @Query(
        "UPDATE routines SET position = :position, isDirty = 1, lastModifiedAt = :lastModifiedAt " +
                "WHERE id = :id"
    )
    suspend fun updatePosition(id: String, position: Int, lastModifiedAt: Long)

    /** Rewrites every routine's position to match the given id order (index = new position). */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index, now) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    /** Cascade-victim enumeration (and, for `replaceRoutineExercises`'s caller, exerciseId-based
     * id-stability matching) for the sync tombstone write, which lives in `RoutineRepositoryImpl`
     * (see [dev.gouthaman.regimen.data.repository.RoutineRepositoryImpl]) - this DAO only exposes
     * the raw row lookup, since only a DAO can run a typed Room query. */
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId")
    suspend fun routineExercisesFor(routineId: String): List<RoutineExerciseEntity>

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun clearRoutineExercises(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(items: List<RoutineExerciseEntity>)

    /** Replaces a routine's exercise list atomically. */
    @Transaction
    suspend fun replaceRoutineExercises(routineId: String, items: List<RoutineExerciseEntity>) {
        clearRoutineExercises(routineId)
        insertRoutineExercises(items)
    }
}
