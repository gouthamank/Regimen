package dev.gouthaman.regimen.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

/** A movement definition. Built-in exercises ship with the app; users can add custom strength ones. */
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ExerciseType,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val isCustom: Boolean = false,
)

/** A saved workout template. Holds strength exercises only (see [RoutineExercise]). */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetRestSec: Int,
    // Reserved for future superset grouping (v2) — kept nullable so it can be added without migration pain.
    val supersetGroupId: Long? = null,
)

/** An actual workout performed on a date. Created from a routine, or freeform (routineId null). */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val note: String? = null,
    val routineId: Long? = null,
    // Session pause (S13). Non-null pausedAt = currently paused (value = when it began);
    // accumulatedPausedMs = total paused time so far. Excluded from the session timer & duration.
    val pausedAt: Long? = null,
    val accumulatedPausedMs: Long = 0,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val isSkipped: Boolean = false,
    val supersetGroupId: Long? = null,
)

/** One logged set of a strength exercise. Weight stored canonically in kg. */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class SetEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isComplete: Boolean = false,
)

/** A logged cardio bout. Distance stored canonically in meters. */
@Entity(
    tableName = "cardio_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class CardioEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val durationSec: Long,
    val distanceMeters: Double? = null,
)

/** A body-measurement type. "Bodyweight" is built-in; users add custom types. */
@Entity(tableName = "measurement_types")
data class MeasurementType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val isBuiltIn: Boolean = false,
)

@Entity(
    tableName = "body_metrics",
    foreignKeys = [
        ForeignKey(
            entity = MeasurementType::class,
            parentColumns = ["id"],
            childColumns = ["measurementTypeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("measurementTypeId")],
)
data class BodyMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementTypeId: Long,
    val date: Long,
    val value: Double,
)
