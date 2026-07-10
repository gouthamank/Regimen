package dev.gouthaman.regimen.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.usecase.ObserveHistoryUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One past session as shown on a calendar day (and in the day picker when a day has several). */
data class DaySession(
    val workoutId: Long,
    val title: String,
    val startMillis: Long,
) : Serializable

data class HistoryUiState(
    /** Local calendar day → the sessions completed that day (earliest first). */
    val sessionsByDay: Map<LocalDate, List<DaySession>> = emptyMap(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = sessionsByDay.isEmpty()
    val totalSessions: Int get() = sessionsByDay.values.sumOf { it.size }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistory: ObserveHistoryUseCase,
    observeRoutines: ObserveRoutinesUseCase,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val uiState: StateFlow<HistoryUiState> = combine(
        observeHistory(),
        observeRoutines(),
    ) { workouts, routines ->
        val routineNames = routines.associate { it.routine.id to it.routine.name }
        val byDay = workouts
            .map { w ->
                val day = Instant.ofEpochMilli(w.workout.startTime).atZone(zone).toLocalDate()
                day to DaySession(
                    workoutId = w.workout.id,
                    title = w.workout.routineId?.let { routineNames[it] } ?: "Quick workout",
                    startMillis = w.workout.startTime,
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sessions) -> sessions.sortedBy { it.startMillis } }
        HistoryUiState(sessionsByDay = byDay, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
