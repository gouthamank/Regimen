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
    fun observeWorkout(id: String): Flow<WorkoutWithDetails?>
    fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>>

    fun observeInProgressId(): Flow<String?>
    fun observeBestWeight(exerciseId: String): Flow<Double?>
    fun observePersonalRecords(excludingWorkoutId: String? = null): Flow<List<PersonalRecordRow>>
    fun observeBestReps(excludingWorkoutId: String? = null): Flow<List<RepsRecordRow>>
    fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistorySession>>

    suspend fun getInProgress(): WorkoutWithDetails?
    suspend fun getWorkout(id: String): WorkoutWithDetails?
    suspend fun getMostRecentForRoutine(routineId: String): WorkoutWithDetails?

    suspend fun getMostRecentSetForExercise(exerciseId: String): SetEntry?

    suspend fun isExerciseUsed(exerciseId: String): Boolean

    suspend fun createWorkout(startTime: Long, routineId: String?): String

    /** Atomically creates a workout with all its exercises and prefilled sets in one transaction,
     * instead of the caller awaiting a separate DB round trip per exercise/set. */
    suspend fun startWorkout(
        startTime: Long,
        routineId: String?,
        note: String?,
        exercises: List<NewWorkoutExercise>,
    ): String

    suspend fun updateWorkout(workout: Workout)
    suspend fun deleteWorkout(workout: Workout)

    suspend fun addExercise(item: WorkoutExercise): String
    suspend fun updateExercise(item: WorkoutExercise)
    suspend fun removeExercise(item: WorkoutExercise)

    suspend fun upsertSet(set: SetEntry): String
    suspend fun deleteSet(set: SetEntry)

    suspend fun upsertCardio(cardio: CardioEntry): String
    suspend fun deleteCardio(cardio: CardioEntry)
}
