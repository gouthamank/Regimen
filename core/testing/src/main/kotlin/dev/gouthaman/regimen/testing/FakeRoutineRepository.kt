package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineExercise
import dev.gouthaman.regimen.domain.model.RoutineExerciseWithExercise
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRoutineRepository : RoutineRepository {

    private val routines = MutableStateFlow<List<RoutineWithExercises>>(emptyList())
    private var nextRoutineId = 1L
    private var nextRoutineExerciseId = 1L

    var exerciseLookup: (String) -> Exercise = { id ->
        Exercise(
            id = id,
            name = "Exercise $id",
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.OTHER,
            equipment = Equipment.OTHER,
        )
    }

    override fun observeAll(): Flow<List<RoutineWithExercises>> = routines

    override fun observeRoutine(id: String): Flow<RoutineWithExercises?> =
        routines.map { list -> list.find { it.routine.id == id } }

    override fun observeCount(): Flow<Int> = routines.map { it.size }

    override suspend fun getRoutine(id: String): RoutineWithExercises? =
        routines.value.find { it.routine.id == id }

    override suspend fun isExerciseUsed(exerciseId: String): Boolean =
        routines.value.any { r -> r.exercises.any { it.exercise.id == exerciseId } }

    override suspend fun saveRoutine(
        routineId: String?,
        name: String,
        specs: List<ExerciseSpec>
    ): String {
        val id = routineId ?: (nextRoutineId++).toString()
        val position = routineId
            ?.let { existingId -> routines.value.find { it.routine.id == existingId }?.routine?.position }
            ?: routines.value.size

        val exercises = specs.mapIndexed { index, spec ->
            RoutineExerciseWithExercise(
                routineExercise = RoutineExercise(
                    id = (nextRoutineExerciseId++).toString(),
                    routineId = id,
                    exerciseId = spec.exerciseId,
                    position = index,
                    targetSets = spec.targetSets,
                    targetReps = spec.targetReps,
                    targetRestSec = spec.targetRestSec,
                ),
                exercise = exerciseLookup(spec.exerciseId),
            )
        }

        val updated = RoutineWithExercises(
            routine = Routine(id = id, name = name, position = position),
            exercises = exercises,
        )

        routines.value =
            if (routineId != null && routines.value.any { it.routine.id == routineId }) {
                routines.value.map { if (it.routine.id == routineId) updated else it }
            } else {
                routines.value + updated
            }

        return id
    }

    override suspend fun delete(routine: Routine) {
        routines.value = routines.value.filterNot { it.routine.id == routine.id }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        val positionById = orderedIds.withIndex().associate { (index, id) -> id to index }
        routines.value = routines.value.map { r ->
            positionById[r.routine.id]?.let { position -> r.copy(routine = r.routine.copy(position = position)) }
                ?: r
        }
    }

    fun seed(vararg seeded: RoutineWithExercises) {
        routines.value = seeded.toList()
        nextRoutineId = (seeded.mapNotNull { it.routine.id.toLongOrNull() }.maxOrNull() ?: 0) + 1
        nextRoutineExerciseId = (seeded.flatMap { it.exercises }
            .mapNotNull { it.routineExercise.id.toLongOrNull() }.maxOrNull() ?: 0) + 1
    }
}
