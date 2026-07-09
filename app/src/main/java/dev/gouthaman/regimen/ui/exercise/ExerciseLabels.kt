package dev.gouthaman.regimen.ui.exercise

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** Human-readable labels for the exercise taxonomy enums, shared across the exercise screens. */

fun ExerciseType.label(): String = when (this) {
    ExerciseType.STRENGTH -> "Strength"
    ExerciseType.CARDIO -> "Cardio"
}

fun MuscleGroup.label(): String = when (this) {
    MuscleGroup.CHEST -> "Chest"
    MuscleGroup.BACK -> "Back"
    MuscleGroup.SHOULDERS -> "Shoulders"
    MuscleGroup.ARMS -> "Arms"
    MuscleGroup.LEGS -> "Legs"
    MuscleGroup.CORE -> "Core"
    MuscleGroup.FULL_BODY -> "Full body"
    MuscleGroup.CARDIO -> "Cardio"
    MuscleGroup.OTHER -> "Other"
}

fun Equipment.label(): String = when (this) {
    Equipment.BARBELL -> "Barbell"
    Equipment.DUMBBELL -> "Dumbbell"
    Equipment.MACHINE -> "Machine"
    Equipment.CABLE -> "Cable"
    Equipment.BODYWEIGHT -> "Bodyweight"
    Equipment.KETTLEBELL -> "Kettlebell"
    Equipment.CARDIO_MACHINE -> "Cardio machine"
    Equipment.OTHER -> "Other"
}

/** Muscle groups offered when creating a custom (strength) exercise — excludes the cardio-only bucket. */
val customExerciseMuscleGroups: List<MuscleGroup> =
    MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }

/** Equipment offered when creating a custom (strength) exercise — excludes cardio machines. */
val customExerciseEquipment: List<Equipment> =
    Equipment.entries.filter { it != Equipment.CARDIO_MACHINE }
