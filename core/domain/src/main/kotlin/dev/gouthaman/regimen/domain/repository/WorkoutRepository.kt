package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.NewWorkoutExercise
import dev.gouthaman.regimen.domain.model.PersonalRecordRow
import dev.gouthaman.regimen.domain.model.RepsRecordRow
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeCompleted(): Flow<List<WorkoutWithDetails>>
    fun observeWorkout(id: Long): Flow<WorkoutWithDetails?>
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>>

    fun observeInProgressId(): Flow<Long?>
    fun observeBestWeight(exerciseId: Long): Flow<Double?>
    fun observePersonalRecords(excludingWorkoutId: Long? = null): Flow<List<PersonalRecordRow>>
    fun observeBestReps(excludingWorkoutId: Long? = null): Flow<List<RepsRecordRow>>
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySession>>

    suspend fun getInProgress(): WorkoutWithDetails?
    suspend fun getWorkout(id: Long): WorkoutWithDetails?
    suspend fun getMostRecentForRoutine(routineId: Long): WorkoutWithDetails?

    suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntry?

    suspend fun isExerciseUsed(exerciseId: Long): Boolean

    suspend fun createWorkout(startTime: Long, routineId: Long?): Long

    /** Atomically creates a workout with all its exercises and prefilled sets in one transaction,
     * instead of the caller awaiting a separate DB round trip per exercise/set. */
    suspend fun startWorkout(
        startTime: Long,
        routineId: Long?,
        note: String?,
        exercises: List<NewWorkoutExercise>,
    ): Long

    suspend fun updateWorkout(workout: Workout)
    suspend fun deleteWorkout(workout: Workout)

    suspend fun addExercise(item: WorkoutExercise): Long
    suspend fun updateExercise(item: WorkoutExercise)
    suspend fun removeExercise(item: WorkoutExercise)

    suspend fun upsertSet(set: SetEntry): Long
    suspend fun deleteSet(set: SetEntry)

    suspend fun upsertCardio(cardio: CardioEntry): Long
    suspend fun deleteCardio(cardio: CardioEntry)
}
