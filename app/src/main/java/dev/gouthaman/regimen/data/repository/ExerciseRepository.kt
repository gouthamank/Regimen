package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()
    fun observeByType(type: ExerciseType): Flow<List<Exercise>> = dao.observeByType(type)
    fun observeById(id: Long): Flow<Exercise?> = dao.observeById(id)
    suspend fun getById(id: Long): Exercise? = dao.getById(id)

    /** Custom exercises are strength-only in v1. */
    suspend fun addCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Long =
        dao.insert(
            Exercise(
                name = name.trim(),
                type = ExerciseType.STRENGTH,
                muscleGroup = muscleGroup,
                equipment = equipment,
                isCustom = true,
            )
        )

    suspend fun update(exercise: Exercise) = dao.update(exercise)
    suspend fun delete(exercise: Exercise) = dao.delete(exercise)
}
