package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.NewWorkoutExercise
import dev.gouthaman.regimen.domain.model.PersonalRecordRow
import dev.gouthaman.regimen.domain.model.RepsRecordRow
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutExerciseWithDetails
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val FINISHED_STATUSES = setOf(WorkoutStatus.COMPLETE, WorkoutStatus.EDITING)

private fun Workout.isFinished(): Boolean = workoutStatus in FINISHED_STATUSES

class FakeWorkoutRepository : WorkoutRepository {

    private val workouts = MutableStateFlow<List<WorkoutWithDetails>>(emptyList())
    private var nextWorkoutId = 1L
    private var nextWorkoutExerciseId = 1L
    private var nextSetId = 1L
    private var nextCardioId = 1L

    override fun observeCompleted(): Flow<List<WorkoutWithDetails>> =
        workouts.map { list -> list.filter { it.workout.isFinished() } }

    override fun observeWorkout(id: Long): Flow<WorkoutWithDetails?> =
        workouts.map { list -> list.find { it.workout.id == id } }

    override fun observeCompletedBetween(start: Long, end: Long): Flow<List<Workout>> =
        workouts.map { list ->
            list.filter {
                it.workout.isFinished() && it.workout.startTime in start..end
            }.map { it.workout }
        }

    override fun observeInProgressId(): Flow<Long?> = workouts.map { list ->
        list.filterNot { it.workout.isFinished() }.maxByOrNull { it.workout.startTime }?.workout?.id
    }

    override fun observeBestWeight(exerciseId: Long): Flow<Double?> = workouts.map { list ->
        list.filter { it.workout.isFinished() }
            .flatMap { it.exercises }
            .filter { it.exercise.id == exerciseId }
            .flatMap { it.sets }
            .filter { it.isComplete }
            .mapNotNull { it.weightKg }
            .maxOrNull()
    }

