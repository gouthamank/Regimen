package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.repository.RoutineRepository
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveRoutinesUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    operator fun invoke(): Flow<List<RoutineWithExercises>> = repo.observeAll()
}

class ObserveRoutineUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    operator fun invoke(id: Long): Flow<RoutineWithExercises?> = repo.observeRoutine(id)
}

/** Whether the user has any routines — drives the cold-start funnel on Home. */
class HasRoutinesUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    operator fun invoke(): Flow<Boolean> = repo.observeCount().map { it > 0 }
}

class SaveRoutineUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    suspend operator fun invoke(routineId: Long?, name: String, specs: List<ExerciseSpec>): Long =
        repo.saveRoutine(routineId, name, specs)
}

class DeleteRoutineUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    suspend operator fun invoke(routine: Routine) = repo.delete(routine)
}

/** Persist a user-defined ordering of the routine list ([orderedIds] top-to-bottom). */
class ReorderRoutinesUseCase @Inject constructor(
    private val repo: RoutineRepository,
) {
    suspend operator fun invoke(orderedIds: List<Long>) = repo.reorder(orderedIds)
}
