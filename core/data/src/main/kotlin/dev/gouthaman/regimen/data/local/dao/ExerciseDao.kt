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
}
