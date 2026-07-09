package dev.gouthaman.regimen.ui.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.data.local.entity.WorkoutExercise
import dev.gouthaman.regimen.di.ApplicationScope
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.AddExercisesToWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.AddSetUseCase
import dev.gouthaman.regimen.domain.usecase.CancelWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteSetUseCase
import dev.gouthaman.regimen.domain.usecase.FinishWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.PauseWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.RemoveWorkoutExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ResumeWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleSkipExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutNoteUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertCardioUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertSetUseCase
import dev.gouthaman.regimen.service.RestAlerts
import dev.gouthaman.regimen.ui.navigation.ActiveWorkoutRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise in the active session with its logged data, ready to edit. */
data class ActiveExercise(
    val workoutExercise: WorkoutExercise,
    val name: String,
    val isStrength: Boolean,
    val isSkipped: Boolean,
    val sets: List<SetEntry>,
    val cardio: CardioEntry?,
    /** Default rest for this exercise (routine target, or the global default). */
    val restTargetSec: Int,
) {
    val workoutExerciseId: Long get() = workoutExercise.id
}

/** An active rest countdown: [endAtMillis] is when it completes; [totalSec] scales the progress. */
data class RestTimerState(
    val endAtMillis: Long,
    val totalSec: Int,
    /** The exercise this rest was started from, so its next set can auto-complete on finish. */
    val workoutExerciseId: Long,
)

data class ActiveWorkoutUiState(
    val workoutId: Long = 0,
    val title: String = "",
    val startTime: Long = 0,
    val exercises: List<ActiveExercise> = emptyList(),
    val note: String = "",
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
    /** Non-null when the session is paused (millis at which it was paused). */
    val pausedAt: Long? = null,
    val accumulatedPausedMs: Long = 0,
    /** True once the workout has an end time — finished here or via the notification's End action. */
    val finished: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
) {
    val isPaused: Boolean get() = pausedAt != null
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
    private val removeExerciseUseCase: RemoveWorkoutExerciseUseCase,
    private val addExercisesUseCase: AddExercisesToWorkoutUseCase,
    private val upsertCardio: UpsertCardioUseCase,
    private val updateNoteUseCase: UpdateWorkoutNoteUseCase,
    private val finishWorkoutUseCase: FinishWorkoutUseCase,
    private val cancelWorkoutUseCase: CancelWorkoutUseCase,
    private val pauseWorkoutUseCase: PauseWorkoutUseCase,
    private val resumeWorkoutUseCase: ResumeWorkoutUseCase,
    private val restAlerts: RestAlerts,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val workoutId: Long = savedStateHandle.toRoute<ActiveWorkoutRoute>().workoutId

    private val _rest = MutableStateFlow<RestTimerState?>(null)
    val rest: StateFlow<RestTimerState?> = _rest.asStateFlow()
    private var restJob: Job? = null

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
                title = routine?.routine?.name ?: "Quick workout",
                startTime = workout.workout.startTime,
                note = workout.workout.note.orEmpty(),
                weightUnit = prefs.weightUnit,
                distanceUnit = prefs.distanceUnit,
                pausedAt = workout.workout.pausedAt,
                accumulatedPausedMs = workout.workout.accumulatedPausedMs,
                finished = workout.workout.endTime != null,
                exercises = workout.exercises
                    .sortedBy { it.workoutExercise.position }
                    .map { we ->
                        ActiveExercise(
                            workoutExercise = we.workoutExercise,
                            name = we.exercise.name,
                            isStrength = we.exercise.type == ExerciseType.STRENGTH,
                            isSkipped = we.workoutExercise.isSkipped,
                            sets = we.sets.sortedBy { it.setNumber },
                            cardio = we.cardio.firstOrNull(),
                            restTargetSec = restByExercise[we.exercise.id] ?: prefs.restDefaultSec,
                        )
                    },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveWorkoutUiState())

    fun updateSet(set: SetEntry) = viewModelScope.launch { upsertSet(set) }

    fun addSet(workoutExerciseId: Long, lastSet: SetEntry?) =
        viewModelScope.launch { addSetUseCase(workoutExerciseId, lastSet) }

    fun deleteSet(set: SetEntry) = viewModelScope.launch { deleteSetUseCase(set) }

    fun toggleSkip(exercise: WorkoutExercise) =
        viewModelScope.launch { toggleSkipUseCase(exercise) }

    fun removeExercise(exercise: WorkoutExercise) =
        viewModelScope.launch { removeExerciseUseCase(exercise) }

    fun addExercises(ids: List<Long>) =
        viewModelScope.launch { addExercisesUseCase(workoutId, ids) }

    fun updateCardio(cardio: CardioEntry) = viewModelScope.launch { upsertCardio(cardio) }

    fun updateNote(note: String) = viewModelScope.launch { updateNoteUseCase(workoutId, note) }

    fun pause() = viewModelScope.launch { pauseWorkoutUseCase(workoutId) }

    fun resume() = viewModelScope.launch { resumeWorkoutUseCase(workoutId) }

    // Terminal actions run on the app scope, not viewModelScope: navigating away pops this screen
    // and clears the ViewModel, which would cancel the write mid-flight (leaving the workout stuck
    // in-progress). The app scope outlives the screen, so the finish/discard write always lands.
    fun finish() = appScope.launch { finishWorkoutUseCase(workoutId) }

    fun discard() = appScope.launch { cancelWorkoutUseCase(workoutId) }

    // ── Rest timer (S14). Manual start; runs alongside the (never-paused) session timer. ──

    /**
     * Starts (or restarts) a rest countdown for [durationSec] tied to [workoutExerciseId]. When it
     * runs out it fires the alert; ending it (either way — timeout or [stopRest]) auto-completes
     * that exercise's first unchecked set (the set just performed).
     */
    fun startRest(workoutExerciseId: Long, durationSec: Int) {
        restJob?.cancel()
        _rest.value = RestTimerState(
            endAtMillis = System.currentTimeMillis() + durationSec * 1000L,
            totalSec = durationSec,
            workoutExerciseId = workoutExerciseId,
        )
        restJob = viewModelScope.launch {
            while (isActive) {
                val current = _rest.value ?: break
                val remaining = current.endAtMillis - System.currentTimeMillis()
                if (remaining <= 0) {
                    restAlerts.fire()
                    endRest()
                    break
                }
                delay(remaining.coerceAtMost(200L))
            }
        }
    }

    /** Clears the rest and ticks the exercise's first unchecked set (the just-performed set). */
    private fun endRest() {
        val current = _rest.value ?: return
        _rest.value = null
        val target = uiState.value.exercises
            .firstOrNull { it.workoutExerciseId == current.workoutExerciseId }
            ?.sets?.firstOrNull { !it.isComplete }
            ?: return
        viewModelScope.launch { upsertSet(target.copy(isComplete = true)) }
    }

    /** Adjusts the running rest by [deltaSec] (e.g. +/- 15s). */
    fun addRestTime(deltaSec: Int) {
        val current = _rest.value ?: return
        _rest.value = current.copy(
            endAtMillis = (current.endAtMillis + deltaSec * 1000L)
                .coerceAtLeast(System.currentTimeMillis()),
            totalSec = (current.totalSec + deltaSec).coerceAtLeast(1),
        )
    }

    /** Stops the rest early (skip/dismiss): no alert, but still ticks the just-performed set. */
    fun stopRest() {
        restJob?.cancel()
        restJob = null
        endRest()
    }

    override fun onCleared() {
        restJob?.cancel()
    }
}
