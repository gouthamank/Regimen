package dev.gouthaman.regimen.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WeekCount
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One personal record ready for display: exercise name + formatted heaviest weight. */
data class PersonalRecordItem(
    val exerciseId: Long,
    val exerciseName: String,
    val weightLabel: String,
)

data class ProgressUiState(
    /** Weekly workout counts, oldest week first (for the frequency chart). */
    val frequency: List<WeekCount> = emptyList(),
    val personalRecords: List<PersonalRecordItem> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Total workouts across the whole frequency window. */
    val totalInWindow: Int get() = frequency.sumOf { it.count }

    /** Workouts logged in the current (most recent) week. */
    val thisWeekCount: Int get() = frequency.lastOrNull()?.count ?: 0

    val hasFrequency: Boolean get() = totalInWindow > 0
    val hasRecords: Boolean get() = personalRecords.isNotEmpty()
    val isEmpty: Boolean get() = !hasFrequency && !hasRecords
}

private const val FREQUENCY_WEEKS = 8

@HiltViewModel
class ProgressViewModel @Inject constructor(
    getPersonalRecords: GetPersonalRecordsUseCase,
    getWorkoutFrequency: GetWorkoutFrequencyUseCase,
    observePreferences: ObservePreferencesUseCase,
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = combine(
        getPersonalRecords(),
        getWorkoutFrequency(FREQUENCY_WEEKS),
        observePreferences(),
    ) { prs, frequency, prefs ->
        val system: UnitSystem = prefs.weightUnit
        ProgressUiState(
            frequency = frequency,
            personalRecords = prs.map { pr ->
                PersonalRecordItem(
                    exerciseId = pr.exerciseId,
                    exerciseName = pr.exerciseName,
                    weightLabel = "${
                        UnitConverter.formatValue(
                            UnitConverter.kgToDisplay(
                                pr.bestWeightKg,
                                system
                            )
                        )
                    } ${UnitConverter.weightLabel(system)}",
                )
            },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}
