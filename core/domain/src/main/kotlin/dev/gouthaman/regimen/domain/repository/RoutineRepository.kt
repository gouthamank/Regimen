package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun observeAll(): Flow<List<RoutineWithExercises>>
    fun observeRoutine(id: Long): Flow<RoutineWithExercises?>
    fun observeCount(): Flow<Int>
    suspend fun getRoutine(id: Long): RoutineWithExercises?
    suspend fun isExerciseUsed(exerciseId: Long): Boolean

    /** Create or update a routine and its exercise list in one shot. Returns the routine id. */
    suspend fun saveRoutine(routineId: Long?, name: String, specs: List<ExerciseSpec>): Long

    suspend fun delete(routine: Routine)

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    suspend fun reorder(orderedIds: List<Long>)
}
