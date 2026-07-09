package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.data.repository.ExerciseRepository
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
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
    ): Flow<List<Exercise>> = repo.observeAll().map { list ->
        list.filter { e ->
            (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true)) &&
                (type == null || e.type == type) &&
                (muscleGroup == null || e.muscleGroup == muscleGroup) &&
                (equipment == null || e.equipment == equipment)
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
    suspend operator fun invoke(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Long =
        repo.addCustom(name, muscleGroup, equipment)
}

/** Edits an existing exercise (custom only in v1). */
class UpdateExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    suspend operator fun invoke(exercise: Exercise) = repo.update(exercise)
}

/** Deletes an exercise (custom only in v1). */
class DeleteExerciseUseCase @Inject constructor(
    private val repo: ExerciseRepository,
) {
    suspend operator fun invoke(exercise: Exercise) = repo.delete(exercise)
}
