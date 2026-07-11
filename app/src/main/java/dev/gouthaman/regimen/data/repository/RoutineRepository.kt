package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val dao: RoutineDao,
) {
    fun observeAll(): Flow<List<RoutineWithExercises>> =
        dao.observeRoutinesWithExercises().map { list -> list.map { it.toDomain() } }

    fun observeRoutine(id: Long): Flow<RoutineWithExercises?> =
        dao.observeRoutine(id).map { it?.toDomain() }

    fun observeCount(): Flow<Int> = dao.observeCount()
    suspend fun getRoutine(id: Long): RoutineWithExercises? =
        dao.getRoutineWithExercises(id)?.toDomain()

    suspend fun isExerciseUsed(exerciseId: Long): Boolean =
        dao.isExerciseUsedInAnyRoutine(exerciseId)

    /** Create or update a routine and its exercise list in one shot. Returns the routine id. */
    suspend fun saveRoutine(
        routineId: Long?,
        name: String,
        specs: List<ExerciseSpec>,
    ): Long {
        val id = if (routineId == null) {
            dao.insertRoutine(RoutineEntity(name = name.trim(), position = dao.maxPosition() + 1))
        } else {
            val position = dao.positionOf(routineId) ?: (dao.maxPosition() + 1)
            dao.updateRoutine(
                RoutineEntity(
                    id = routineId,
                    name = name.trim(),
                    position = position
                )
            )
            routineId
        }
        val items = specs.mapIndexed { index, spec ->
            RoutineExerciseEntity(
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

    suspend fun delete(routine: Routine) = dao.deleteRoutine(routine.toEntity())

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    suspend fun reorder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)
}
