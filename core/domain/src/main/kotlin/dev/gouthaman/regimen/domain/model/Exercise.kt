package dev.gouthaman.regimen.domain.model

/** A movement definition. Built-in exercises ship with the app; users can add custom strength ones. */
data class Exercise(
    val id: Long = 0,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
)
