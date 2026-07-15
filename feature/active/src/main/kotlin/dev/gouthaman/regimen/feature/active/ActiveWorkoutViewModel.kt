package dev.gouthaman.regimen.feature.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.di.ApplicationScope
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.service.RestAlerts
import dev.gouthaman.regimen.domain.usecase.AddExercisesToWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.AddSetUseCase
import dev.gouthaman.regimen.domain.usecase.AdjustRestUseCase
import dev.gouthaman.regimen.domain.usecase.CancelWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteSetUseCase
import dev.gouthaman.regimen.domain.usecase.DoneEditingWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.FinishWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.PauseWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ResumeWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.StartRestUseCase
import dev.gouthaman.regimen.domain.usecase.StopRestUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleDoneExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleSkipExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutNoteUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertCardioUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertSetUseCase
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise in the active session with its logged data, ready to edit. */
data class ActiveExercise(
    val workoutExercise: WorkoutExercise,
    val name: String,
    val isStrength: Boolean,
    val equipment: Equipment,
    val isSkipped: Boolean,
    val isDone: Boolean,
    val sets: List<SetEntry>,
    val cardio: CardioEntry?,
    /** Default rest for this exercise (routine target, or the global default). */
    val restTargetSec: Int,
) {
    val workoutExerciseId: Long get() = workoutExercise.id
}

/** An active rest countdown: [endAtMillis] is when it completes; [totalSec] is the original
 * duration the progress bar is normalized against, fixed even as [endAtMillis] is adjusted. */
data class RestTimerState(
    val endAtMillis: Long,
    val totalSec: Int,
    /** The exercise this rest was started from, so its next set can auto-complete on finish. */
    val workoutExerciseId: Long,
)

