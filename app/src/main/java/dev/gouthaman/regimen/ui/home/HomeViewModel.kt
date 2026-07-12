package dev.gouthaman.regimen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.common.MeasurementFormat
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.model.cutoffMillis
import dev.gouthaman.regimen.domain.usecase.GetHomeSummaryUseCase
import dev.gouthaman.regimen.domain.usecase.GetInProgressWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.GetWorkoutFrequencyUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveActiveWorkoutIdUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.StartWorkoutUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.domain.util.UnitLabel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/** A formatted value + its unit label (e.g. displayValue="72", unitLabel=UnitLabel.KG), kept
 * structured so the Composable can localize the "value unit" template at render time. */
data class WeightValue(val displayValue: String, val unitLabel: UnitLabel)

enum class GreetingPeriod { MORNING, AFTERNOON, EVENING }

data class HomeUiState(
    val greetingPeriod: GreetingPeriod? = null,
    val hasRoutines: Boolean = false,
    /** Has at least one completed workout — unlocks the freeform Quick workout entry. */
    val isEstablished: Boolean = false,
    val workoutsThisWeek: Int = 0,
    val volumeThisWeek: WeightValue = WeightValue("0", UnitLabel.KG),
    val durationMillisThisWeek: Long = 0L,
    val weekStreak: Int = 0,
    val workoutsThisMonth: Int = 0,
    val volumeThisMonth: WeightValue = WeightValue("0", UnitLabel.KG),
    val durationMillisThisMonth: Long = 0L,
    /** Top few recent routines shown as quick-start chips. */
    val quickStart: List<QuickStartRoutine> = emptyList(),
    /** All routines (recency-ordered) for the "Start a workout" chooser. */
    val routines: List<QuickStartRoutine> = emptyList(),
    /** Workouts per week, oldest first — fixed to the last 4 weeks. */
    val workoutFrequency: List<Int> = emptyList(),
    /** Bodyweight entries in the last 4 weeks, oldest first, in display units. */
    val bodyweightTrend: List<Float> = emptyList(),
    /** Most recent bodyweight entry, e.g. displayValue="72" unitLabel="kg"; null if none logged. */
    val bodyweightLatest: WeightValue? = null,
    /** A workout is already running — Start Workout resumes it via the banner instead of launching
     * a fresh pick-a-routine flow. */
    val hasWorkoutInProgress: Boolean = false,
    val loaded: Boolean = false,
)

private const val MAX_QUICK_START = 4

@HiltViewModel
class HomeViewModel @Inject constructor(
    getHomeSummary: GetHomeSummaryUseCase,
    observeRoutines: ObserveRoutinesUseCase,
    observeHistory: ObserveHistoryUseCase,
    observePreferences: ObservePreferencesUseCase,
    getWorkoutFrequency: GetWorkoutFrequencyUseCase,
    observeMeasurementTypes: ObserveMeasurementTypesUseCase,
    observeMeasurements: ObserveMeasurementsUseCase,
    observeActiveWorkoutId: ObserveActiveWorkoutIdUseCase,
    private val startWorkoutUseCase: StartWorkoutUseCase,
    private val getInProgressWorkoutId: GetInProgressWorkoutIdUseCase,
) : ViewModel() {

    // In-progress workout ids to navigate to (one-shot; buffered so the event isn't lost).
    private val startedWorkouts = Channel<Long>(Channel.BUFFERED)
    val startedWorkout: Flow<Long> = startedWorkouts.receiveAsFlow()

    // Bodyweight is the built-in measurement type, resolved dynamically since it's a seeded row,
    // not a fixed id — no type yet (fresh install) means an empty trend.
    private val bodyweightTrend: Flow<List<Float>> = combine(
        observeMeasurementTypes(),
        observePreferences(),
    ) { types, prefs -> types.firstOrNull { it.isBuiltIn } to prefs.weightUnit }
        .flatMapLatest { (type, unit) ->
            if (type == null) {
                flowOf(emptyList())
            } else {
                observeMeasurements(type.id).map { metrics ->
                    val cutoff = HistoryRange.FOUR_WEEKS.cutoffMillis()!!
                    metrics.filter { it.date >= cutoff }
                        .sortedBy { it.date }
                        .map { MeasurementFormat.toDisplay(type, it.value, unit).toFloat() }
                }
            }
        }

    /**
     * Opens the active workout: resumes one already in progress (single-active — avoids
     * orphaning a session), or starts a new one from [routineId] (null = freeform).
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
        combine(
            getWorkoutFrequency(HistoryRange.FOUR_WEEKS),
            bodyweightTrend,
            observeActiveWorkoutId(),
        ) { frequency, trend, activeWorkoutId -> Triple(frequency, trend, activeWorkoutId) },
    ) { summary, routines, history, prefs, (frequency, weightTrend, activeWorkoutId) ->
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
            greetingPeriod = greetingPeriodFor(LocalTime.now()),
            hasRoutines = routines.isNotEmpty(),
            isEstablished = history.isNotEmpty(),
            workoutsThisWeek = summary.workoutsThisWeek,
            volumeThisWeek = WeightValue(
                displayValue = UnitConverter.formatValue(
                    UnitConverter.kgToDisplay(summary.volumeKgThisWeek, system)
                ),
                unitLabel = UnitConverter.weightLabel(system),
            ),
            durationMillisThisWeek = summary.durationMillisThisWeek,
            weekStreak = summary.weekStreak,
            workoutsThisMonth = summary.workoutsThisMonth,
            volumeThisMonth = WeightValue(
                displayValue = UnitConverter.formatValue(
                    UnitConverter.kgToDisplay(summary.volumeKgThisMonth, system)
                ),
                unitLabel = UnitConverter.weightLabel(system),
            ),
            durationMillisThisMonth = summary.durationMillisThisMonth,
            quickStart = orderedRoutines.take(MAX_QUICK_START),
            routines = orderedRoutines,
            workoutFrequency = frequency.map { it.count },
            bodyweightTrend = weightTrend,
            bodyweightLatest = weightTrend.lastOrNull()?.let {
                WeightValue(
                    displayValue = UnitConverter.formatValue(it.toDouble()),
                    unitLabel = UnitConverter.weightLabel(system),
                )
            },
            hasWorkoutInProgress = activeWorkoutId != null,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

private fun greetingPeriodFor(time: LocalTime): GreetingPeriod = when (time.hour) {
    in 5..11 -> GreetingPeriod.MORNING
    in 12..17 -> GreetingPeriod.AFTERNOON
    else -> GreetingPeriod.EVENING
}
