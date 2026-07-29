package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun observeAll(): Flow<List<Exercise>>
    fun observeByType(type: ExerciseType): Flow<List<Exercise>>
    fun observeById(id: String): Flow<Exercise?>
    suspend fun getById(id: String): Exercise?

    /** Custom exercises are strength-only in v1. */
    suspend fun addCustom(name: String, muscleGroup: MuscleGroup, equipment: Equipment): String

    suspend fun update(exercise: Exercise)
    suspend fun delete(exercise: Exercise)
}