    override fun observePersonalRecords(excludingWorkoutId: Long?): Flow<List<PersonalRecordRow>> =
        workouts.map { list ->
            list.filter { it.workout.isFinished() && it.workout.id != excludingWorkoutId }
                .flatMap { it.exercises }
                .flatMap { we ->
                    we.sets.filter { it.isComplete && it.weightKg != null }
                        .map { we.exercise.id to it.weightKg!! }
                }
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (exerciseId, weights) ->
                    weights.maxOrNull()?.let { PersonalRecordRow(exerciseId, it) }
                }
        }

    override fun observeBestReps(excludingWorkoutId: Long?): Flow<List<RepsRecordRow>> =
        workouts.map { list ->
            list.filter { it.workout.isFinished() && it.workout.id != excludingWorkoutId }
                .flatMap { it.exercises }
                .flatMap { we ->
                    we.sets.filter { it.isComplete && it.weightKg == null && it.reps != null }
                        .map { we.exercise.id to it.reps!! }
                }
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (exerciseId, reps) ->
                    reps.maxOrNull()?.let { RepsRecordRow(exerciseId, it) }
                }
        }

    override fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseHistorySession>> =
        workouts.map { list ->
            list.filter { it.workout.isFinished() }
                .flatMap { w ->
                    w.exercises.filter { it.exercise.id == exerciseId }.map { w to it }
                }
                .sortedByDescending { (w, _) -> w.workout.startTime }
                .map { (w, we) ->
                    ExerciseHistorySession(
                        workoutExercise = we.workoutExercise,
                        startTime = w.workout.startTime,
                        sets = we.sets,
                        cardio = we.cardio,
                    )
                }
        }

    override suspend fun getInProgress(): WorkoutWithDetails? =
        workouts.value.filterNot { it.workout.isFinished() }.maxByOrNull { it.workout.startTime }

    override suspend fun getWorkout(id: Long): WorkoutWithDetails? =
        workouts.value.find { it.workout.id == id }

    override suspend fun getMostRecentForRoutine(routineId: Long): WorkoutWithDetails? =
        workouts.value
            .filter { it.workout.routineId == routineId && it.workout.isFinished() }
            .maxByOrNull { it.workout.startTime }

    override suspend fun getMostRecentSetForExercise(exerciseId: Long): SetEntry? =
        workouts.value
            .filter { it.workout.isFinished() }
            .sortedByDescending { it.workout.startTime }
            .firstNotNullOfOrNull { w ->
                w.exercises.firstOrNull { it.exercise.id == exerciseId }?.sets?.maxByOrNull { it.setNumber }
            }

    override suspend fun isExerciseUsed(exerciseId: Long): Boolean =
        workouts.value.any { w -> w.exercises.any { it.exercise.id == exerciseId } }

    override suspend fun createWorkout(startTime: Long, routineId: Long?): Long {
        val id = nextWorkoutId++
        workouts.value = workouts.value + WorkoutWithDetails(
            workout = Workout(id = id, startTime = startTime, routineId = routineId),
            exercises = emptyList(),
        )
        return id
    }

    override suspend fun startWorkout(
        startTime: Long,
        routineId: Long?,
        note: String?,
        exercises: List<NewWorkoutExercise>,
    ): Long {
        val workoutId = createWorkout(startTime, routineId)
        if (note != null) updateWorkout(
            Workout(
                id = workoutId,
                startTime = startTime,
                routineId = routineId,
                note = note
            )
        )
        exercises.forEach { ex ->
            val weId = addExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = ex.exerciseId,
                    position = ex.position
                )
            )
            ex.sets.forEach { set ->
                upsertSet(
                    SetEntry(
                        workoutExerciseId = weId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps,
                    )
                )
            }
            ex.cardio?.let { cardio ->
                upsertCardio(
                    CardioEntry(
                        workoutExerciseId = weId,
                        durationSec = cardio.durationSec,
                        distanceMeters = cardio.distanceMeters,
                    )
                )
            }
        }
        return workoutId
    }

    override suspend fun updateWorkout(workout: Workout) {
        workouts.value = workouts.value.map {
            if (it.workout.id == workout.id) it.copy(workout = workout) else it
        }
    }

    override suspend fun deleteWorkout(workout: Workout) {
        workouts.value = workouts.value.filterNot { it.workout.id == workout.id }
    }

    override suspend fun addExercise(item: WorkoutExercise): Long {
        val id = nextWorkoutExerciseId++
        val withId = item.copy(id = id)
        workouts.value = workouts.value.map { w ->
            if (w.workout.id == item.workoutId) {
                w.copy(
                    exercises = w.exercises + WorkoutExerciseWithDetails(
                        workoutExercise = withId,
                        exercise = exerciseLookup(item.exerciseId),
                        sets = emptyList(),
                        cardio = emptyList(),
                    )
                )
            } else {
                w
            }
        }
        return id
    }

    override suspend fun updateExercise(item: WorkoutExercise) {
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.map {
                if (it.workoutExercise.id == item.id) it.copy(
                    workoutExercise = item
                ) else it
            })
        }
    }

    override suspend fun removeExercise(item: WorkoutExercise) {
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.filterNot { it.workoutExercise.id == item.id })
        }
    }

    override suspend fun upsertSet(set: SetEntry): Long {
        val id = if (set.id != 0L) set.id else nextSetId++
        val withId = set.copy(id = id)
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.map { we ->
                if (we.workoutExercise.id == set.workoutExerciseId) {
                    val existingIndex = we.sets.indexOfFirst { it.id == id }
                    val newSets = if (existingIndex >= 0) {
                        we.sets.toMutableList().apply { set(existingIndex, withId) }
                    } else {
                        we.sets + withId
                    }
                    we.copy(sets = newSets)
                } else {
                    we
                }
            })
        }
        return id
    }

    override suspend fun deleteSet(set: SetEntry) {
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.map { we -> we.copy(sets = we.sets.filterNot { it.id == set.id }) })
        }
    }

    override suspend fun upsertCardio(cardio: CardioEntry): Long {
        val id = if (cardio.id != 0L) cardio.id else nextCardioId++
        val withId = cardio.copy(id = id)
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.map { we ->
                if (we.workoutExercise.id == cardio.workoutExerciseId) {
                    val existingIndex = we.cardio.indexOfFirst { it.id == id }
                    val newCardio = if (existingIndex >= 0) {
                        we.cardio.toMutableList().apply { set(existingIndex, withId) }
                    } else {
                        we.cardio + withId
                    }
                    we.copy(cardio = newCardio)
                } else {
                    we
                }
            })
        }
        return id
    }

    override suspend fun deleteCardio(cardio: CardioEntry) {
        workouts.value = workouts.value.map { w ->
            w.copy(exercises = w.exercises.map { we -> we.copy(cardio = we.cardio.filterNot { it.id == cardio.id }) })
        }
    }

    var exerciseLookup: (Long) -> Exercise = { id ->
        Exercise(
            id = id,
            name = "Exercise $id",
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.OTHER,
            equipment = Equipment.OTHER,
        )
    }

    fun seed(vararg seeded: WorkoutWithDetails) {
        workouts.value = seeded.toList()
        nextWorkoutId = (seeded.maxOfOrNull { it.workout.id } ?: 0) + 1
        nextWorkoutExerciseId =
            (seeded.flatMap { it.exercises }.maxOfOrNull { it.workoutExercise.id } ?: 0) + 1
        nextSetId =
            (seeded.flatMap { it.exercises }.flatMap { it.sets }.maxOfOrNull { it.id } ?: 0) + 1
        nextCardioId =
            (seeded.flatMap { it.exercises }.flatMap { it.cardio }.maxOfOrNull { it.id } ?: 0) + 1
    }
}
