package dev.gouthaman.regimen.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineExercise
import dev.gouthaman.regimen.domain.model.RoutineExerciseWithExercise
import dev.gouthaman.regimen.domain.model.RoutineWithExercises

/** A saved workout template. Holds strength exercises only (see [RoutineExerciseEntity]). */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
    val isDirty: Boolean = true,
    val lastModifiedAt: Long = System.currentTimeMillis(),
)

fun RoutineEntity.toDomain(): Routine = Routine(id = id, name = name, position = position)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(id = id, name = name, position = position)

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetRestSec: Int,
    // Reserved for future superset grouping (v2); nullable now to avoid a migration later.
    val supersetGroupId: String? = null,
    val isDirty: Boolean = true,
    val lastModifiedAt: Long = System.currentTimeMillis(),
)

fun RoutineExerciseEntity.toDomain(): RoutineExercise = RoutineExercise(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    position = position,
    targetSets = targetSets,
    targetReps = targetReps,
    targetRestSec = targetRestSec,
    supersetGroupId = supersetGroupId,
)

/** A routine's exercise together with its resolved [ExerciseEntity] definition. */
data class RoutineExerciseWithExerciseEntity(
    @Embedded val routineExercise: RoutineExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

fun RoutineExerciseWithExerciseEntity.toDomain(): RoutineExerciseWithExercise =
    RoutineExerciseWithExercise(
        routineExercise = routineExercise.toDomain(),
        exercise = exercise.toDomain(),
    )

/** A routine with its ordered exercises resolved. */
data class RoutineWithExercisesEntity(
    @Embedded val routine: RoutineEntity,
    @Relation(
        entity = RoutineExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val exercises: List<RoutineExerciseWithExerciseEntity>,
)

fun RoutineWithExercisesEntity.toDomain(): RoutineWithExercises = RoutineWithExercises(
    routine = routine.toDomain(),
    exercises = exercises.map { it.toDomain() },
)
