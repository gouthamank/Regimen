package dev.gouthaman.regimen.data.repository

import dev.gouthaman.regimen.data.local.dao.NewWorkoutExerciseRow
import dev.gouthaman.regimen.data.local.dao.WorkoutDao
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.data.local.entity.toDomain
import dev.gouthaman.regimen.data.local.entity.toEntity
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.NewWorkoutExercise
import dev.gouthaman.regimen.domain.model.PersonalRecordRow
import dev.gouthaman.regimen.domain.model.RepsRecordRow
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {
    override fun observeCompleted(): Flow<List<WorkoutWithDetails>> =
        dao.observeCompletedWithDetails().map { list -> list.map { it.toDomain() } }

    override fun observeWorkout(id: Long): Flow<WorkoutWithDetails?> =
        dao.observeWorkout(id).map { it?.toDomain() }

    override fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>> =
        dao.observeCompletedBetween(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeInProgressId(): Flow<Long?> = dao.observeInProgressId()
    override fun observeBestWeight(exerciseId: Long): Flow<Double?> =
        dao.observeBestWeight(exerciseId)

    override fun observePersonalRecords(excludingWorkoutId: Long?): Flow<List<PersonalRecordRow>> =
        dao.observePersonalRecords(excludingWorkoutId ?: -1)
            .map { list -> list.map { it.toDomain() } }

    override fun observeBestReps(excludingWorkoutId: Long?): Flow<List<RepsRecordRow>> =
        dao.observeBestReps(excludingWorkoutId ?: -1).map { list -> list.map { it.toDomain() } }

    override fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySession>> =
        dao.observeExerciseHistory(exerciseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getInProgress(): WorkoutWithDetails? =
        dao.getInProgressWorkout()?.toDomain()

    override suspend fun getWorkout(id: Long): WorkoutWithDetails? =
        dao.getWorkoutWithDetails(id)?.toDomain()

    override suspend fun getMostRecentForRoutine(routineId: Long): WorkoutWithDetails? =
        dao.getMostRecentCompletedForRoutine(routineId)?.toDomain()

    override suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntry? =
        dao.getMostRecentSetForExercise(exerciseId)?.toDomain()

    override suspend fun isExerciseUsed(exerciseId: Long): Boolean =
        dao.isExerciseUsedInAnyWorkout(exerciseId)

    override suspend fun createWorkout(startTime: Long, routineId: Long?): Long =
        dao.insertWorkout(WorkoutEntity(startTime = startTime, routineId = routineId))

    override suspend fun startWorkout(
        startTime: Long,
        routineId: Long?,
        note: String?,
        exercises: List<NewWorkoutExercise>,
    ): Long = dao.insertWorkoutWithExercises(
        workout = WorkoutEntity(startTime = startTime, routineId = routineId, note = note),
        exercises = exercises.map { ex ->
            NewWorkoutExerciseRow(
                workoutExercise = WorkoutExerciseEntity(
                    workoutId = 0,
                    exerciseId = ex.exerciseId,
                    position = ex.position,
                ),
                sets = ex.sets.map { set ->
                    SetEntryEntity(
                        workoutExerciseId = 0,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps,
                    )
                },
                cardio = ex.cardio?.let {
                    CardioEntryEntity(
                        workoutExerciseId = 0,
                        durationSec = it.durationSec,
                        distanceMeters = it.distanceMeters,
                    )
                },
            )
        },
    )

    override suspend fun updateWorkout(workout: Workout) = dao.updateWorkout(workout.toEntity())
    override suspend fun deleteWorkout(workout: Workout) = dao.deleteWorkout(workout.toEntity())

    override suspend fun addExercise(item: WorkoutExercise): Long =
        dao.insertWorkoutExercise(item.toEntity())

    override suspend fun updateExercise(item: WorkoutExercise) =
        dao.updateWorkoutExercise(item.toEntity())

    override suspend fun removeExercise(item: WorkoutExercise) =
        dao.deleteWorkoutExercise(item.toEntity())

    override suspend fun upsertSet(set: SetEntry): Long = dao.upsertSet(set.toEntity())
    override suspend fun deleteSet(set: SetEntry) = dao.deleteSet(set.toEntity())

    override suspend fun upsertCardio(cardio: CardioEntry): Long =
        dao.upsertCardio(cardio.toEntity())

    override suspend fun deleteCardio(cardio: CardioEntry) = dao.deleteCardio(cardio.toEntity())
}
