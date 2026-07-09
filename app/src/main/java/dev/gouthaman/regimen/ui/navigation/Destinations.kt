package dev.gouthaman.regimen.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
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
data object ProfileRoute

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
data object MeasurementsRoute

@Serializable
data class MeasurementDetailRoute(val typeId: Long)

@Serializable
data object SettingsRoute

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
    TopLevelDestination(ProfileRoute, "Profile", Icons.Outlined.Person),
)
