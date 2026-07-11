package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.matchesSearch
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** All exercises, optionally filtered by type/muscle/equipment and a search query. */
class ObserveExercisesUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    operator fun invoke(
        query: String = "",
        type: ExerciseType? = null,
        muscleGroup: MuscleGroup? = null,
        equipment: Equipment? = null,
        customOnly: Boolean = false,
    ): Flow<List<Exercise>> = repo.observeAll().map { list ->
        list.filter { e ->
            e.matchesSearch(query) &&
                    (type == null || e.type == type) &&
                    (muscleGroup == null || e.muscleGroup == muscleGroup) &&
                    (equipment == null || e.equipment == equipment) &&
                    (!customOnly || e.isCustom)
        }
    }
}

class ObserveExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    operator fun invoke(id: Long): Flow<Exercise?> = repo.observeById(id)
}

class AddCustomExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    suspend operator fun invoke(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment
    ): Long =
        repo.addCustom(name, muscleGroup, equipment)
}

/** Edits an existing exercise (custom only in v1). */
class UpdateExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    suspend operator fun invoke(exercise: Exercise) = repo.update(exercise)
}

/** Result of a delete attempt: either it succeeded, or it's still referenced somewhere and was
 * refused (would otherwise cascade-delete those rows). */
sealed interface DeleteExerciseResult {
    data object Deleted : DeleteExerciseResult
    data class InUse(val inRoutines: Boolean, val inWorkouts: Boolean) : DeleteExerciseResult
}

/** Deletes an exercise (custom only in v1), refusing if referenced by any routine or workout
 * (active or historical) — those rows cascade-delete on the Exercise FK, which would otherwise
 * silently erase routine slots and logged history. */
class DeleteExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
) {
    suspend operator fun invoke(exercise: Exercise): DeleteExerciseResult {
        val inRoutines = routineRepo.isExerciseUsed(exercise.id)
        val inWorkouts = workoutRepo.isExerciseUsed(exercise.id)
        if (inRoutines || inWorkouts) {
            return DeleteExerciseResult.InUse(inRoutines = inRoutines, inWorkouts = inWorkouts)
        }
        repo.delete(exercise)
        return DeleteExerciseResult.Deleted
    }
}
