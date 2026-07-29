package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val dao: RoutineDao,
) : RoutineRepository {
    override fun observeAll(): Flow<List<RoutineWithExercises>> =
        dao.observeRoutinesWithExercises().map { list -> list.map { it.toDomain() } }

    override fun observeRoutine(id: String): Flow<RoutineWithExercises?> =
        dao.observeRoutine(id).map { it?.toDomain() }

    override fun observeCount(): Flow<Int> = dao.observeCount()
    override suspend fun getRoutine(id: String): RoutineWithExercises? =
        dao.getRoutineWithExercises(id)?.toDomain()

    override suspend fun isExerciseUsed(exerciseId: String): Boolean =
        dao.isExerciseUsedInAnyRoutine(exerciseId)

    /** Create or update a routine and its exercise list in one shot. Returns the routine id. */
    override suspend fun saveRoutine(
        routineId: String?,
        name: String,
        specs: List<ExerciseSpec>,
    ): String {
        val id = if (routineId == null) {
            val newId = UUID.randomUUID().toString()
            dao.insertRoutine(
                RoutineEntity(id = newId, name = name.trim(), position = dao.maxPosition() + 1)
            )
            newId
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
                id = UUID.randomUUID().toString(),
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

    override suspend fun delete(routine: Routine) = dao.deleteRoutine(routine.toEntity())

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    override suspend fun reorder(orderedIds: List<String>) = dao.applyOrder(orderedIds)
}
