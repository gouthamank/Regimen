package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.domain.model.ExerciseType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE type = :type ORDER BY name COLLATE NOCASE ASC")
    fun observeByType(type: ExerciseType): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    /** Sync push job's read/clear side. Built-ins (`isCustom = 0`) are never in sync scope - they
     * ship with the APK, so there's nothing for a push to say about them. */
    @Query(
        "SELECT * FROM exercises WHERE isCustom = 1 AND isDirty = 1 " +
                "ORDER BY lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirty(limit: Int): List<ExerciseEntity>

    @Query("UPDATE exercises SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    /** "Pull cloud data"'s wipe side - same `isCustom = 1` scope as [getDirty], since built-ins
     * were never uploaded and have nothing in the cloud to be replaced by. */
    @Query("DELETE FROM exercises WHERE isCustom = 1")
    suspend fun deleteAllCustom()

    /** "Claim primary"'s force-full-upload side - marks every row [getDirty] would ever push as
     * dirty, regardless of whether it already was, so the very next push re-uploads everything
     * rather than only whatever happened to be flagged dirty from this device's past history. */
    @Query("UPDATE exercises SET isDirty = 1 WHERE isCustom = 1")
    suspend fun markAllCustomDirty()
}
