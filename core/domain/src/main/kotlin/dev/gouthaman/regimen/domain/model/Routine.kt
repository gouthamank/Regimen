package dev.gouthaman.regimen.domain.model

/** A saved workout template. Holds strength exercises only (see [RoutineExercise]). */
data class Routine(
    val id: String = "",
    val name: String,
    val position: Int,
)

data class RoutineExercise(
    val id: String = "",
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetRestSec: Int,
    val supersetGroupId: String? = null,
)

/** A routine's exercise together with its resolved [Exercise] definition. */
data class RoutineExerciseWithExercise(
    val routineExercise: RoutineExercise,
    val exercise: Exercise,
)

/** A routine with its ordered exercises resolved. */
data class RoutineWithExercises(
    val routine: Routine,
    val exercises: List<RoutineExerciseWithExercise>,
)
