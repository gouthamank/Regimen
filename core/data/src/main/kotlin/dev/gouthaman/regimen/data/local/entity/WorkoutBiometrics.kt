package dev.gouthaman.regimen.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics

@Entity(
    tableName = "workout_biometrics",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId", unique = true)]
)
data class WorkoutBiometricsEntity(
    @PrimaryKey val id: String,
    val workoutId: String,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val activeCaloriesKcal: Double? = null,
    val sourcePackageName: String? = null,
    val fetchedAt: Long,
    val isDirty: Boolean = true,
    val lastModifiedAt: Long = System.currentTimeMillis(),
)

fun WorkoutBiometricsEntity.toDomain(): WorkoutBiometrics = WorkoutBiometrics(
    id = id,
    workoutId = workoutId,
    avgBpm = avgBpm,
    maxBpm = maxBpm,
    activeCaloriesKcal = activeCaloriesKcal,
    sourcePackageName = sourcePackageName,
    fetchedAt = fetchedAt,
)

fun WorkoutBiometrics.toEntity(): WorkoutBiometricsEntity = WorkoutBiometricsEntity(
    id = id,
    workoutId = workoutId,
    avgBpm = avgBpm,
    maxBpm = maxBpm,
    activeCaloriesKcal = activeCaloriesKcal,
    sourcePackageName = sourcePackageName,
    fetchedAt = fetchedAt,
)