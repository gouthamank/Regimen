package dev.gouthaman.regimen.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.ui.navigation.ExerciseDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    /** Heaviest weight lifted for this exercise, formatted in the user's units (null = none yet). */
    val prLabel: String? = null,
    val loaded: Boolean = false,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExercise: ObserveExerciseUseCase,
    getPersonalRecords: GetPersonalRecordsUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val deleteExercise: DeleteExerciseUseCase,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        observeExercise(exerciseId),
        getPersonalRecords(),
        observePreferences(),
    ) { exercise, prs, prefs ->
        val prLabel = prs.firstOrNull { it.exerciseId == exerciseId }?.let { pr ->
            val value = UnitConverter.kgToDisplay(pr.bestWeightKg, prefs.unitSystem)
            "${UnitConverter.formatValue(value)} ${UnitConverter.weightLabel(prefs.unitSystem)}"
        }
        ExerciseDetailUiState(exercise = exercise, prLabel = prLabel, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun deleteCurrent() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch { deleteExercise(exercise) }
    }
}
