package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.PersonalRecordRow
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.Workout
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise
import dev.gouthaman.regimen.data.local.entity.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
) {
    fun observeCompleted(): Flow<List<WorkoutWithDetails>> = dao.observeCompletedWithDetails()
    fun observeWorkout(id: Long): Flow<WorkoutWithDetails?> = dao.observeWorkout(id)
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>> =
        dao.observeCompletedBetween(start, end)

    fun observeInProgressId(): Flow<Long?> = dao.observeInProgressId()
    fun observeBestWeight(exerciseId: Long): Flow<Double?> = dao.observeBestWeight(exerciseId)
    fun observePersonalRecords(): Flow<List<PersonalRecordRow>> = dao.observePersonalRecords()

    suspend fun getInProgress(): WorkoutWithDetails? = dao.getInProgressWorkout()
    suspend fun getWorkout(id: Long): WorkoutWithDetails? = dao.getWorkoutWithDetails(id)
    suspend fun getMostRecentForRoutine(routineId: Long): WorkoutWithDetails? =
        dao.getMostRecentCompletedForRoutine(routineId)

    suspend fun createWorkout(startTime: Long, routineId: Long?): Long =
        dao.insertWorkout(Workout(startTime = startTime, routineId = routineId))

    suspend fun updateWorkout(workout: Workout) = dao.updateWorkout(workout)
    suspend fun deleteWorkout(workout: Workout) = dao.deleteWorkout(workout)

    suspend fun addExercise(item: WorkoutExercise): Long = dao.insertWorkoutExercise(item)
    suspend fun updateExercise(item: WorkoutExercise) = dao.updateWorkoutExercise(item)
    suspend fun removeExercise(item: WorkoutExercise) = dao.deleteWorkoutExercise(item)

    suspend fun upsertSet(set: SetEntry): Long = dao.upsertSet(set)
    suspend fun deleteSet(set: SetEntry) = dao.deleteSet(set)

    suspend fun upsertCardio(cardio: CardioEntry): Long = dao.upsertCardio(cardio)
    suspend fun deleteCardio(cardio: CardioEntry) = dao.deleteCardio(cardio)
}
