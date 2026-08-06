package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gouthaman.regimen.data.local.entity.WorkoutBiometricsEntity

@Dao
interface WorkoutBiometricsDao {
    @Query("SELECT * FROM workout_biometrics WHERE workoutId = :workoutId")
    suspend fun get(workoutId: String): WorkoutBiometricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkoutBiometricsEntity)

    @Query(
        "SELECT w.id FROM workouts w " +
                "WHERE w.workoutStatus = 'COMPLETE' AND w.startTime >= :sinceStartTime " +
                "AND w.id NOT IN (SELECT workoutId FROM workout_biometrics)"
    )
    suspend fun getCompletedWorkoutIdsMissingBiometrics(sinceStartTime: Long): List<String>
}