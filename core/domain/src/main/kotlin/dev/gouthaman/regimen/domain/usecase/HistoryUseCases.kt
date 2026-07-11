package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(): Flow<List<WorkoutWithDetails>> = workoutRepo.observeCompleted()
}

/** Every finished session that logged a specific exercise, most recent first — used by
 * Exercise Detail's History section. */
class ObserveExerciseHistoryUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(exerciseId: Long): Flow<List<ExerciseHistorySession>> =
        workoutRepo.observeExerciseHistory(exerciseId)
}

/**
 * Starts a new workout modelled on [sourceWorkoutId] ("Repeat"): from its routine (prefilled from
 * the most recent session) if it had one, else a freeform clone with the source's logged numbers.
 * Returns the new workout id, or null if gone.
 */
class RepeatWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val startWorkoutUseCase: StartWorkoutUseCase,
) {
    suspend operator fun invoke(sourceWorkoutId: Long): Long? {
        val source = workoutRepo.getWorkout(sourceWorkoutId) ?: return null
        source.workout.routineId?.let { return startWorkoutUseCase(it) }

        val newId = workoutRepo.createWorkout(System.currentTimeMillis(), routineId = null)
        source.exercises.sortedBy { it.workoutExercise.position }.forEachIndexed { index, we ->
            val weId = workoutRepo.addExercise(
                WorkoutExercise(workoutId = newId, exerciseId = we.exercise.id, position = index)
            )
            if (we.exercise.type == ExerciseType.STRENGTH) {
                val sets = we.sets.sortedBy { it.setNumber }
                if (sets.isEmpty()) {
                    workoutRepo.upsertSet(SetEntry(workoutExerciseId = weId, setNumber = 1))
                } else {
                    sets.forEachIndexed { i, s ->
                        workoutRepo.upsertSet(
                            SetEntry(
                                workoutExerciseId = weId,
                                setNumber = i + 1,
                                weightKg = s.weightKg,
                                reps = s.reps,
                            )
                        )
                    }
                }
            } else {
                workoutRepo.upsertCardio(CardioEntry(workoutExerciseId = weId, durationSec = 0))
            }
        }
        return newId
    }
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
 * Creates a routine from a past session's strength exercises (cardio excluded, session-only).
 * Target sets = sets logged; target reps = most common logged rep count; rest = [defaultRestSec].
 * Returns the new routine id, or null if no strength exercises to save.
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
                    targetReps = loggedReps.mostCommonOrNull() ?: 10,
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
