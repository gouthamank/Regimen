package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** Human-readable labels for the exercise taxonomy enums, shared across the exercise screens. */

@Composable
fun ExerciseType.label(): String = when (this) {
    ExerciseType.STRENGTH -> stringResource(R.string.exercise_type_strength)
    ExerciseType.CARDIO -> stringResource(R.string.exercise_type_cardio)
}

@Composable
fun MuscleGroup.label(): String = when (this) {
    MuscleGroup.CHEST -> stringResource(R.string.muscle_group_chest)
    MuscleGroup.BACK -> stringResource(R.string.muscle_group_back)
    MuscleGroup.SHOULDERS -> stringResource(R.string.muscle_group_shoulders)
    MuscleGroup.ARMS -> stringResource(R.string.muscle_group_arms)
    MuscleGroup.LEGS -> stringResource(R.string.muscle_group_legs)
    MuscleGroup.CORE -> stringResource(R.string.muscle_group_core)
    MuscleGroup.FULL_BODY -> stringResource(R.string.muscle_group_full_body)
    MuscleGroup.CARDIO -> stringResource(R.string.muscle_group_cardio)
    MuscleGroup.OTHER -> stringResource(R.string.muscle_group_other)
}

@Composable
fun Equipment.label(): String = when (this) {
    Equipment.BARBELL -> stringResource(R.string.equipment_barbell)
    Equipment.DUMBBELL -> stringResource(R.string.equipment_dumbbell)
    Equipment.MACHINE -> stringResource(R.string.equipment_machine)
    Equipment.CABLE -> stringResource(R.string.equipment_cable)
    Equipment.BODYWEIGHT -> stringResource(R.string.equipment_bodyweight)
    Equipment.KETTLEBELL -> stringResource(R.string.equipment_kettlebell)
    Equipment.CARDIO_MACHINE -> stringResource(R.string.equipment_cardio_machine)
    Equipment.OTHER -> stringResource(R.string.equipment_other)
}

/** Muscle groups offered when creating a custom (strength) exercise — excludes the cardio-only bucket. */
val customExerciseMuscleGroups: List<MuscleGroup> =
    MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }

/** Equipment offered when creating a custom (strength) exercise — excludes cardio machines. */
val customExerciseEquipment: List<Equipment> =
    Equipment.entries.filter { it != Equipment.CARDIO_MACHINE }
