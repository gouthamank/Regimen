package dev.gouthaman.regimen.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.di.ApplicationScope
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.usecase.AddExercisesToWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.AddSetUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteSetUseCase
import dev.gouthaman.regimen.domain.usecase.DoneEditingWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleDoneExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ToggleSkipExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutExerciseNoteUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateWorkoutNoteUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertCardioUseCase
import dev.gouthaman.regimen.domain.usecase.UpsertSetUseCase
import dev.gouthaman.regimen.feature.exercise.WorkoutExerciseRow
import dev.gouthaman.regimen.navigation.EditWorkoutRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditWorkoutUiState(
    /** Null means it's a freeform/"Quick workout" session, not that it isn't loaded yet ([loaded]
     * distinguishes that) - resolved to display text by the Composable. */
    val routineName: String? = null,
    val exercises: List<WorkoutExerciseRow> = emptyList(),
    val note: String = "",
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
)

/** Reopens a finished session for editing (Session Detail's "Edit"). Unlike the live in-progress
 * workout (ActiveWorkoutViewModel, :feature:active), this is a normal NavHost-scoped destination
 * with a real NavBackStackEntry, so [workoutId] comes from [SavedStateHandle] directly - no
 * assisted injection needed. No timer/pause/rest-timer/finish here either: editing historical
 * data never touches that state (see WorkoutUseCases.EditWorkoutUseCase). */
@HiltViewModel
class EditWorkoutViewModel @Inject constructor(
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
    private val updateExerciseNoteUseCase: UpdateWorkoutExerciseNoteUseCase,
    private val doneEditingWorkoutUseCase: DoneEditingWorkoutUseCase,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    val workoutId = savedStateHandle.toRoute<EditWorkoutRoute>().workoutId

    /** All exercises, for the add-exercise picker (all types, including cardio). */
    val allExercises: StateFlow<List<Exercise>> =
        observeExercises().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    // Which exercises' blank note field is toggled open to type into - UI-only, not persisted;
    // a non-blank note is always shown regardless of this set (see WorkoutExerciseRow.notes).
    private val expandedNotes = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<EditWorkoutUiState> = combine(
        observeWorkout(workoutId),
        observeRoutines(),
        observePreferences(),
        expandedNotes,
    ) { workout, routines, prefs, expandedIds ->
        if (workout == null) {
            EditWorkoutUiState(loaded = true, notFound = true)
        } else {
            val routine = workout.workout.routineId
                ?.let { id -> routines.firstOrNull { it.routine.id == id } }
            EditWorkoutUiState(
                routineName = routine?.routine?.name,
                note = workout.workout.note.orEmpty(),
                weightUnit = prefs.weightUnit,
                distanceUnit = prefs.distanceUnit,
                exercises = workout.exercises
                    .sortedBy { it.workoutExercise.position }
                    .map { we ->
                        WorkoutExerciseRow(
                            workoutExercise = we.workoutExercise,
                            name = we.exercise.name,
                            isStrength = we.exercise.type == ExerciseType.STRENGTH,
                            equipment = we.exercise.equipment,
                            isSkipped = we.workoutExercise.isSkipped,
                            isDone = we.workoutExercise.isDone,
                            sets = we.sets.sortedBy { it.setNumber },
                            cardio = we.cardio.firstOrNull(),
                            notes = we.workoutExercise.notes,
                            notesToggledOpen = we.workoutExercise.id in expandedIds,
                        )
                    },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditWorkoutUiState())

    fun updateSet(set: SetEntry) = viewModelScope.launch { upsertSet(set) }

    fun addSet(workoutExerciseId: String, lastSet: SetEntry?) =
        viewModelScope.launch { addSetUseCase(workoutExerciseId, lastSet) }

    fun deleteSet(set: SetEntry) = viewModelScope.launch { deleteSetUseCase(set) }

    /** On blur of a weight field: fills every later set in the same exercise whose weight is
     * still empty with the value just entered into [fromSetId]. */
    fun autofillWeightBelow(workoutExerciseId: String, fromSetId: String, weightKg: Double) {
        viewModelScope.launch {
            val sets = uiState.value.exercises
                .firstOrNull { it.workoutExerciseId == workoutExerciseId }?.sets ?: return@launch
            val fromSetNumber = sets.firstOrNull { it.id == fromSetId }?.setNumber ?: return@launch
            sets.filter { it.setNumber > fromSetNumber && it.weightKg == null }
                .forEach { upsertSet(it.copy(weightKg = weightKg)) }
        }
    }

    /** Same as [autofillWeightBelow], for reps. */
    fun autofillRepsBelow(workoutExerciseId: String, fromSetId: String, reps: Int) {
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

    fun addExercises(ids: List<String>) =
        viewModelScope.launch { addExercisesUseCase(workoutId, ids) }

    fun updateCardio(cardio: CardioEntry) = viewModelScope.launch { upsertCardio(cardio) }

    fun updateNote(note: String) = viewModelScope.launch { updateNoteUseCase(workoutId, note) }

    fun updateExerciseNote(exercise: WorkoutExercise, notes: String) =
        viewModelScope.launch { updateExerciseNoteUseCase(exercise, notes) }

    fun toggleExerciseNotes(workoutExerciseId: String) = expandedNotes.update {
        if (workoutExerciseId in it) it - workoutExerciseId else it + workoutExerciseId
    }

    /** Leaves editing mode (both Done and Cancel-edit call this - editing never touches endTime,
     * so there's nothing different to do between "keep the edits" and "cancel"; Cancel-edit's
     * confirm dialog is purely a friendly heads-up, not an undo). Runs on the app scope, not
     * viewModelScope - the screen navigates away immediately after, which would otherwise cancel
     * the write mid-flight. */
    fun doneEditing() = appScope.launch { doneEditingWorkoutUseCase(workoutId) }
}
