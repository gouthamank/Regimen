package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.PersonalRecordRow
import dev.gouthaman.regimen.domain.model.RepsRecordRow
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
) {
    fun observeCompleted(): Flow<List<WorkoutWithDetails>> =
        dao.observeCompletedWithDetails().map { list -> list.map { it.toDomain() } }

    fun observeWorkout(id: Long): Flow<WorkoutWithDetails?> =
        dao.observeWorkout(id).map { it?.toDomain() }

    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>> =
        dao.observeCompletedBetween(start, end).map { list -> list.map { it.toDomain() } }

    fun observeInProgressId(): Flow<Long?> = dao.observeInProgressId()
    fun observeBestWeight(exerciseId: Long): Flow<Double?> = dao.observeBestWeight(exerciseId)
    fun observePersonalRecords(): Flow<List<PersonalRecordRow>> =
        dao.observePersonalRecords().map { list -> list.map { it.toDomain() } }

    fun observeBestReps(): Flow<List<RepsRecordRow>> =
        dao.observeBestReps().map { list -> list.map { it.toDomain() } }

    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySession>> =
        dao.observeExerciseHistory(exerciseId).map { list -> list.map { it.toDomain() } }

    suspend fun getInProgress(): WorkoutWithDetails? = dao.getInProgressWorkout()?.toDomain()
    suspend fun getWorkout(id: Long): WorkoutWithDetails? =
        dao.getWorkoutWithDetails(id)?.toDomain()

    suspend fun getMostRecentForRoutine(routineId: Long): WorkoutWithDetails? =
        dao.getMostRecentCompletedForRoutine(routineId)?.toDomain()

    suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntry? =
        dao.getMostRecentSetForExercise(exerciseId)?.toDomain()

    suspend fun isExerciseUsed(exerciseId: Long): Boolean =
        dao.isExerciseUsedInAnyWorkout(exerciseId)

    suspend fun createWorkout(startTime: Long, routineId: Long?): Long =
        dao.insertWorkout(WorkoutEntity(startTime = startTime, routineId = routineId))

    suspend fun updateWorkout(workout: Workout) = dao.updateWorkout(workout.toEntity())
    suspend fun deleteWorkout(workout: Workout) = dao.deleteWorkout(workout.toEntity())

    suspend fun setPausedAt(id: Long, pausedAt: Long?) = dao.setPausedAt(id, pausedAt)
    suspend fun clearPause(id: Long, accumulatedMs: Long) = dao.clearPause(id, accumulatedMs)

    suspend fun addExercise(item: WorkoutExercise): Long =
        dao.insertWorkoutExercise(item.toEntity())

    suspend fun updateExercise(item: WorkoutExercise) = dao.updateWorkoutExercise(item.toEntity())
    suspend fun removeExercise(item: WorkoutExercise) = dao.deleteWorkoutExercise(item.toEntity())

    suspend fun upsertSet(set: SetEntry): Long = dao.upsertSet(set.toEntity())
    suspend fun deleteSet(set: SetEntry) = dao.deleteSet(set.toEntity())

    suspend fun upsertCardio(cardio: CardioEntry): Long = dao.upsertCardio(cardio.toEntity())
    suspend fun deleteCardio(cardio: CardioEntry) = dao.deleteCardio(cardio.toEntity())
}
