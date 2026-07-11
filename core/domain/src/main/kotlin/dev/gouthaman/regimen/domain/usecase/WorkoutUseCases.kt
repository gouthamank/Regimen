package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
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
            w.copy(
                endTime = now,
                pausedAt = null,
                accumulatedPausedMs = settledPaused,
            )
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

// Active-workout editing (S13): each write persists immediately so the session survives process
// death/rotation without an explicit save step.

/** Inserts or updates a single logged set (weight/reps/complete). */
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
                // Prefill from this exercise's own most recent logged set (any past workout) —
                // same idea as StartWorkoutUseCase's prefill, keyed by exercise instead of slot.
                val lastSet = workoutRepo.getMostRecentSetForExercise(exId)
                workoutRepo.upsertSet(
                    SetEntry(
                        workoutExerciseId = weId,
                        setNumber = 1,
                        weightKg = lastSet?.weightKg,
                        reps = lastSet?.reps,
                    )
                )
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
