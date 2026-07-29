package dev.gouthaman.regimen.feature.active

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.loggedVolumeKg
import dev.gouthaman.regimen.domain.usecase.GetPersonalRecordsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveWorkoutUseCase
import dev.gouthaman.regimen.domain.usecase.SaveWorkoutAsRoutineUseCase
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.domain.util.UnitLabel
import dev.gouthaman.regimen.navigation.WorkoutSummaryRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A formatted value + its unit label (e.g. displayValue="1,250", unitLabel=UnitLabel.KG), kept
 * structured so the Composable can localize the "value unit" template at render time. */
data class WeightValue(val displayValue: String, val unitLabel: UnitLabel)

data class WorkoutSummaryUiState(
    /** Null means it's a freeform/"Quick workout" session, not that it isn't loaded yet ([loaded]
     * distinguishes that) - resolved to display text by the Composable. */
    val routineName: String? = null,
    /** Raw session timing, formatted to a duration string by the Composable (SessionFormat.duration
     * is @Composable, so it can't be resolved here). */
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val accumulatedPausedMs: Long = 0L,
    val volume: WeightValue = WeightValue("0", UnitLabel.KG),
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
        getPersonalRecords(excludingWorkoutId = workoutId),
    ) { workout, routines, prefs, prs ->
        restDefaultSec = prefs.restDefaultSec
        if (workout == null) {
            WorkoutSummaryUiState(loaded = true, notFound = true)
        } else {
            val system = prefs.weightUnit
            val routineName = workout.workout.routineId
                ?.let { id -> routines.firstOrNull { it.routine.id == id }?.routine?.name }
            val completedSets = workout.exercises.flatMap { it.sets }.filter { it.isComplete }
            val volumeKg = workout.loggedVolumeKg()

            // "prs" already excludes this workout's own sets (see getPersonalRecords call
            // above), so it's the record this session actually needs to beat - a null entry
            // means the exercise has no prior history, in which case any completed set is a
            // new PR by definition; a non-null entry only counts as beaten by a strictly higher
            // session value, not merely tied.
            val priorBestWeight = prs.mapNotNull { pr ->
                pr.bestWeightKg?.let { pr.exerciseId to it }
            }.toMap()
            val priorBestReps = prs.mapNotNull { pr ->
                pr.bestReps?.let { pr.exerciseId to it }
            }.toMap()
            val prsHit = workout.exercises
                .filter { it.exercise.type == ExerciseType.STRENGTH }
                .mapNotNull { we ->
                    val completedSets = we.sets.filter { it.isComplete }
                    val sessionBestWeight = completedSets.mapNotNull { it.weightKg }.maxOrNull()
                    val priorWeight = priorBestWeight[we.exercise.id]
                    val hitWeightPr =
                        sessionBestWeight != null &&
                                (priorWeight == null || sessionBestWeight > priorWeight)

                    // Reps-PR is bodyweight-only (matches priorBestReps' own definition) - a
                    // weighted set's reps count toward the weight PR, not this one.
                    val sessionBestReps = completedSets.filter { it.weightKg == null }
                        .mapNotNull { it.reps }.maxOrNull()
                    val priorReps = priorBestReps[we.exercise.id]
                    val hitRepsPr =
                        sessionBestReps != null && (priorReps == null || sessionBestReps > priorReps)

                    if (hitWeightPr || hitRepsPr) we.exercise.name else null
                }
                .distinct()

            WorkoutSummaryUiState(
                routineName = routineName,
                startTime = workout.workout.startTime,
                endTime = workout.workout.endTime,
                accumulatedPausedMs = workout.workout.accumulatedPausedMs,
                volume = WeightValue(
                    displayValue = UnitConverter.formatCompact(
                        UnitConverter.kgToDisplay(
                            volumeKg,
                            system
                        )
                    ),
                    unitLabel = UnitConverter.weightLabel(system),
                ),
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
