package dev.gouthaman.regimen.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.usecase.AddCustomExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.UpdateExerciseUseCase
import dev.gouthaman.regimen.ui.navigation.EditExerciseRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditExerciseUiState(
    val isEditing: Boolean = false,
    val name: String = "",
    val muscleGroup: MuscleGroup = customExerciseMuscleGroups.first(),
    val equipment: Equipment = customExerciseEquipment.first(),
    /** Flips true after a successful save so the screen can navigate back. */
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

@HiltViewModel
class EditExerciseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExercise: ObserveExerciseUseCase,
    private val addCustomExercise: AddCustomExerciseUseCase,
    private val updateExercise: UpdateExerciseUseCase,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<EditExerciseRoute>().exerciseId

    private val _uiState = MutableStateFlow(EditExerciseUiState(isEditing = exerciseId != 0L))
    val uiState: StateFlow<EditExerciseUiState> = _uiState.asStateFlow()

    init {
        if (exerciseId != 0L) {
            viewModelScope.launch {
                observeExercise(exerciseId).first()?.let { existing ->
                    _uiState.update {
                        it.copy(
                            name = existing.name,
                            muscleGroup = existing.muscleGroup,
                            equipment = existing.equipment,
                        )
                    }
                }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setMuscleGroup(value: MuscleGroup) = _uiState.update { it.copy(muscleGroup = value) }
    fun setEquipment(value: Equipment) = _uiState.update { it.copy(equipment = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            if (exerciseId == 0L) {
                addCustomExercise(state.name, state.muscleGroup, state.equipment)
            } else {
                // Custom exercises are strength-only in v1, so type/isCustom are fixed.
                updateExercise(
                    Exercise(
                        id = exerciseId,
                        name = state.name.trim(),
                        type = ExerciseType.STRENGTH,
                        muscleGroup = state.muscleGroup,
                        equipment = state.equipment,
                        isCustom = true,
                    )
                )
            }
            _uiState.update { it.copy(saved = true) }
        }
    }
}
