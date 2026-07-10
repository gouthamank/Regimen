package dev.gouthaman.regimen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.RoutineWithExercises
import dev.gouthaman.regimen.domain.usecase.GetHomeSummaryUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.ui.history.SessionFormat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

/** A routine offered as a quick-start chip. */
data class QuickStartRoutine(
    val routineId: Long,
    val name: String,
)

data class HomeUiState(
    val greeting: String = "",
    val hasRoutines: Boolean = false,
    /** Has at least one completed workout — unlocks the freeform Quick workout entry. */
    val isEstablished: Boolean = false,
    val workoutsThisWeek: Int = 0,
    val volumeLabel: String = "",
    val timeLabel: String = "",
    val weekStreak: Int = 0,
    val workoutsThisMonth: Int = 0,
    val volumeLabelMonth: String = "",
    val timeLabelMonth: String = "",
    /** Top few recent routines shown as quick-start chips. */
    val quickStart: List<QuickStartRoutine> = emptyList(),
    /** All routines (recency-ordered) for the "Start a workout" chooser. */
    val routines: List<QuickStartRoutine> = emptyList(),
    val loaded: Boolean = false,
)

private const val MAX_QUICK_START = 4

@HiltViewModel
class HomeViewModel @Inject constructor(
    getHomeSummary: GetHomeSummaryUseCase,
    observeRoutines: ObserveRoutinesUseCase,
    observeHistory: ObserveHistoryUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val getInProgressWorkoutId: GetInProgressWorkoutIdUseCase,
) : ViewModel() {

    // In-progress workout ids to navigate to (one-shot; buffered so the event isn't lost).
    private val startedWorkouts = Channel<Long>(Channel.BUFFERED)
    val startedWorkout: Flow<Long> = startedWorkouts.receiveAsFlow()

    /**
     * Opens the active workout: resumes the one already in progress if there is one (single-active —
     * avoids orphaning a session), otherwise starts a new one from [routineId] (null = freeform).
     */
    fun startWorkout(routineId: Long?) {
        viewModelScope.launch {
            val id = getInProgressWorkoutId() ?: startWorkoutUseCase(routineId)
            startedWorkouts.send(id)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        getHomeSummary(),
        observeRoutines(),
        observeHistory(),
        observePreferences(),
    ) { summary, routines, history, prefs ->
        val system = prefs.weightUnit

        // Order quick-start chips by most-recently-used routine, then by manual position.
        val lastUsed: Map<Long, Long> = history
            .mapNotNull { w -> w.workout.routineId?.let { it to w.workout.startTime } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }
        val orderedRoutines = routines
            .sortedWith(
                compareByDescending<RoutineWithExercises> {
                    lastUsed[it.routine.id] ?: Long.MIN_VALUE
                }.thenBy { it.routine.position }
            )
            .map { QuickStartRoutine(it.routine.id, it.routine.name) }

        HomeUiState(
            greeting = greetingFor(LocalTime.now()),
            hasRoutines = routines.isNotEmpty(),
            isEstablished = history.isNotEmpty(),
            workoutsThisWeek = summary.workoutsThisWeek,
            volumeLabel = "${
                UnitConverter.formatValue(
                    UnitConverter.kgToDisplay(
                        summary.volumeKgThisWeek,
                        system
                    )
                )
            } ${UnitConverter.weightLabel(system)}",
            timeLabel = SessionFormat.duration(0L, summary.durationMillisThisWeek),
            weekStreak = summary.weekStreak,
            workoutsThisMonth = summary.workoutsThisMonth,
            volumeLabelMonth = "${
                UnitConverter.formatValue(
                    UnitConverter.kgToDisplay(
                        summary.volumeKgThisMonth,
                        system
                    )
                )
            } ${UnitConverter.weightLabel(system)}",
            timeLabelMonth = SessionFormat.duration(0L, summary.durationMillisThisMonth),
            quickStart = orderedRoutines.take(MAX_QUICK_START),
            routines = orderedRoutines,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
