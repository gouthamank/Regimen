package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise
import dev.gouthaman.regimen.data.local.entity.WorkoutWithDetails
import dev.gouthaman.regimen.data.repository.RoutineRepository
import dev.gouthaman.regimen.data.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.ExerciseType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Starts a new workout. If [routineId] is given, copies the routine's exercises in and
 * prefills each set from the most recent completed session of that same routine.
 * Returns the new workout id.
 */
class StartWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
) {
    suspend operator fun invoke(routineId: Long?): Long {
        val now = System.currentTimeMillis()
        val workoutId = workoutRepo.createWorkout(now, routineId)
        if (routineId == null) return workoutId

        val routine = routineRepo.getRoutine(routineId) ?: return workoutId
        val prior = workoutRepo.getMostRecentForRoutine(routineId)
        val priorSetsByExercise: Map<Long, List<SetEntry>> = prior?.let { p ->
            p.exercises.associate { it.exercise.id to it.sets.sortedBy { s -> s.setNumber } }
        } ?: emptyMap()

        routine.exercises.sortedBy { it.routineExercise.position }.forEachIndexed { index, item ->
            val weId = workoutRepo.addExercise(
                WorkoutExercise(
                    workoutId = workoutId,
                    exerciseId = item.exercise.id,
                    position = index,
                )
            )
            val priorSets = priorSetsByExercise[item.exercise.id].orEmpty()
            val setCount = maxOf(item.routineExercise.targetSets, 1)
            for (i in 0 until setCount) {
                val ps = priorSets.getOrNull(i)
                workoutRepo.upsertSet(
                    SetEntry(
                        workoutExerciseId = weId,
                        setNumber = i + 1,
                        weightKg = ps?.weightKg,
                        reps = ps?.reps ?: item.routineExercise.targetReps,
                    )
                )
            }
        }
        return workoutId
    }
}

class FinishWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val current = workoutRepo.getWorkout(workoutId) ?: return
        workoutRepo.updateWorkout(current.workout.copy(endTime = System.currentTimeMillis()))
    }
}

/** Discards an in-progress workout entirely. */
class CancelWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val current = workoutRepo.getWorkout(workoutId) ?: return
        workoutRepo.deleteWorkout(current.workout)
    }
}

class ObserveActiveWorkoutIdUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(): Flow<Long?> = workoutRepo.observeInProgressId()
}

class ObserveWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(id: Long): Flow<WorkoutWithDetails?> = workoutRepo.observeWorkout(id)
}

class ObserveHistoryUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(): Flow<List<WorkoutWithDetails>> = workoutRepo.observeCompleted()
}

/** Deletes a past (or in-progress) workout and all its logged data (cascade). */
class DeleteWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val current = workoutRepo.getWorkout(workoutId) ?: return
        workoutRepo.deleteWorkout(current.workout)
    }
}

/**
 * Creates a new routine from a past session's strength exercises (cardio is session-only and
 * excluded). Target sets = the number of sets logged; target reps = the most common logged rep
 * count; rest defaults to [defaultRestSec]. Returns the new routine id, or null if the session has
 * no strength exercises to save.
 */
class SaveWorkoutAsRoutineUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
) {
    suspend operator fun invoke(workoutId: Long, name: String, defaultRestSec: Int): Long? {
        val workout = workoutRepo.getWorkout(workoutId) ?: return null
        val specs = workout.exercises
            .filter { it.exercise.type == ExerciseType.STRENGTH }
            .sortedBy { it.workoutExercise.position }
            .map { we ->
                val loggedReps = we.sets.mapNotNull { it.reps }
                ExerciseSpec(
                    exerciseId = we.exercise.id,
                    targetSets = maxOf(we.sets.size, 1),
                    targetReps = loggedReps.mostCommonOrNull() ?: 8,
                    targetRestSec = defaultRestSec,
                )
            }
        if (specs.isEmpty()) return null
        return routineRepo.saveRoutine(routineId = null, name = name.trim(), specs = specs)
    }
}

/** Most frequently occurring value (ties broken by the larger value), or null if empty. */
private fun List<Int>.mostCommonOrNull(): Int? =
    groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy({ it.value }, { it.key }))
        ?.key
