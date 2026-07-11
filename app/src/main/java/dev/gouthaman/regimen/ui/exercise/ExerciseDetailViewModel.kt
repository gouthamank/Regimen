package dev.gouthaman.regimen.ui.exercise

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseResult
import dev.gouthaman.regimen.domain.usecase.DeleteExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveExerciseUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.domain.util.UnitLabel
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

/** One past session's log of this exercise: its date and its raw set/cardio entries (exactly one
 * of the two is non-empty, per the exercise's type) — kept raw so the Composable can localize each
 * entry's label via SessionFormat.setLabel/cardioLabel at render time. */
data class ExerciseHistoryItem(
    val workoutId: Long,
    val dateLabel: String,
    val sets: List<SetEntry> = emptyList(),
    val cardio: List<CardioEntry> = emptyList(),
)

/** Either a formatted heaviest weight, or (for a bodyweight exercise with no logged weight) best
 * reps. Kept structured rather than a pre-formatted String so the Composable can localize/pluralize
 * "reps" at render time. */
sealed interface ExercisePrValue {
    data class Weight(val displayValue: String, val unitLabel: UnitLabel) : ExercisePrValue
    data class Reps(val count: Int) : ExercisePrValue
}

/** Why deletion was refused: which category(ies) still reference this exercise. Kept structured so
 * the Composable can localize the message at render time. */
data class ExerciseDeleteBlockedInfo(
    val exerciseName: String,
    val inRoutines: Boolean,
    val inWorkouts: Boolean,
)

data class ExerciseDetailUiState(
    val exerciseId: Long = 0L,
    val exercise: Exercise? = null,
    /** Heaviest weight lifted, or best reps for a bodyweight exercise (null = none yet). */
    val pr: ExercisePrValue? = null,
    /** Past sessions that logged this exercise, most recent first. */
    val history: List<ExerciseHistoryItem> = emptyList(),
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    val distanceUnit: UnitSystem = UnitSystem.METRIC,
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
        val pr = prs.firstOrNull { it.exerciseId == exerciseId }?.let { pr ->
            when {
                pr.bestWeightKg != null -> ExercisePrValue.Weight(
                    displayValue = UnitConverter.formatValue(
                        UnitConverter.kgToDisplay(pr.bestWeightKg, prefs.weightUnit)
                    ),
                    unitLabel = UnitConverter.weightLabel(prefs.weightUnit),
                )

                pr.bestReps != null -> ExercisePrValue.Reps(pr.bestReps)
                else -> null
            }
        }
        val isStrength = exercise?.type == ExerciseType.STRENGTH
        val history = historySessions.mapNotNull { session ->
            if (isStrength) {
                // Only sets completed with a value logged count — a blank/skipped placeholder set (no weight, no reps) isn't a real record.
                val sets = session.sets
                    .filter { it.isComplete && (it.weightKg != null || it.reps != null) }
                    .sortedBy { it.setNumber }
                if (sets.isEmpty()) null else {
                    ExerciseHistoryItem(
                        workoutId = session.workoutExercise.workoutId,
                        dateLabel = SessionFormat.fullDate(session.startTime),
                        sets = sets,
                    )
                }
            } else {
                val cardio =
                    session.cardio.filter { it.durationSec > 0 || it.distanceMeters != null }
                if (cardio.isEmpty()) null else {
                    ExerciseHistoryItem(
                        workoutId = session.workoutExercise.workoutId,
                        dateLabel = SessionFormat.fullDate(session.startTime),
                        cardio = cardio,
                    )
                }
            }
        }
        ExerciseDetailUiState(
            exerciseId = exerciseId,
            exercise = exercise,
            pr = pr,
            history = history,
            weightUnit = prefs.weightUnit,
            distanceUnit = prefs.distanceUnit,
            loaded = true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExerciseDetailUiState(exerciseId = exerciseId),
    )

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _deleteBlockedInfo = MutableStateFlow<ExerciseDeleteBlockedInfo?>(null)
    val deleteBlockedInfo: StateFlow<ExerciseDeleteBlockedInfo?> = _deleteBlockedInfo.asStateFlow()

    fun deleteCurrent() {
        val exercise = uiState.value.exercise ?: return
        viewModelScope.launch {
            when (val result = deleteExercise(exercise)) {
                DeleteExerciseResult.Deleted -> _deleted.value = true
                is DeleteExerciseResult.InUse -> {
                    _deleteBlockedInfo.value = ExerciseDeleteBlockedInfo(
                        exerciseName = exercise.name,
                        inRoutines = result.inRoutines,
                        inWorkouts = result.inWorkouts,
                    )
                }
            }
        }
    }

    fun dismissDeleteBlockedMessage() {
        _deleteBlockedInfo.value = null
    }
}
