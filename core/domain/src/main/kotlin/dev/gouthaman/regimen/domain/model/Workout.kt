package dev.gouthaman.regimen.domain.model

/**
 * A workout's explicit lifecycle state - the single source of truth for "paused / in rest /
 * running / done", replacing ad hoc inference from [Workout.pausedAt]/[Workout.endTime] nullability.
 */
enum class WorkoutStatus { IN_PROGRESS, IN_REST_TIME, PAUSED, EDITING, COMPLETE }

/** An actual workout performed on a date. Created from a routine, or freeform (routineId null). */
data class Workout(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String? = null,
    val routineId: Long? = null,
    val workoutStatus: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
    // Session pause (S13): pausedAt non-null = currently paused (value = pause start time);
    // accumulatedPausedMs = total paused time, excluded from the session timer/duration.
    val pausedAt: Long? = null,
    val accumulatedPausedMs: Long = 0,
    // Rest countdown - all three non-null only while workoutStatus == IN_REST_TIME.
    val restTimeEndAt: Long? = null,
    val restTotalSec: Int? = null,
    val restWorkoutExerciseId: Long? = null,
)

data class WorkoutExercise(
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val isSkipped: Boolean = false,
    // True once every set is logged and checked complete (or auto-set when the last one is,
    // whether via checkbox or rest-timer completion) - collapses the card until Edit reopens it.
    val isDone: Boolean = false,
    val supersetGroupId: Long? = null,
)

/** One logged set of a strength exercise. Weight stored canonically in kg. */
data class SetEntry(
    val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isComplete: Boolean = false,
)

/** A logged cardio bout. Distance stored canonically in meters. */
data class CardioEntry(
    val id: Long = 0,
    val workoutExerciseId: Long,
    val durationSec: Long,
    val distanceMeters: Double? = null,
)

/** A workout exercise with its definition, logged sets, and cardio entries. */
data class WorkoutExerciseWithDetails(
    val workoutExercise: WorkoutExercise,
    val exercise: Exercise,
    val sets: List<SetEntry>,
    val cardio: List<CardioEntry>,
)

/** A full workout session with all exercises and their logged data. */
data class WorkoutWithDetails(
    val workout: Workout,
    val exercises: List<WorkoutExerciseWithDetails>,
)

/** Total weight lifted across completed sets (same "logged work" semantics as the PR derivation);
 * bodyweight sets contribute no load. */
fun WorkoutWithDetails.loggedVolumeKg(): Double = exercises.sumOf { we ->
    we.sets.filter { it.isComplete }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
}

/** Aggregate result: heaviest weight lifted per exercise (the PR definition). */
data class PersonalRecordRow(
    val exerciseId: Long,
    val bestWeightKg: Double,
)

/** Aggregate result: best reps in a single set for bodyweight exercises - PR definition when there's no [PersonalRecordRow]. */
data class RepsRecordRow(
    val exerciseId: Long,
    val bestReps: Int,
)

/** One finished session's log of a single exercise: its date plus just that exercise's sets/cardio (not the full workout). Source for Exercise Detail's History. */
data class ExerciseHistorySession(
    val workoutExercise: WorkoutExercise,
    val startTime: Long,
    val sets: List<SetEntry>,
    val cardio: List<CardioEntry>,
)
