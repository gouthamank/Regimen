package dev.gouthaman.regimen.data.repository

import androidx.room.withTransaction
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.dao.RoutineDao
import dev.gouthaman.regimen.data.local.dao.SyncTombstoneDao
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.data.local.entity.SyncTombstoneEntity
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
    private val tombstoneDao: SyncTombstoneDao,
    private val db: RegimenDatabase,
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
        // A routine can never contain the same exercise twice (enforced in the editor's UI state),
        // so exerciseId is a safe join key between the old rows and the new specs - unlike a
        // RoutineExercise's own id, which the editor never tracks or threads back through
        // `ExerciseSpec`. Matching by exerciseId (rather than always minting a fresh id) is what
        // lets an edit that keeps an exercise actually keep its row - and its Firestore document -
        // instead of tombstoning and recreating every exercise in the routine on every save.
        db.withTransaction {
            val existingByExerciseId = dao.routineExercisesFor(id).associateBy { it.exerciseId }
            val items = specs.mapIndexed { index, spec ->
                RoutineExerciseEntity(
                    id = existingByExerciseId[spec.exerciseId]?.id ?: UUID.randomUUID().toString(),
                    routineId = id,
                    exerciseId = spec.exerciseId,
                    position = index,
                    targetSets = spec.targetSets,
                    targetReps = spec.targetReps,
                    targetRestSec = spec.targetRestSec,
                )
            }
            val newExerciseIds = specs.map { it.exerciseId }.toSet()
            val removed = existingByExerciseId.values.filterNot { it.exerciseId in newExerciseIds }
            if (removed.isNotEmpty()) {
                tombstoneDao.insertAll(
                    removed.map {
                        SyncTombstoneEntity(
                            entityType = SyncEntityType.ROUTINE_EXERCISE,
                            entityId = it.id,
                            parentId = id,
                        )
                    }
                )
            }
            dao.replaceRoutineExercises(id, items)
        }
        return id
    }

    /** Deleting a routine cascades to its `RoutineExercise` rows at the SQLite level, invisible to
     * this call - so those cascade victims are enumerated and tombstoned here too, not just the
     * routine itself. */
    override suspend fun delete(routine: Routine) = db.withTransaction {
        val exercises = dao.routineExercisesFor(routine.id)
        tombstoneDao.insertAll(
            listOf(
                SyncTombstoneEntity(
                    entityType = SyncEntityType.ROUTINE,
                    entityId = routine.id
                )
            ) +
                    exercises.map {
                        SyncTombstoneEntity(
                            entityType = SyncEntityType.ROUTINE_EXERCISE,
                            entityId = it.id,
                            parentId = routine.id,
                        )
                    }
        )
        dao.deleteRoutine(routine.toEntity())
    }

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    override suspend fun reorder(orderedIds: List<String>) = dao.applyOrder(orderedIds)
}
