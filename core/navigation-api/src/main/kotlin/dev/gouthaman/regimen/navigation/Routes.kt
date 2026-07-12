package dev.gouthaman.regimen.navigation

import kotlinx.serialization.Serializable

/** Top-level (bottom-tab) destinations. */
@Serializable
data object HomeRoute

@Serializable
data object RoutinesRoute

@Serializable
data object HistoryRoute

@Serializable
data object ProgressRoute

@Serializable
data object SettingsRoute

/** Detail / secondary destinations. */
@Serializable
data object ExerciseLibraryRoute

@Serializable
data class ExerciseDetailRoute(val exerciseId: Long)

@Serializable
data class EditExerciseRoute(val exerciseId: Long = 0L)

@Serializable
data class RoutineEditorRoute(val routineId: Long = 0L)

@Serializable
data class SessionDetailRoute(val workoutId: Long)

@Serializable
data class ActiveWorkoutRoute(val workoutId: Long)

@Serializable
data class WorkoutSummaryRoute(val workoutId: Long)

@Serializable
data object MeasurementsRoute

@Serializable
data class MeasurementDetailRoute(val typeId: Long)
