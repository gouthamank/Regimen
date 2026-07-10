package dev.gouthaman.regimen.ui.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.ui.history.SessionFormat
import dev.gouthaman.regimen.ui.navigation.WorkoutSummaryRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutSummaryUiState(
    val title: String = "",
    val durationLabel: String = "",
    val volumeLabel: String = "",
    val completedSets: Int = 0,
    val prsHit: List<String> = emptyList(),
    /** Freeform (no routine) sessions with strength work can be saved as a routine. */
    val canSaveAsRoutine: Boolean = false,
    val loaded: Boolean = false,
    val notFound: Boolean = false,
)

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeWorkout: ObserveWorkoutUseCase,
    observeRoutines: ObserveRoutinesUseCase,
    observePreferences: ObservePreferencesUseCase,
    getPersonalRecords: GetPersonalRecordsUseCase,
    private val saveAsRoutineUseCase: SaveWorkoutAsRoutineUseCase,
) : ViewModel() {

    private val workoutId = savedStateHandle.toRoute<WorkoutSummaryRoute>().workoutId
    private var restDefaultSec = 90

    val uiState: StateFlow<WorkoutSummaryUiState> = combine(
        observeWorkout(workoutId),
        observeRoutines(),
        observePreferences(),
        getPersonalRecords(),
    ) { workout, routines, prefs, prs ->
        restDefaultSec = prefs.restDefaultSec
        if (workout == null) {
            WorkoutSummaryUiState(loaded = true, notFound = true)
        } else {
            val system = prefs.weightUnit
            val routineName = workout.workout.routineId
                ?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine?.name }
            val completedSets = workout.exercises.flatMap { it.sets }.filter { it.isComplete }
            val volumeKg = completedSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

            val overallBestWeight = prs.mapNotNull { pr ->
                pr.bestWeightKg?.let { pr.exerciseId to it }
            }.toMap()
            val overallBestReps = prs.mapNotNull { pr ->
                pr.bestReps?.let { pr.exerciseId to it }
            }.toMap()
            val prsHit = workout.exercises
                .filter { it.exercise.type == ExerciseType.STRENGTH }
                .mapNotNull { we ->
                    val completedSets = we.sets.filter { it.isComplete }
                    val sessionBestWeight = completedSets.mapNotNull { it.weightKg }.maxOrNull()
                    val bestWeight = overallBestWeight[we.exercise.id]
                    val hitWeightPr =
                        sessionBestWeight != null && bestWeight != null && sessionBestWeight >= bestWeight

                    val sessionBestReps = completedSets.mapNotNull { it.reps }.maxOrNull()
                    val bestReps = overallBestReps[we.exercise.id]
                    val hitRepsPr =
                        sessionBestReps != null && bestReps != null && sessionBestReps >= bestReps

                    if (hitWeightPr || hitRepsPr) we.exercise.name else null
                }
                .distinct()

            WorkoutSummaryUiState(
                title = routineName ?: "Quick workout",
                durationLabel = SessionFormat.duration(
                    workout.workout.startTime,
                    workout.workout.endTime,
                    workout.workout.accumulatedPausedMs,
                ),
                volumeLabel = "${
                    UnitConverter.formatValue(UnitConverter.kgToDisplay(volumeKg, system))
                } ${UnitConverter.weightLabel(system)}",
                completedSets = completedSets.size,
                prsHit = prsHit,
                canSaveAsRoutine = workout.workout.routineId == null &&
                        workout.exercises.any { it.exercise.type == ExerciseType.STRENGTH },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutSummaryUiState())

    fun saveAsRoutine(name: String) {
        viewModelScope.launch { saveAsRoutineUseCase(workoutId, name, restDefaultSec) }
    }
}
