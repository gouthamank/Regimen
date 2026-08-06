package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gouthaman.regimen.data.local.entity.WorkoutBiometricsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutBiometricsDao {
    @Query("SELECT * FROM workout_biometrics WHERE workoutId = :workoutId")
    suspend fun get(workoutId: String): WorkoutBiometricsEntity?

    @Query("SELECT * FROM workout_biometrics WHERE workoutId = :workoutId")
    fun observe(workoutId: String): Flow<WorkoutBiometricsEntity?>

    @Query("SELECT * FROM workout_biometrics WHERE workoutId IN (:workoutIds)")
    suspend fun getForWorkouts(workoutIds: List<String>): List<WorkoutBiometricsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkoutBiometricsEntity)

    @Query("SELECT * FROM workout_biometrics ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun getMostRecentlyFetched(): WorkoutBiometricsEntity?

    @Query("DELETE FROM workout_biometrics")
    suspend fun deleteAll()
}