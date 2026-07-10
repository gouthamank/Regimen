package dev.gouthaman.regimen.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutineUseCase
import dev.gouthaman.regimen.domain.usecase.SaveRoutineUseCase
import dev.gouthaman.regimen.ui.exercise.label
import dev.gouthaman.regimen.ui.navigation.RoutineEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One exercise line being edited within a routine. */
data class EditorExercise(
    val exerciseId: Long,
    val name: String,
    val subtitle: String,
    val targetSets: Int,
    val targetReps: Int,
    val targetRestSec: Int,
)

data class RoutineEditorUiState(
    val routineId: Long = 0L,
    val isEditing: Boolean = false,
    val name: String = "",
    val exercises: List<EditorExercise> = emptyList(),
    val availableStrength: List<Exercise> = emptyList(),
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()

    /** Exercise ids already in the routine — what the picker should show pre-checked. */
    val usedIds: Set<Long> get() = exercises.map { it.exerciseId }.toSet()
}

private const val SETS_MIN = 1
private const val SETS_MAX = 15
private const val REPS_MIN = 1
private const val REPS_MAX = 50
private const val REST_MIN = 0
private const val REST_MAX = 600
private const val REST_STEP = 15

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExercises: ObserveExercisesUseCase,
    observeRoutine: ObserveRoutineUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val saveRoutine: SaveRoutineUseCase,
) : ViewModel() {

    private val routineId = savedStateHandle.toRoute<RoutineEditorRoute>().routineId

    private val _uiState =
        MutableStateFlow(RoutineEditorUiState(routineId = routineId, isEditing = routineId != 0L))
    val uiState: StateFlow<RoutineEditorUiState> = _uiState.asStateFlow()

    /** Default rest (seconds) for newly added exercises, seeded from user preferences. */
    private var defaultRestSec = 90

    init {
        viewModelScope.launch {
            defaultRestSec = observePreferences().first().restDefaultSec
        }
        viewModelScope.launch {
            observeExercises(type = ExerciseType.STRENGTH).collect { list ->
                _uiState.update { it.copy(availableStrength = list) }
            }
        }
        if (routineId != 0L) {
            viewModelScope.launch {
                observeRoutine(routineId).first()?.let { routine ->
                    _uiState.update {
                        it.copy(
                            name = routine.routine.name,
                            exercises = routine.exercises
                                .sortedBy { re -> re.routineExercise.position }
                                .map { re ->
                                    EditorExercise(
                                        exerciseId = re.exercise.id,
                                        name = re.exercise.name,
                                        subtitle = "${re.exercise.muscleGroup.label()} · ${re.exercise.equipment.label()}",
                                        targetSets = re.routineExercise.targetSets,
                                        targetReps = re.routineExercise.targetReps,
                                        targetRestSec = re.routineExercise.targetRestSec,
                                    )
                                },
                        )
                    }
                }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }

    /** Reconciles the picker's checked ids against the current routine: keeps customized entries
     * for ids still checked, drops unchecked ones, and appends newly checked ids. */
    fun setExercises(ids: List<Long>) = _uiState.update { state ->
        val idSet = ids.toSet()
        val kept = state.exercises.filter { it.exerciseId in idSet }
        val keptIds = kept.map { it.exerciseId }.toSet()
        val byId = state.availableStrength.associateBy { it.id }
        val added = ids.filter { it !in keptIds }
            .mapNotNull { byId[it] }
            .map { ex ->
                EditorExercise(
                    exerciseId = ex.id,
                    name = ex.name,
                    subtitle = "${ex.muscleGroup.label()} · ${ex.equipment.label()}",
                    targetSets = 3,
                    targetReps = 10,
                    targetRestSec = defaultRestSec,
                )
            }
        state.copy(exercises = kept + added)
    }

    fun removeAt(index: Int) = _uiState.update { state ->
        state.copy(exercises = state.exercises.toMutableList().also { it.removeAt(index) })
    }

    /**
     * Commits a drag-and-drop reorder's final order (by exercise id). Intermediate swaps during
     * the drag are handled entirely in the screen's own local working copy — not routed through
     * this StateFlow per-swap, which lagged a frame behind the LazyColumn's layout and made the
     * drag look like it stalled as soon as an item swapped.
     */
    fun reorder(orderedExerciseIds: List<Long>) = _uiState.update { state ->
        val byId = state.exercises.associateBy { it.exerciseId }
        state.copy(exercises = orderedExerciseIds.mapNotNull { byId[it] })
    }

    fun setSets(index: Int, value: Int) =
        updateItem(index) { it.copy(targetSets = value.coerceIn(SETS_MIN, SETS_MAX)) }

    fun setReps(index: Int, value: Int) =
        updateItem(index) { it.copy(targetReps = value.coerceIn(REPS_MIN, REPS_MAX)) }

    fun setRest(index: Int, value: Int) =
        updateItem(index) { it.copy(targetRestSec = value.coerceIn(REST_MIN, REST_MAX)) }

    val restStep: Int get() = REST_STEP

    private fun updateItem(index: Int, transform: (EditorExercise) -> EditorExercise) =
        _uiState.update { state ->
            if (index !in state.exercises.indices) return@update state
            state.copy(
                exercises = state.exercises.toMutableList()
                    .also { it[index] = transform(it[index]) })
        }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val specs = state.exercises.map {
                ExerciseSpec(it.exerciseId, it.targetSets, it.targetReps, it.targetRestSec)
            }
            saveRoutine(routineId.takeIf { it != 0L }, state.name, specs)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