data class ActiveWorkoutUiState(
    val workoutId: Long = 0,
    /** Null means it's a freeform/"Quick workout" session, not that it isn't loaded yet ([loaded]
     * distinguishes that) — resolved to display text by the Composable. */
    val routineName: String? = null,
    val startTime: Long = 0,
    val exercises: List<ActiveExercise> = emptyList(),
    val note: String = "",
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
    val restChimeEnabled: Boolean = true,
    val status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
    /** Non-null when the session is paused (millis at which it was paused). */
    val pausedAt: Long? = null,
    val accumulatedPausedMs: Long = 0,
    /** Non-null while [status] is [WorkoutStatus.IN_REST_TIME] — the active rest countdown. */
    val rest: RestTimerState? = null,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
) {
    val isPaused: Boolean get() = status == WorkoutStatus.PAUSED

    /** True once the workout has an end time — finished here or via the notification's End action. */
    val finished: Boolean get() = status == WorkoutStatus.COMPLETE || status == WorkoutStatus.EDITING

    /** True while re-editing a finished session (via Session Detail's "Edit"); no live timer runs
     * in this mode — see [dev.gouthaman.regimen.feature.active.ActiveWorkoutScreen]. */
    val isEditingPastSession: Boolean get() = status == WorkoutStatus.EDITING
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWorkout: ObserveWorkoutUseCase,
    observeRoutines: ObserveRoutinesUseCase,
    observePreferences: ObservePreferencesUseCase,
    observeExercises: ObserveExercisesUseCase,
    private val upsertSet: UpsertSetUseCase,
    private val addSetUseCase: AddSetUseCase,
    private val deleteSetUseCase: DeleteSetUseCase,
    private val toggleSkipUseCase: ToggleSkipExerciseUseCase,
    private val toggleDoneUseCase: ToggleDoneExerciseUseCase,
    private val addExercisesUseCase: AddExercisesToWorkoutUseCase,
    private val upsertCardio: UpsertCardioUseCase,
    private val updateNoteUseCase: UpdateWorkoutNoteUseCase,
    private val finishWorkoutUseCase: FinishWorkoutUseCase,
    private val cancelWorkoutUseCase: CancelWorkoutUseCase,
    private val pauseWorkoutUseCase: PauseWorkoutUseCase,
    private val resumeWorkoutUseCase: ResumeWorkoutUseCase,
    private val startRestUseCase: StartRestUseCase,
    private val adjustRestUseCase: AdjustRestUseCase,
    private val stopRestUseCase: StopRestUseCase,
    private val doneEditingWorkoutUseCase: DoneEditingWorkoutUseCase,
    private val restAlerts: RestAlerts,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val workoutId: Long = savedStateHandle.toRoute<ActiveWorkoutRoute>().workoutId

    private var restWatchJob: Job? = null

    /** All exercises, for the add-exercise picker (all types, including cardio). */
    val allExercises: StateFlow<List<Exercise>> =
        observeExercises().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        observeWorkout(workoutId),
        observeRoutines(),
        observePreferences(),
    ) { workout, routines, prefs ->
        if (workout == null) {
            ActiveWorkoutUiState(workoutId = workoutId, loaded = true, notFound = true)
        } else {
            val routine = workout.workout.routineId
                ?.let { id -> routines.firstOrNull { it.routine.id == id } }
            // Per-exercise rest target from the source routine, else the global default.
            val restByExercise = routine?.exercises
                ?.associate { it.exercise.id to it.routineExercise.targetRestSec }
                .orEmpty()
            ActiveWorkoutUiState(
                workoutId = workoutId,
                routineName = routine?.routine?.name,
                startTime = workout.workout.startTime,
                note = workout.workout.note.orEmpty(),
                weightUnit = prefs.weightUnit,
                distanceUnit = prefs.distanceUnit,
                restChimeEnabled = prefs.restChimeEnabled,
                status = workout.workout.workoutStatus,
                pausedAt = workout.workout.pausedAt,
                accumulatedPausedMs = workout.workout.accumulatedPausedMs,
                rest = if (workout.workout.workoutStatus == WorkoutStatus.IN_REST_TIME) {
                    RestTimerState(
                        endAtMillis = workout.workout.restTimeEndAt ?: 0L,
                        totalSec = workout.workout.restTotalSec ?: 0,
                        workoutExerciseId = workout.workout.restWorkoutExerciseId ?: 0L,
                    )
                } else null,
                exercises = workout.exercises
                    .sortedBy { it.workoutExercise.position }
                    .map { we ->
                        ActiveExercise(
                            workoutExercise = we.workoutExercise,
                            name = we.exercise.name,
                            isStrength = we.exercise.type == ExerciseType.STRENGTH,
                            equipment = we.exercise.equipment,
                            isSkipped = we.workoutExercise.isSkipped,
                            isDone = we.workoutExercise.isDone,
                            sets = we.sets.sortedBy { it.setNumber },
                            cardio = we.cardio.firstOrNull(),
                            restTargetSec = restByExercise[we.exercise.id] ?: prefs.restDefaultSec,
                        )
                    },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    fun updateSet(set: SetEntry) = viewModelScope.launch {
        upsertSet(set)
        if (set.isComplete) autoMarkDoneIfAllComplete(set)
    }

    fun addSet(workoutExerciseId: Long, lastSet: SetEntry?) =
        viewModelScope.launch { addSetUseCase(workoutExerciseId, lastSet) }

    fun deleteSet(set: SetEntry) = viewModelScope.launch { deleteSetUseCase(set) }

    /** On blur of a weight field (item 3): fills every later set in the same exercise whose
     * weight is still empty with the value just entered into [fromSetId]. */
    fun autofillWeightBelow(workoutExerciseId: Long, fromSetId: Long, weightKg: Double) {
        viewModelScope.launch {
            val sets = uiState.value.exercises
                .firstOrNull { it.workoutExerciseId == workoutExerciseId }?.sets ?: return@launch
            val fromSetNumber = sets.firstOrNull { it.id == fromSetId }?.setNumber ?: return@launch
            sets.filter { it.setNumber > fromSetNumber && it.weightKg == null }
                .forEach { upsertSet(it.copy(weightKg = weightKg)) }
        }
    }

    /** Same as [autofillWeightBelow], for reps. */
    fun autofillRepsBelow(workoutExerciseId: Long, fromSetId: Long, reps: Int) {
        viewModelScope.launch {
            val sets = uiState.value.exercises
                .firstOrNull { it.workoutExerciseId == workoutExerciseId }?.sets ?: return@launch
            val fromSetNumber = sets.firstOrNull { it.id == fromSetId }?.setNumber ?: return@launch
            sets.filter { it.setNumber > fromSetNumber && it.reps == null }
                .forEach { upsertSet(it.copy(reps = reps)) }
        }
    }

    fun toggleSkip(exercise: WorkoutExercise) =
        viewModelScope.launch { toggleSkipUseCase(exercise) }

    /** Manual Done/Edit tap. Marking done is gated in the UI (only enabled once every set is
     * complete); un-marking (Edit, reopening for further edits) is always allowed. */
    fun toggleDone(exercise: WorkoutExercise) =
        viewModelScope.launch { toggleDoneUseCase(exercise) }

    fun addExercises(ids: List<Long>) =
        viewModelScope.launch { addExercisesUseCase(workoutId, ids) }

    fun updateCardio(cardio: CardioEntry) = viewModelScope.launch { upsertCardio(cardio) }

    fun updateNote(note: String) = viewModelScope.launch { updateNoteUseCase(workoutId, note) }

    fun pause() = viewModelScope.launch { pauseWorkoutUseCase(workoutId) }

    fun resume() = viewModelScope.launch { resumeWorkoutUseCase(workoutId) }

    // Terminal actions run on the app scope, not viewModelScope — navigating away clears the
    // ViewModel, which would cancel the write mid-flight and leave the workout stuck in-progress.
    // The app scope outlives the screen, so the write always lands.
    fun finish() = appScope.launch { finishWorkoutUseCase(workoutId) }

    fun discard() = appScope.launch { cancelWorkoutUseCase(workoutId) }

    /** Leaves editing mode (Done / Cancel-edit while re-editing a finished session). Runs on the
     * app scope for the same reason as [finish]/[discard] — the screen navigates away immediately. */
    fun doneEditing() = appScope.launch { doneEditingWorkoutUseCase(workoutId) }

    // ── Rest timer (S14/item 7). Persisted on Workout so it survives process death. The sheet is
    // undismissable (see RestTimerSheet) — only Skip rest / pause / finish cancel it. ──

    val rest: StateFlow<RestTimerState?> =
        uiState.map { it.rest }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            uiState.map { it.rest }.distinctUntilChanged().collect { rest ->
                restWatchJob?.cancel()
                restWatchJob = null
                if (rest == null) return@collect
                restWatchJob = launch {
                    val remaining = rest.endAtMillis - System.currentTimeMillis()
                    if (remaining > 0) delay(remaining)
                    restAlerts.fire(
                        workoutId = workoutId,
                        chimeEnabled = uiState.value.restChimeEnabled
                    )
                    completeRestSet(rest.workoutExerciseId)
                    stopRestUseCase(workoutId)
                }
            }
        }
    }

    /** Ticks the exercise's first unchecked set (the just-performed set) when a rest ends. */
    private suspend fun completeRestSet(workoutExerciseId: Long) {
        val target = uiState.value.exercises
            .firstOrNull { it.workoutExerciseId == workoutExerciseId }
            ?.sets?.firstOrNull { !it.isComplete }
            ?: return
        val updated = target.copy(isComplete = true)
        upsertSet(updated)
        autoMarkDoneIfAllComplete(updated)
    }

    /** Unified auto-done trigger: fires whether the last set was completed via the checkbox or
     * via a rest countdown ending/being skipped. Uses [justCompleted] directly rather than
     * re-reading [uiState] for that one row, since the Room round-trip for the write just made
     * may not have landed in [uiState] yet. */
    private suspend fun autoMarkDoneIfAllComplete(justCompleted: SetEntry) {
        val exercise = uiState.value.exercises
            .firstOrNull { it.workoutExerciseId == justCompleted.workoutExerciseId } ?: return
        if (exercise.isDone) return
        val sets = exercise.sets.map { if (it.id == justCompleted.id) justCompleted else it }
        if (sets.isNotEmpty() && sets.all { it.isComplete }) {
            toggleDoneUseCase(exercise.workoutExercise)
        }
    }

    /** Starts (or restarts) a rest countdown for [durationSec] tied to [workoutExerciseId]. */
    fun startRest(workoutExerciseId: Long, durationSec: Int) =
        viewModelScope.launch { startRestUseCase(workoutId, workoutExerciseId, durationSec) }

    /** Adjusts the running rest by [deltaSec] (e.g. +/- 15s), clamped so remaining time can't
     * exceed the original duration (the bar can't overfill) or go negative. */
    fun addRestTime(deltaSec: Int) =
        viewModelScope.launch { adjustRestUseCase(workoutId, deltaSec) }

    /** Stops the rest early (Skip rest): no alert, but still ticks the just-performed set. */
    fun stopRest() {
        val current = uiState.value.rest ?: return
        restWatchJob?.cancel()
        restWatchJob = null
        viewModelScope.launch {
            completeRestSet(current.workoutExerciseId)
            stopRestUseCase(workoutId)
        }
    }
}
