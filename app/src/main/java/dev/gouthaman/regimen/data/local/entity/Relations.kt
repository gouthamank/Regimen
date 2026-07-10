package dev.gouthaman.regimen.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A routine's exercise together with its resolved [Exercise] definition. */
data class RoutineExerciseWithExercise(
    @Embedded val routineExercise: RoutineExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

/** A routine with its ordered exercises resolved. */
data class RoutineWithExercises(
    @Embedded val routine: Routine,
    @Relation(
        entity = RoutineExercise::class,
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val exercises: List<RoutineExerciseWithExercise>,
)

/** A workout exercise with its definition, logged sets, and cardio entries. */
data class WorkoutExerciseWithDetails(
    @Embedded val workoutExercise: WorkoutExercise,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntry>,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val cardio: List<CardioEntry>,
)

/** A full workout session with all exercises and their logged data. */
data class WorkoutWithDetails(
    @Embedded val workout: Workout,
    @Relation(
        entity = WorkoutExercise::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val exercises: List<WorkoutExerciseWithDetails>,
)

/** Aggregate result: heaviest weight lifted per exercise (the PR definition). */
data class PersonalRecordRow(
    val exerciseId: Long,
    val bestWeightKg: Double,
)

/** Aggregate result: most reps in a single set, for exercises logged without a weight
 * (bodyweight) — the PR definition when there's no [PersonalRecordRow]. */
data class RepsRecordRow(
    val exerciseId: Long,
    val bestReps: Int,
)

/** One finished session's log of a specific exercise — the session's date plus just that
 * exercise's own sets/cardio (not the whole workout). Source for Exercise Detail's History. */
data class ExerciseHistorySession(
    @Embedded val workoutExercise: WorkoutExercise,
    val startTime: Long,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntry>,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val cardio: List<CardioEntry>,
)

/** A body metric with its measurement type resolved. */
data class BodyMetricWithType(
    @Embedded val metric: BodyMetric,
    @Relation(parentColumn = "measurementTypeId", entityColumn = "id")
    val type: MeasurementType,
)
