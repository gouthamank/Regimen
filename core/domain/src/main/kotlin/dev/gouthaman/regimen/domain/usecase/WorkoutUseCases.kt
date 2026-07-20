package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.ExerciseHistorySession
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.NewCardioEntry
import dev.gouthaman.regimen.domain.model.NewSetEntry
import dev.gouthaman.regimen.domain.model.NewWorkoutExercise
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutEndReason
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.model.WorkoutWithDetails
import dev.gouthaman.regimen.domain.repository.ExerciseRepository
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Starts a new workout. If [routineId] is given, copies the routine's exercises in and
 * prefills each set - and the session note - from the most recent completed session of that
 * same routine. Returns the new workout id.
 */
class StartWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val routineRepo: RoutineRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(routineId: Long?): Long {
        val now = clock.nowMillis()
        if (routineId == null) return workoutRepo.createWorkout(now, routineId)

        val routine = routineRepo.getRoutine(routineId)
            ?: return workoutRepo.createWorkout(now, routineId)
        val prior = workoutRepo.getMostRecentForRoutine(routineId)
        val priorSetsByExercise: Map<Long, List<SetEntry>> = prior?.let { p ->
            p.exercises.associate { it.exercise.id to it.sets.sortedBy { s -> s.setNumber } }
        } ?: emptyMap()

        // Carries forward a personal note (e.g. "advance bench next time") from the same
        // routine's last session, same idea as the per-set prefill below.
        val note = prior?.workout?.note?.takeIf { it.isNotBlank() }

        // Built up-front, then written in a single transaction (startWorkout) - the previous
        // version awaited one DB round trip per exercise/set here, which was the dominant source
        // of tap-to-navigate latency for routine-based workouts.
        val exercises = routine.exercises.sortedBy { it.routineExercise.position }
            .mapIndexed { index, item ->
                val priorSets = priorSetsByExercise[item.exercise.id].orEmpty()
                val setCount = maxOf(item.routineExercise.targetSets, 1)
                NewWorkoutExercise(
                    exerciseId = item.exercise.id,
                    position = index,
                    sets = (0 until setCount).map { i ->
                        val ps = priorSets.getOrNull(i)
                        NewSetEntry(
                            setNumber = i + 1,
                            weightKg = ps?.weightKg,
                            reps = ps?.reps ?: item.routineExercise.targetReps,
                        )
                    },
                )
            }
        return workoutRepo.startWorkout(now, routineId, note, exercises)
    }
}

class FinishWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        workoutId: Long,
        reason: WorkoutEndReason = WorkoutEndReason.MANUAL
    ) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        // Guards against a redundant re-finish (e.g. a double-tap race) and against ever
        // clobbering a session that's being re-edited (EDITING always has endTime already set,
        // so it can't be reached via the in-progress-only Finish flow today regardless - this is
        // defense-in-depth, matching the same status guard every sibling use-case has).
        if (w.workoutStatus == WorkoutStatus.COMPLETE || w.workoutStatus == WorkoutStatus.EDITING) return
        val now = clock.nowMillis()
        // Settle any in-progress pause into the accumulated total so recorded duration is correct.
        val settledPaused =
            w.accumulatedPausedMs + (w.pausedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
        workoutRepo.updateWorkout(
            w.copy(
                endTime = now,
                workoutStatus = WorkoutStatus.COMPLETE,
                endReason = reason,
                pausedAt = null,
                accumulatedPausedMs = settledPaused,
                restTimeEndAt = null,
                restTotalSec = null,
                restWorkoutExerciseId = null,
            )
        )
        pruneUnloggedExercises(workoutId)
    }

    /** Removes never-actually-logged data before a session is filed into history: incomplete
     * sets and still-default cardio bouts (never touched from their [StartWorkoutUseCase]
     * placeholder). An exercise left with nothing logged is marked skipped, same as if the user
     * had explicitly skipped it, rather than showing an empty card in History. */
    private suspend fun pruneUnloggedExercises(workoutId: Long) {
        val details = workoutRepo.getWorkout(workoutId) ?: return
        for (we in details.exercises) {
            we.sets.filterNot { it.isComplete }.forEach { workoutRepo.deleteSet(it) }
            we.cardio.filter { it.durationSec == 0L && it.distanceMeters == null }
                .forEach { workoutRepo.deleteCardio(it) }

            val nothingLogged = we.sets.all { !it.isComplete } &&
                    we.cardio.all { it.durationSec == 0L && it.distanceMeters == null }
            if (nothingLogged && !we.workoutExercise.isSkipped) {
                workoutRepo.updateExercise(we.workoutExercise.copy(isSkipped = true))
            }
        }
    }
}

/** Pauses the session timer (no-op if already paused). Cancels any active rest countdown rather
 * than juggling two simultaneous timers under one mechanism. */
class PauseWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus == WorkoutStatus.PAUSED) return
        workoutRepo.updateWorkout(
            w.copy(
                workoutStatus = WorkoutStatus.PAUSED,
                pausedAt = clock.nowMillis(),
                restTimeEndAt = null,
                restTotalSec = null,
                restWorkoutExerciseId = null,
            )
        )
    }
}

