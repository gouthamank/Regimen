package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
) : ExerciseRepository {
    override fun observeAll(): Flow<List<Exercise>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByType(type: ExerciseType): Flow<List<Exercise>> =
        dao.observeByType(type).map { list -> list.map { it.toDomain() } }

    override fun observeById(id: String): Flow<Exercise?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): Exercise? = dao.getById(id)?.toDomain()

    /** Custom exercises are strength-only in v1. */
    override suspend fun addCustom(
        name: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment
    ): String {
        val id = UUID.randomUUID().toString()
        dao.insert(
            ExerciseEntity(
                id = id,
                name = name.trim(),
                type = ExerciseType.STRENGTH,
                muscleGroup = muscleGroup,
                equipment = equipment,
                isCustom = true,
            )
        )
        return id
    }

    override suspend fun update(exercise: Exercise) = dao.update(exercise.toEntity())
    override suspend fun delete(exercise: Exercise) = dao.delete(exercise.toEntity())
}
