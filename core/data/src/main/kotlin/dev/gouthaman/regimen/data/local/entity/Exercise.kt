package dev.gouthaman.regimen.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** A movement definition. Built-in exercises ship with the app; users can add custom strength ones. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
)

fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        id = id,
        name = name,
        type = type,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isCustom = isCustom
    )

fun Exercise.toEntity(): ExerciseEntity =
    ExerciseEntity(
        id = id,
        name = name,
        type = type,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isCustom = isCustom
    )
