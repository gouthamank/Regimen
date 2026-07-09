package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.entity.Routine
import dev.gouthaman.regimen.data.local.entity.RoutineExercise
import dev.gouthaman.regimen.data.local.entity.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao,
) {
    fun observeAll(): Flow<List<RoutineWithExercises>> = dao.observeRoutinesWithExercises()
    fun observeRoutine(id: Long): Flow<RoutineWithExercises?> = dao.observeRoutine(id)
    fun observeCount(): Flow<Int> = dao.observeCount()
    suspend fun getRoutine(id: Long): RoutineWithExercises? = dao.getRoutineWithExercises(id)

    /** Create or update a routine and its exercise list in one shot. Returns the routine id. */
    suspend fun saveRoutine(
        routineId: Long?,
        name: String,
        specs: List<ExerciseSpec>,
    ): Long {
        val id = if (routineId == null) {
            dao.insertRoutine(Routine(name = name.trim(), position = dao.maxPosition() + 1))
        } else {
            val position = dao.positionOf(routineId) ?: (dao.maxPosition() + 1)
            dao.updateRoutine(Routine(id = routineId, name = name.trim(), position = position))
            routineId
        }
        val items = specs.mapIndexed { index, spec ->
            RoutineExercise(
                routineId = id,
                exerciseId = spec.exerciseId,
                position = index,
                targetSets = spec.targetSets,
                targetReps = spec.targetReps,
                targetRestSec = spec.targetRestSec,
            )
        }
        dao.replaceRoutineExercises(id, items)
        return id
    }

    suspend fun delete(routine: Routine) = dao.deleteRoutine(routine)

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    suspend fun reorder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)
}
