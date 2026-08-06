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
data class ExerciseDetailRoute(val exerciseId: String)

@Serializable
data class EditExerciseRoute(val exerciseId: String = "")

@Serializable
data class RoutineEditorRoute(val routineId: String = "")

@Serializable
data class SessionDetailRoute(val workoutId: String)

/** Reopens a finished session for editing (Session Detail's "Edit"). The live in-progress
 * workout isn't a NavHost destination at all - see ActiveWorkoutSheet in :app. */
@Serializable
data class EditWorkoutRoute(val workoutId: String)

@Serializable
data class WorkoutSummaryRoute(val workoutId: String)

@Serializable
data object MeasurementsRoute

@Serializable
data class MeasurementDetailRoute(val typeId: String)

@Serializable
data object AccountRoute

@Serializable
data object HealthConnectSettingsRoute