/** Resumes a paused session, banking the elapsed pause into the accumulated total. */
class ResumeWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus != WorkoutStatus.PAUSED) return
        val pausedAt = w.pausedAt ?: return
        val added = (clock.nowMillis() - pausedAt).coerceAtLeast(0)
        workoutRepo.updateWorkout(
            w.copy(
                workoutStatus = WorkoutStatus.IN_PROGRESS,
                pausedAt = null,
                accumulatedPausedMs = w.accumulatedPausedMs + added,
            )
        )
    }
}

/** Starts (or restarts) a rest countdown tied to [workoutExerciseId], persisted so it survives
 * process death. No-op while paused or after the workout is finished. */
class StartRestUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: Long, workoutExerciseId: Long, durationSec: Int) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus == WorkoutStatus.PAUSED || w.workoutStatus == WorkoutStatus.COMPLETE) return
        workoutRepo.updateWorkout(
            w.copy(
                workoutStatus = WorkoutStatus.IN_REST_TIME,
                restTimeEndAt = clock.nowMillis() + durationSec * 1000L,
                restTotalSec = durationSec,
                restWorkoutExerciseId = workoutExerciseId,
            )
        )
    }
}

/** Adjusts the running rest countdown by [deltaSec] (e.g. +/- 15s), clamped so remaining time
 * can't go negative or exceed the original duration. */
class AdjustRestUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: Long, deltaSec: Int) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus != WorkoutStatus.IN_REST_TIME) return
        val endAt = w.restTimeEndAt ?: return
        val totalSec = w.restTotalSec ?: return
        val now = clock.nowMillis()
        workoutRepo.updateWorkout(
            w.copy(restTimeEndAt = (endAt + deltaSec * 1000L).coerceIn(now, now + totalSec * 1000L))
        )
    }
}

/** Cancels the running rest countdown (e.g. "Skip rest"). */
class StopRestUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus != WorkoutStatus.IN_REST_TIME) return
        workoutRepo.updateWorkout(
            w.copy(
                workoutStatus = WorkoutStatus.IN_PROGRESS,
                restTimeEndAt = null,
                restTotalSec = null,
                restWorkoutExerciseId = null,
            )
        )
    }
}

/** Reopens a finished session for editing (see SessionDetailViewModel.edit()). */
class EditWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus != WorkoutStatus.COMPLETE) return
        workoutRepo.updateWorkout(w.copy(workoutStatus = WorkoutStatus.EDITING))
    }
}

/** Leaves editing mode, returning a session to its finished state (endTime is untouched by editing). */
class DoneEditingWorkoutUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(workoutId: Long) {
        val w = workoutRepo.getWorkout(workoutId)?.workout ?: return
        if (w.workoutStatus != WorkoutStatus.EDITING) return
        workoutRepo.updateWorkout(w.copy(workoutStatus = WorkoutStatus.COMPLETE))
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

/** Completed workouts within [start, end] (inclusive), lightweight (no exercises/sets/cardio) -
 * scopes History's calendar + recent-workouts list to just the visible month, instead of loading
 * every workout ever logged with its full details. */
class ObserveWorkoutsInRangeUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    operator fun invoke(start: Long, end: Long): Flow<List<Workout>> =
        workoutRepo.observeCompletedBetween(start, end)
}

/** Every finished session that logged a specific exercise, most recent first - used by
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
    private val clock: Clock,
) {
    suspend operator fun invoke(sourceWorkoutId: Long): Long? {
        val source = workoutRepo.getWorkout(sourceWorkoutId) ?: return null
        source.workout.routineId?.let { return startWorkoutUseCase(it) }

        // Built up-front, then written in a single transaction (startWorkout) - see
        // StartWorkoutUseCase for why the sequential-await version of this loop was slow.
        val exercises = source.exercises.sortedBy { it.workoutExercise.position }
            .mapIndexed { index, we ->
                if (we.exercise.type == ExerciseType.STRENGTH) {
                    val sets = we.sets.sortedBy { it.setNumber }
                    NewWorkoutExercise(
                        exerciseId = we.exercise.id,
                        position = index,
                        sets = if (sets.isEmpty()) {
                            listOf(NewSetEntry(setNumber = 1))
                        } else {
                            sets.mapIndexed { i, s ->
                                NewSetEntry(setNumber = i + 1, weightKg = s.weightKg, reps = s.reps)
                            }
                        },
                    )
                } else {
                    NewWorkoutExercise(
                        exerciseId = we.exercise.id,
                        position = index,
                        cardio = NewCardioEntry(durationSec = 0),
                    )
                }
            }
        return workoutRepo.startWorkout(clock.nowMillis(), routineId = null, note = null, exercises)
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

/** Marks an exercise done/reopened for editing - manually (once eligible) or auto-fired when its
 * last set becomes complete (see ActiveWorkoutViewModel's autoMarkDoneIfAllComplete). */
class ToggleDoneExerciseUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
) {
    suspend operator fun invoke(exercise: WorkoutExercise) {
        workoutRepo.updateExercise(exercise.copy(isDone = !exercise.isDone))
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
                // Prefill from this exercise's own most recent logged set (any past workout) -
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

/** Most frequently occurring value (ties broken by the larger value), or null if empty. */
private fun List<Int>.mostCommonOrNull(): Int? =
    groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy({ it.value }, { it.key }))
        ?.key
