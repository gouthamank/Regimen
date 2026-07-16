package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExerciseRepository : ExerciseRepository {

    private val exercises = MutableStateFlow<List<Exercise>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Exercise>> = exercises

    override fun observeByType(type: ExerciseType): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { it.type == type } }

    override fun observeById(id: Long): Flow<Exercise?> =
        exercises.map { list -> list.find { it.id == id } }

    override suspend fun getById(id: Long): Exercise? = exercises.value.find { it.id == id }

    override suspend fun addCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment
    ): Long {
        val id = nextId++
        exercises.value = exercises.value + Exercise(
            id = id,
            name = name,
            type = ExerciseType.STRENGTH,
            muscleGroup = muscleGroup,
            equipment = equipment,
            isCustom = true,
        )
        return id
    }

    override suspend fun update(exercise: Exercise) {
        exercises.value = exercises.value.map { if (it.id == exercise.id) exercise else it }
    }

    override suspend fun delete(exercise: Exercise) {
        exercises.value = exercises.value.filterNot { it.id == exercise.id }
    }

    fun seed(vararg seeded: Exercise) {
        exercises.value = seeded.toList()
        nextId = (seeded.maxOfOrNull { it.id } ?: 0) + 1
    }
}
