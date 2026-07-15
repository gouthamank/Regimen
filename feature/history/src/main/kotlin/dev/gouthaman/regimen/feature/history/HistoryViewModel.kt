package dev.gouthaman.regimen.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutsInRangeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** One past session as shown on a calendar day (and in the day picker when a day has several).
 * Null [routineName] means it was a freeform/"Quick workout" session — resolved to display text by
 * the Composable. */
data class DaySession(
    val workoutId: Long,
    val routineName: String?,
    val startMillis: Long,
) : Serializable

data class HistoryUiState(
    val month: YearMonth = YearMonth.now(),
    /** Local calendar day → the sessions completed that day (earliest first). Scoped to [month]. */
    val sessionsByDay: Map<LocalDate, List<DaySession>> = emptyMap(),
    /** [month]'s sessions, most recent first — for the "recent workouts" list under the calendar. */
    val recentSessions: List<DaySession> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = sessionsByDay.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeWorkoutsInRange: ObserveWorkoutsInRangeUseCase,
    observeRoutines: ObserveRoutinesUseCase,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    // Drives which month's data is loaded — only the visible month is ever queried/held in
    // memory (not the entire history), so this stays small regardless of how long the app's
    // been used for.
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        _month,
        _month.flatMapLatest { month ->
            val (start, end) = month.monthMillisRange(zone)
            observeWorkoutsInRange(start, end)
        },
        observeRoutines(),
    ) { month, workouts, routines ->
        val routineNames = routines.associate { it.routine.id to it.routine.name }
        fun toDaySession(w: Workout) = DaySession(
            workoutId = w.id,
            routineName = w.routineId?.let { routineNames[it] },
            startMillis = w.startTime,
        )

        val byDay = workouts
            .map { w ->
                Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate() to toDaySession(w)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sessions) -> sessions.sortedBy { it.startMillis } }
        HistoryUiState(
            month = month,
            sessionsByDay = byDay,
            recentSessions = workouts.sortedByDescending { it.startTime }.map(::toDaySession),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setMonth(month: YearMonth) {
        _month.value = month
    }
}

/** [start, end] epoch-millis bounds spanning every moment of this calendar month, in [zone]. */
private fun YearMonth.monthMillisRange(zone: ZoneId): Pair<Long, Long> {
    val start = atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = atEndOfMonth().atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
    return start to end
}
