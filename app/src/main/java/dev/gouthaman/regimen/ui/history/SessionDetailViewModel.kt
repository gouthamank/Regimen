package dev.gouthaman.regimen.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.DeleteWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.RepeatWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.ui.navigation.SessionDetailRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise within a past session, pre-formatted for display. */
data class SessionExercise(
    val workoutExerciseId: Long,
    val name: String,
    val isStrength: Boolean,
    val equipment: Equipment,
    val isSkipped: Boolean,
    val setLabels: List<String>,
    val cardioLabels: List<String>,
)

data class SessionDetailUiState(
    val title: String = "",
    val dateLabel: String = "",
    val durationLabel: String = "",
    val note: String? = null,
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
) : ViewModel() {

    private val workoutId = savedStateHandle.toRoute<SessionDetailRoute>().workoutId
    private var restDefaultSec = 90

    // Workout id to open in Active Workout (from Repeat/Edit).
    private val openActiveWorkout = Channel<Long>(Channel.BUFFERED)
    val openWorkout: Flow<Long> = openActiveWorkout.receiveAsFlow()

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
                title = routineName ?: "Quick workout",
                dateLabel = SessionFormat.fullDate(workout.workout.startTime),
                durationLabel = SessionFormat.duration(
                    workout.workout.startTime,
                    workout.workout.endTime,
                    workout.workout.accumulatedPausedMs,
                ),
                note = workout.workout.note?.takeIf { it.isNotBlank() },
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
                            setLabels = we.sets
                                .sortedBy { it.setNumber }
                                .map { SessionFormat.setLabel(it, weightUnit) },
                            cardioLabels = we.cardio.map {
                                SessionFormat.cardioLabel(it, distanceUnit)
                            },
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

    /** Start the same workout again (resumes an in-progress one if there is one — single-active). */
    fun repeat() {
        viewModelScope.launch {
            val id = getInProgressWorkoutId() ?: repeatWorkoutUseCase(workoutId) ?: return@launch
            openActiveWorkout.send(id)
        }
    }

    /**
     * Reopen this finished session for editing. Not gated on another workout being in progress —
     * editing historical data doesn't touch the timer/in-progress state at all (see
     * ActiveWorkoutViewModel.isEditingPastSession), so it doesn't conflict with a genuinely live
     * workout running elsewhere.
     */
    fun edit() {
        viewModelScope.launch { openActiveWorkout.send(workoutId) }
    }
}
