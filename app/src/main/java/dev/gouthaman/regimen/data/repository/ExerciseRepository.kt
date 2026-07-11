package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
) {
    fun observeAll(): Flow<List<Exercise>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByType(type: ExerciseType): Flow<List<Exercise>> =
        dao.observeByType(type).map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Exercise?> = dao.observeById(id).map { it?.toDomain() }
    suspend fun getById(id: Long): Exercise? = dao.getById(id)?.toDomain()

    /** Custom exercises are strength-only in v1. */
    suspend fun addCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): Long =
        dao.insert(
            ExerciseEntity(
                name = name.trim(),
                type = ExerciseType.STRENGTH,
                muscleGroup = muscleGroup,
                equipment = equipment,
                isCustom = true,
            )
        )

    suspend fun update(exercise: Exercise) = dao.update(exercise.toEntity())
    suspend fun delete(exercise: Exercise) = dao.delete(exercise.toEntity())
}
