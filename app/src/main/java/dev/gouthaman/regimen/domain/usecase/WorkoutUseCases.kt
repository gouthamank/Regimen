package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise
import dev.gouthaman.regimen.data.local.entity.WorkoutWithDetails
import dev.gouthaman.regimen.data.repository.ExerciseRepository
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
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        val now = System.currentTimeMillis()
        // Settle any in-progress pause into the accumulated total so recorded duration is correct.
        val settledPaused =
            w.accumulatedPausedMs + (w.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
        workoutRepo.updateWorkout(
            w.copy(endTime = now, pausedAt = null, accumulatedPausedMs = settledPaused)
        )
    }
}

/** Pauses the session timer (no-op if already paused). */
class PauseWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.pausedAt == null) workoutRepo.setPausedAt(workoutId, System.currentTimeMillis())
    }
}

/** Resumes a paused session, banking the elapsed pause into the accumulated total. */
class ResumeWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        val pausedAt = w.pausedAt ?: return
        val added = (System.currentTimeMillis() - pausedAt).coerceAtLeast(0)
        workoutRepo.clearPause(workoutId, w.accumulatedPausedMs + added)
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

/** One-shot lookup of the current in-progress workout id, if any (for resume / single-active). */
class GetInProgressWorkoutIdUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(): Long? = workoutRepo.getInProgress()?.workout?.id
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

/**
 * Starts a new workout modelled on [sourceWorkoutId] ("Repeat"): from its routine (exercises
 * prefilled from the most recent session) if it had one, else a freeform clone of its exercises
 * with the source's logged numbers prefilled. Returns the new workout id, or null if gone.
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

/** Reopens a finished session for editing ("Edit") by clearing its end time (back to in-progress). */
class ReopenWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.endTime != null) workoutRepo.updateWorkout(w.copy(endTime = null))
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

// ── Active-workout editing (S13). Each write persists immediately so the session survives
// process death / rotation without an explicit save step. ──────────────────────────────

/** Inserts or updates a single logged set (weight/reps/complete/rpe). */
class UpsertSetUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(set: SetEntry) {
        workoutRepo.upsertSet(set)
    }
}

/** Appends a new set to an exercise, seeded from [lastSet]'s weight/reps as a starting point. */
class AddSetUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutExerciseId: Long, lastSet: SetEntry?) {
        workoutRepo.upsertSet(
            SetEntry(
                workoutExerciseId = workoutExerciseId,
                setNumber = (lastSet?.setNumber ?: 0) + 1,
                weightKg = lastSet?.weightKg,
                reps = lastSet?.reps,
            )
        )
    }
}

class DeleteSetUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(set: SetEntry) {
        workoutRepo.deleteSet(set)
    }
}

/** Marks an exercise skipped/un-skipped mid-workout (skipped is saved to history, not removed). */
class ToggleSkipExerciseUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(exercise: WorkoutExercise) {
        workoutRepo.updateExercise(exercise.copy(isSkipped = !exercise.isSkipped))
    }
}

class RemoveWorkoutExerciseUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(exercise: WorkoutExercise) {
        workoutRepo.removeExercise(exercise)
    }
}

/**
 * Appends the chosen exercises to an in-progress workout (after existing ones). Strength gets one
 * blank set to log into; cardio gets one blank bout. Cardio is session-only (never in routines).
 */
class AddExercisesToWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val exerciseRepo: ExerciseRepository,
) {
    suspend operator fun invoke(workoutId: Long, exerciseIds: List<Long>) {
        val existing = workoutRepo.getWorkout(workoutId) ?: return
        var position = existing.exercises.size
        for (exId in exerciseIds) {
            val exercise = exerciseRepo.getById(exId) ?: continue
            val weId = workoutRepo.addExercise(
                WorkoutExercise(workoutId = workoutId, exerciseId = exId, position = position)
            )
            if (exercise.type == ExerciseType.STRENGTH) {
                workoutRepo.upsertSet(SetEntry(workoutExerciseId = weId, setNumber = 1))
            } else {
                workoutRepo.upsertCardio(CardioEntry(workoutExerciseId = weId, durationSec = 0))
            }
            position++
        }
    }
}

/** Inserts or updates a cardio bout (duration + optional distance). */
class UpsertCardioUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(cardio: CardioEntry) {
        workoutRepo.upsertCardio(cardio)
    }
}

/** Updates the session note. */
class UpdateWorkoutNoteUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long, note: String?) {
        val current = workoutRepo.getWorkout(workoutId) ?: return
        workoutRepo.updateWorkout(current.workout.copy(note = note?.ifBlank { null }))
    }
}

/** Most frequently occurring value (ties broken by the larger value), or null if empty. */
private fun List<Int>.mostCommonOrNull(): Int? =
    groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy({ it.value }, { it.key }))
        ?.key
