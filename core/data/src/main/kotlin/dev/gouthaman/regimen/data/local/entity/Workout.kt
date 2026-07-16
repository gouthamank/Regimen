package dev.gouthaman.regimen.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.PersonalRecordRow
import dev.gouthaman.regimen.domain.model.RepsRecordRow
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutExerciseWithDetails
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails

/** An actual workout performed on a date. Created from a routine, or freeform (routineId null). */
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("routineId")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

fun WorkoutEntity.toDomain(): Workout = Workout(
    id = id,
    startTime = startTime,
    endTime = endTime,
    note = note,
    routineId = routineId,
    workoutStatus = workoutStatus,
    pausedAt = pausedAt,
    accumulatedPausedMs = accumulatedPausedMs,
    restTimeEndAt = restTimeEndAt,
    restTotalSec = restTotalSec,
    restWorkoutExerciseId = restWorkoutExerciseId,
)

fun Workout.toEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    note = note,
    routineId = routineId,
    workoutStatus = workoutStatus,
    pausedAt = pausedAt,
    accumulatedPausedMs = accumulatedPausedMs,
    restTimeEndAt = restTimeEndAt,
    restTotalSec = restTotalSec,
    restWorkoutExerciseId = restWorkoutExerciseId,
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val isSkipped: Boolean = false,
    val isDone: Boolean = false,
    val supersetGroupId: Long? = null,
)

fun WorkoutExerciseEntity.toDomain(): WorkoutExercise = WorkoutExercise(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    position = position,
    isSkipped = isSkipped,
    isDone = isDone,
    supersetGroupId = supersetGroupId,
)

fun WorkoutExercise.toEntity(): WorkoutExerciseEntity = WorkoutExerciseEntity(
    id = id,
    workoutId = workoutId,
    exerciseId = exerciseId,
    position = position,
    isSkipped = isSkipped,
    isDone = isDone,
    supersetGroupId = supersetGroupId,
)

/** One logged set of a strength exercise. Weight stored canonically in kg. */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val setNumber: Int,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val isComplete: Boolean = false,
)

fun SetEntryEntity.toDomain(): SetEntry = SetEntry(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isComplete = isComplete,
)

fun SetEntry.toEntity(): SetEntryEntity = SetEntryEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    setNumber = setNumber,
    weightKg = weightKg,
    reps = reps,
    isComplete = isComplete,
)

/** A logged cardio bout. Distance stored canonically in meters. */
@Entity(
    tableName = "cardio_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workoutExerciseId")],
)
data class CardioEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val durationSec: Long,
    val distanceMeters: Double? = null,
)

fun CardioEntryEntity.toDomain(): CardioEntry = CardioEntry(
    id = id,
    workoutExerciseId = workoutExerciseId,
    durationSec = durationSec,
    distanceMeters = distanceMeters,
)

fun CardioEntry.toEntity(): CardioEntryEntity = CardioEntryEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    durationSec = durationSec,
    distanceMeters = distanceMeters,
)

/** A workout exercise with its definition, logged sets, and cardio entries. */
data class WorkoutExerciseWithDetailsEntity(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val cardio: List<CardioEntryEntity>,
)

fun WorkoutExerciseWithDetailsEntity.toDomain(): WorkoutExerciseWithDetails =
    WorkoutExerciseWithDetails(
        workoutExercise = workoutExercise.toDomain(),
        exercise = exercise.toDomain(),
        sets = sets.map { it.toDomain() },
        cardio = cardio.map { it.toDomain() },
    )

/** A full workout session with all exercises and their logged data. */
data class WorkoutWithDetailsEntity(
    @Embedded val workout: WorkoutEntity,
    @Relation(
        entity = WorkoutExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "workoutId",
    )
    val exercises: List<WorkoutExerciseWithDetailsEntity>,
)

fun WorkoutWithDetailsEntity.toDomain(): WorkoutWithDetails = WorkoutWithDetails(
    workout = workout.toDomain(),
    exercises = exercises.map { it.toDomain() },
)

/** Aggregate result: heaviest weight lifted per exercise (the PR definition). */
data class PersonalRecordRowEntity(
    val exerciseId: Long,
    val bestWeightKg: Double,
)

fun PersonalRecordRowEntity.toDomain(): PersonalRecordRow =
    PersonalRecordRow(exerciseId = exerciseId, bestWeightKg = bestWeightKg)

/** Aggregate result: best reps in a single set for bodyweight exercises - PR definition when there's no [PersonalRecordRowEntity]. */
data class RepsRecordRowEntity(
    val exerciseId: Long,
    val bestReps: Int,
)

fun RepsRecordRowEntity.toDomain(): RepsRecordRow =
    RepsRecordRow(exerciseId = exerciseId, bestReps = bestReps)

/** One finished session's log of a single exercise: its date plus just that exercise's sets/cardio (not the full workout). Source for Exercise Detail's History. */
data class ExerciseHistorySessionEntity(
    @Embedded val workoutExercise: WorkoutExerciseEntity,
    val startTime: Long,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val sets: List<SetEntryEntity>,
    @Relation(parentColumn = "id", entityColumn = "workoutExerciseId")
    val cardio: List<CardioEntryEntity>,
)

fun ExerciseHistorySessionEntity.toDomain(): ExerciseHistorySession = ExerciseHistorySession(
    workoutExercise = workoutExercise.toDomain(),
    startTime = startTime,
    sets = sets.map { it.toDomain() },
    cardio = cardio.map { it.toDomain() },
)
