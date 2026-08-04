package dev.gouthaman.regimen.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WorkoutEndReason
import dev.gouthaman.regimen.domain.model.loggedVolumeKg
import dev.gouthaman.regimen.domain.usecase.DeleteWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.EditWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.RepeatWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.domain.util.UnitLabel
import dev.gouthaman.regimen.navigation.SessionDetailRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise within a past session. Sets/cardio kept raw (not pre-formatted) so the Composable
 * can localize each entry's label at render time via SessionFormat.setLabel/cardioLabel. */
data class SessionExercise(
    val workoutExerciseId: String,
    val name: String,
    val isStrength: Boolean,
    val equipment: Equipment,
    val isSkipped: Boolean,
    val sets: List<SetEntry>,
    val cardio: List<CardioEntry>,
    val notes: String? = null,
)

/** A formatted value + its unit label (e.g. displayValue="1,250", unitLabel=UnitLabel.KG), kept
 * structured so the Composable can localize the "value unit" template at render time. */
data class WeightValue(val displayValue: String, val unitLabel: UnitLabel)

/** Null routineName means it was a freeform/"Quick workout" session, not that it isn't loaded yet
 * (SessionDetailUiState.loaded distinguishes that) - resolved to display text by the Composable. */
data class SessionDetailUiState(
    val routineName: String? = null,
    val dateLabel: String = "",
    /** Raw session timing, formatted to a duration string by the Composable (SessionFormat.duration
     * is @Composable, so it can't be resolved here). */
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val accumulatedPausedMs: Long = 0L,
    val note: String? = null,
    /** True if the max-workout-time safety net auto-ended this session (Settings), not a manual Finish. */
    val autoEnded: Boolean = false,
    /** Null when no completed set carried a load (bodyweight-only or cardio-only session) - the
     * Composable hides the volume stat rather than showing a meaningless "0 kg". */
    val volume: WeightValue? = null,
    val exercises: List<SessionExercise> = emptyList(),
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
) {
    /** Save-as-routine only makes sense when the session has at least one strength exercise. */
    val canSaveAsRoutine: Boolean get() = exercises.any { it.isStrength }
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWorkout: ObserveWorkoutUseCase,
    observeRoutines: ObserveRoutinesUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val deleteWorkoutUseCase: DeleteWorkoutUseCase,
    private val saveAsRoutineUseCase: SaveWorkoutAsRoutineUseCase,
    private val getInProgressWorkoutId: GetInProgressWorkoutIdUseCase,
    private val repeatWorkoutUseCase: RepeatWorkoutUseCase,
    private val editWorkoutUseCase: EditWorkoutUseCase,
) : ViewModel() {

    private val workoutId = savedStateHandle.toRoute<SessionDetailRoute>().workoutId
    private var restDefaultSec = 90

    // Repeat starts a brand new *live* workout - same as Home's "Start workout", it expands the
    // persistent ActiveWorkoutSheet rather than pushing a NavHost destination (see
    // dev.gouthaman.regimen.feature.active.ActiveNavigation.kt).
    private val startedWorkouts = Channel<Unit>(Channel.BUFFERED)
    val startedWorkout: Flow<Unit> = startedWorkouts.receiveAsFlow()

    // Edit reopens THIS historical workout for editing, which is a real NavHost push (there's no
    // "in progress" sheet state for editing a past session to expand into).
    private val editWorkouts = Channel<String>(Channel.BUFFERED)
    val editWorkout: Flow<String> = editWorkouts.receiveAsFlow()

    val uiState: StateFlow<SessionDetailUiState> = combine(
        observeWorkout(workoutId),
        observeRoutines(),
        observePreferences(),
    ) { workout, routines, prefs ->
        restDefaultSec = prefs.restDefaultSec
        val weightUnit = prefs.weightUnit
        val distanceUnit = prefs.distanceUnit
        if (workout == null) {
            SessionDetailUiState(loaded = true, notFound = true)
        } else {
            val routineName = workout.workout.routineId
                ?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine.let { it?.name } }
            SessionDetailUiState(
                routineName = routineName,
                dateLabel = SessionFormat.fullDate(workout.workout.startTime),
                startTime = workout.workout.startTime,
                endTime = workout.workout.endTime,
                accumulatedPausedMs = workout.workout.accumulatedPausedMs,
                note = workout.workout.note?.takeIf { it.isNotBlank() },
                autoEnded = workout.workout.endReason == WorkoutEndReason.TIMEOUT,
                volume = workout.exercises
                    .flatMap { it.sets }
                    .takeIf { sets -> sets.any { it.isComplete && it.weightKg != null } }
                    ?.let {
                        WeightValue(
                            displayValue = UnitConverter.formatCompact(
                                UnitConverter.kgToDisplay(workout.loggedVolumeKg(), weightUnit)
                            ),
                            unitLabel = UnitConverter.weightLabel(weightUnit),
                        )
                    },
                weightUnit = weightUnit,
                distanceUnit = distanceUnit,
                exercises = workout.exercises
                    .sortedBy { it.workoutExercise.position }
                    .map { we ->
                        val strength = we.exercise.type == ExerciseType.STRENGTH
                        SessionExercise(
                            workoutExerciseId = we.workoutExercise.id,
                            name = we.exercise.name,
                            isStrength = strength,
                            equipment = we.exercise.equipment,
                            isSkipped = we.workoutExercise.isSkipped,
                            sets = we.sets.sortedBy { it.setNumber },
                            cardio = we.cardio,
                            notes = we.workoutExercise.notes?.takeIf { it.isNotBlank() },
                        )
                    },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun delete() {
        viewModelScope.launch { deleteWorkoutUseCase(workoutId) }
    }

    fun saveAsRoutine(name: String) {
        viewModelScope.launch { saveAsRoutineUseCase(workoutId, name, restDefaultSec) }
    }

    /** Start the same workout again (resumes an in-progress one if there is one - single-active). */
    fun repeat() {
        viewModelScope.launch {
            getInProgressWorkoutId() ?: repeatWorkoutUseCase(workoutId) ?: return@launch
            startedWorkouts.send(Unit)
        }
    }

    /**
     * Reopen this finished session for editing. Not gated on another workout in progress - editing
     * historical data never touches timer/in-progress state (see EditWorkoutViewModel), so it
     * can't conflict with a genuinely live workout.
     */
    fun edit() {
        viewModelScope.launch {
            editWorkoutUseCase(workoutId)
            editWorkouts.send(workoutId)
        }
    }
}
