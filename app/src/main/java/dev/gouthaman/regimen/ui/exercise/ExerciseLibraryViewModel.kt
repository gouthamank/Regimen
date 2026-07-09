package dev.gouthaman.regimen.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ExerciseFilters(
    val query: String = "",
    val type: ExerciseType? = null,
    val muscleGroup: MuscleGroup? = null,
    val equipment: Equipment? = null,
    val customOnly: Boolean = false,
)

data class ExerciseLibraryUiState(
    val filters: ExerciseFilters = ExerciseFilters(),
    val exercises: List<Exercise> = emptyList(),
)

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    observeExercises: ObserveExercisesUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(ExerciseFilters())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ExerciseLibraryUiState> = filters
        .flatMapLatest { f ->
            observeExercises(f.query, f.type, f.muscleGroup, f.equipment, f.customOnly)
                .map { ExerciseLibraryUiState(f, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    fun setQuery(value: String) = filters.update { it.copy(query = value) }

    /** Toggling the active value clears the filter. */
    fun toggleType(value: ExerciseType) =
        filters.update { it.copy(type = if (it.type == value) null else value) }

    fun toggleMuscleGroup(value: MuscleGroup) =
        filters.update { it.copy(muscleGroup = if (it.muscleGroup == value) null else value) }

    fun toggleEquipment(value: Equipment) =
        filters.update { it.copy(equipment = if (it.equipment == value) null else value) }

    fun toggleCustomOnly() = filters.update { it.copy(customOnly = !it.customOnly) }
}
