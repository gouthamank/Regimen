package dev.gouthaman.regimen.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseResult
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.ui.history.SessionFormat
import dev.gouthaman.regimen.ui.navigation.ExerciseDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One past session's log of this exercise: its date and its formatted set/cardio entries. */
data class ExerciseHistoryItem(
    val workoutId: Long,
    val dateLabel: String,
    val entryLabels: List<String>,
)

data class ExerciseDetailUiState(
    val exerciseId: Long = 0L,
    val exercise: Exercise? = null,
    /** Heaviest weight lifted, or best reps for a bodyweight exercise, formatted for display
     * (null = none yet). */
    val prLabel: String? = null,
    /** Past sessions that logged this exercise, most recent first. */
    val history: List<ExerciseHistoryItem> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExercise: ObserveExerciseUseCase,
    getPersonalRecords: GetPersonalRecordsUseCase,
    observePreferences: ObservePreferencesUseCase,
    observeExerciseHistory: ObserveExerciseHistoryUseCase,
    private val deleteExercise: DeleteExerciseUseCase,
) : ViewModel() {

    private val exerciseId = savedStateHandle.toRoute<ExerciseDetailRoute>().exerciseId

    val uiState: StateFlow<ExerciseDetailUiState> = combine(
        observeExercise(exerciseId),
        getPersonalRecords(),
        observePreferences(),
        observeExerciseHistory(exerciseId),
    ) { exercise, prs, prefs, historySessions ->
        val prLabel = prs.firstOrNull { it.exerciseId == exerciseId }?.let { pr ->
            when {
                pr.bestWeightKg != null -> {
                    val value = UnitConverter.kgToDisplay(pr.bestWeightKg, prefs.weightUnit)
                    "${UnitConverter.formatValue(value)} ${UnitConverter.weightLabel(prefs.weightUnit)}"
                }

                pr.bestReps != null -> "${pr.bestReps} reps"
                else -> null
            }
        }
        val isStrength = exercise?.type == ExerciseType.STRENGTH
        val history = historySessions.mapNotNull { session ->
            val entryLabels = if (isStrength) {
                // Only sets completed with a value logged count — a blank/skipped placeholder set (no weight, no reps) isn't a real record.
                session.sets
                    .filter { it.isComplete && (it.weightKg != null || it.reps != null) }
                    .sortedBy { it.setNumber }
                    .map { SessionFormat.setLabel(it, prefs.weightUnit) }
            } else {
                session.cardio
                    .filter { it.durationSec > 0 || it.distanceMeters != null }
                    .map { SessionFormat.cardioLabel(it, prefs.distanceUnit) }
            }
            if (entryLabels.isEmpty()) null else {
                ExerciseHistoryItem(
                    workoutId = session.workoutExercise.workoutId,
                    dateLabel = SessionFormat.fullDate(session.startTime),
                    entryLabels = entryLabels,
                )
            }
        }
        ExerciseDetailUiState(
            exerciseId = exerciseId,
            exercise = exercise,
            prLabel = prLabel,
            history = history,
            loaded = true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExerciseDetailUiState(exerciseId = exerciseId),
    )

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _deleteBlockedMessage = MutableStateFlow<String?>(null)
    val deleteBlockedMessage: StateFlow<String?> = _deleteBlockedMessage.asStateFlow()

    fun deleteCurrent() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            when (val result = deleteExercise(exercise)) {
                DeleteExerciseResult.Deleted -> _deleted.value = true
                is DeleteExerciseResult.InUse -> {
                    val usedIn = listOfNotNull(
                        "routines".takeIf { result.inRoutines },
                        "logged workouts".takeIf { result.inWorkouts },
                    ).joinToString(" and ")
                    _deleteBlockedMessage.value =
                        "\"${exercise.name}\" is still used in $usedIn. Remove it from those first."
                }
            }
        }
    }

    fun dismissDeleteBlockedMessage() {
        _deleteBlockedMessage.value = null
    }
}
