package dev.gouthaman.regimen.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WeekCount
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.domain.util.UnitLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Either a formatted heaviest weight, or (for a bodyweight exercise with no logged weight) best
 * reps. Kept structured rather than a pre-formatted String so the Composable can localize/pluralize
 * "reps" at render time. */
sealed interface PersonalRecordValue {
    data class Weight(val displayValue: String, val unitLabel: UnitLabel) : PersonalRecordValue
    data class Reps(val count: Int) : PersonalRecordValue
}

/** One personal record ready for display: exercise name + its best value. */
data class PersonalRecordItem(
    val exerciseId: Long,
    val exerciseName: String,
    val value: PersonalRecordValue,
)

/** Personal records for one muscle group, in the same order [GetPersonalRecordsUseCase] sorted
 * them overall (heaviest/highest first) — grouping never re-sorts. */
data class PersonalRecordGroup(
    val muscleGroup: MuscleGroup,
    val records: List<PersonalRecordItem>,
)

data class ProgressUiState(
    /** Weekly workout counts, oldest week first (for the frequency chart). */
    val frequency: List<WeekCount> = emptyList(),
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    /** Non-empty muscle groups only, in [MuscleGroup] declaration order. */
    val personalRecordGroups: List<PersonalRecordGroup> = emptyList(),
    val loaded: Boolean = false,
) {
    /** Total workouts across the whole frequency window. */
    val totalInWindow: Int get() = frequency.sumOf { it.count }

    /** Workouts logged in the current (most recent) week. */
    val thisWeekCount: Int get() = frequency.lastOrNull()?.count ?: 0

    val hasFrequency: Boolean get() = totalInWindow > 0
    val hasRecords: Boolean get() = personalRecordGroups.isNotEmpty()
    val isEmpty: Boolean get() = !hasFrequency && !hasRecords
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    getPersonalRecords: GetPersonalRecordsUseCase,
    getWorkoutFrequency: GetWorkoutFrequencyUseCase,
    observePreferences: ObservePreferencesUseCase,
) : ViewModel() {

    private val _range = MutableStateFlow(HistoryRange.THREE_MONTHS)
    val range: StateFlow<HistoryRange> = _range.asStateFlow()

    val uiState: StateFlow<ProgressUiState> = combine(
        getPersonalRecords(),
        _range.flatMapLatest { getWorkoutFrequency(it) },
        _range,
        observePreferences(),
    ) { prs, frequency, range, prefs ->
        val system: UnitSystem = prefs.weightUnit
        val itemsByGroup: Map<MuscleGroup, List<PersonalRecordItem>> = prs
            .map { pr ->
                val bestWeightKg = pr.bestWeightKg
                pr.muscleGroup to PersonalRecordItem(
                    exerciseId = pr.exerciseId,
                    exerciseName = pr.exerciseName,
                    value = when {
                        bestWeightKg != null -> PersonalRecordValue.Weight(
                            displayValue = UnitConverter.formatValue(
                                UnitConverter.kgToDisplay(bestWeightKg, system)
                            ),
                            unitLabel = UnitConverter.weightLabel(system),
                        )

                        else -> PersonalRecordValue.Reps(pr.bestReps ?: 0)
                    },
                )
            }
            // groupBy is stable, so each group keeps prs' overall heaviest/highest-first order.
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        ProgressUiState(
            frequency = frequency,
            range = range,
            personalRecordGroups = MuscleGroup.entries.mapNotNull { group ->
                itemsByGroup[group]?.let { PersonalRecordGroup(group, it) }
            },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun setRange(value: HistoryRange) {
        _range.value = value
    }
}
