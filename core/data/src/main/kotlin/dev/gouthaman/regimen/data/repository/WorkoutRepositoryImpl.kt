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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
) : WorkoutRepository {
    override fun observeCompleted(): Flow<List<WorkoutWithDetails>> =
        dao.observeCompletedWithDetails().map { list -> list.map { it.toDomain() } }

    override fun observeWorkout(id: String): Flow<WorkoutWithDetails?> =
        dao.observeWorkout(id).map { it?.toDomain() }

    override fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>> =
        dao.observeCompletedBetween(start, end).map { list -> list.map { it.toDomain() } }

    override fun observeInProgressId(): Flow<String?> = dao.observeInProgressId()
    override fun observeBestWeight(exerciseId: String): Flow<Double?> =
        dao.observeBestWeight(exerciseId)

    override fun observePersonalRecords(excludingWorkoutId: String?): Flow<List<PersonalRecordRow>> =
        dao.observePersonalRecords(excludingWorkoutId ?: "")
            .map { list -> list.map { it.toDomain() } }

    override fun observeBestReps(excludingWorkoutId: String?): Flow<List<RepsRecordRow>> =
        dao.observeBestReps(excludingWorkoutId ?: "").map { list -> list.map { it.toDomain() } }

    override fun observeExerciseHistory(exerciseId: String): Flow<List<ExerciseHistorySession>> =
        dao.observeExerciseHistory(exerciseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getInProgress(): WorkoutWithDetails? =
        dao.getInProgressWorkout()?.toDomain()

    override suspend fun getWorkout(id: String): WorkoutWithDetails? =
        dao.getWorkoutWithDetails(id)?.toDomain()

    override suspend fun getMostRecentForRoutine(routineId: String): WorkoutWithDetails? =
        dao.getMostRecentCompletedForRoutine(routineId)?.toDomain()

    override suspend fun getMostRecentSetForExercise(exerciseId: String): SetEntry? =
        dao.getMostRecentSetForExercise(exerciseId)?.toDomain()

    override suspend fun isExerciseUsed(exerciseId: String): Boolean =
        dao.isExerciseUsedInAnyWorkout(exerciseId)

    override suspend fun createWorkout(startTime: Long, routineId: String?): String {
        val id = UUID.randomUUID().toString()
        dao.insertWorkout(WorkoutEntity(id = id, startTime = startTime, routineId = routineId))
        return id
    }

    override suspend fun startWorkout(
        startTime: Long,
        routineId: String?,
        note: String?,
        exercises: List<NewWorkoutExercise>,
    ): String {
        val workoutId = UUID.randomUUID().toString()
        dao.insertWorkoutWithExercises(
            workout = WorkoutEntity(
                id = workoutId,
                startTime = startTime,
                routineId = routineId,
                note = note,
            ),
            exercises = exercises.map { ex ->
                val workoutExerciseId = UUID.randomUUID().toString()
                NewWorkoutExerciseRow(
                    workoutExercise = WorkoutExerciseEntity(
                        id = workoutExerciseId,
                        workoutId = workoutId,
                        exerciseId = ex.exerciseId,
                        position = ex.position,
                    ),
                    sets = ex.sets.map { set ->
                        SetEntryEntity(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = workoutExerciseId,
                            setNumber = set.setNumber,
                            weightKg = set.weightKg,
                            reps = set.reps,
                        )
                    },
                    cardio = ex.cardio?.let {
                        CardioEntryEntity(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = workoutExerciseId,
                            durationSec = it.durationSec,
                            distanceMeters = it.distanceMeters,
                        )
                    },
                )
            },
        )
        return workoutId
    }

    override suspend fun updateWorkout(workout: Workout) = dao.updateWorkout(workout.toEntity())
    override suspend fun deleteWorkout(workout: Workout) = dao.deleteWorkout(workout.toEntity())

    override suspend fun addExercise(item: WorkoutExercise): String {
        val id = UUID.randomUUID().toString()
        dao.insertWorkoutExercise(item.copy(id = id).toEntity())
        return id
    }

    override suspend fun updateExercise(item: WorkoutExercise) =
        dao.updateWorkoutExercise(item.toEntity())

    override suspend fun removeExercise(item: WorkoutExercise) =
        dao.deleteWorkoutExercise(item.toEntity())

    override suspend fun upsertSet(set: SetEntry): String {
        val id = if (set.id.isNotEmpty()) set.id else UUID.randomUUID().toString()
        dao.upsertSet(set.copy(id = id).toEntity())
        return id
    }

    override suspend fun deleteSet(set: SetEntry) = dao.deleteSet(set.toEntity())

    override suspend fun upsertCardio(cardio: CardioEntry): String {
        val id = if (cardio.id.isNotEmpty()) cardio.id else UUID.randomUUID().toString()
        dao.upsertCardio(cardio.copy(id = id).toEntity())
        return id
    }

    override suspend fun deleteCardio(cardio: CardioEntry) = dao.deleteCardio(cardio.toEntity())
}
