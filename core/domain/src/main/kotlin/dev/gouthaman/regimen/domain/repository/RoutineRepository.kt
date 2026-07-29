package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    fun observeAll(): Flow<List<RoutineWithExercises>>
    fun observeRoutine(id: String): Flow<RoutineWithExercises?>
    fun observeCount(): Flow<Int>
    suspend fun getRoutine(id: String): RoutineWithExercises?
    suspend fun isExerciseUsed(exerciseId: String): Boolean

    /** Create or update a routine and its exercise list in one shot. Returns the routine id. */
    suspend fun saveRoutine(routineId: String?, name: String, specs: List<ExerciseSpec>): String

    suspend fun delete(routine: Routine)

    /** Persist a new routine ordering; [orderedIds] is the full list top-to-bottom. */
    suspend fun reorder(orderedIds: List<String>)
}
