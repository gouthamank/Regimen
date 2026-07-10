package dev.gouthaman.regimen.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
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

data class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, "Home", Icons.Filled.Home),
    TopLevelDestination(RoutinesRoute, "Routines", Icons.Filled.FitnessCenter),
    TopLevelDestination(HistoryRoute, "History", Icons.Filled.CalendarMonth),
    TopLevelDestination(ProgressRoute, "Progress", Icons.AutoMirrored.Filled.TrendingUp),
    TopLevelDestination(SettingsRoute, "Settings", Icons.Filled.Settings),
)

/**
 * Navigates to a top-level (bottom-tab) [route] exactly as tapping it in the bottom bar would:
 * saves/restores each tab's own back stack rather than pushing a new instance on top. Shared by
 * the bottom bar itself and any in-screen shortcut that should act like switching tabs (e.g.
 * Home's empty-state "Create your first routine" going to the Routines tab instead of pushing
 * the Routine Editor).
 */
fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
