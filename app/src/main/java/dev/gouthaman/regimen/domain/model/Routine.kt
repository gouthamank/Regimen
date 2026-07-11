package dev.gouthaman.regimen.domain.model

/** A saved workout template. Holds strength exercises only (see [RoutineExercise]). */
data class Routine(
    val id: Long = 0,
    val name: String,
    val position: Int,
)

data class RoutineExercise(
    val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetRestSec: Int,
    val supersetGroupId: Long? = null,
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
