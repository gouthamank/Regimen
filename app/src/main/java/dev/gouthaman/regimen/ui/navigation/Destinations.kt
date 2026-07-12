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
import dev.gouthaman.regimen.navigation.HistoryRoute
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.ProgressRoute
import dev.gouthaman.regimen.navigation.RoutinesRoute
import dev.gouthaman.regimen.navigation.SettingsRoute

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
 * Navigates to a top-level (bottom-tab) [route] exactly like tapping it in the bottom bar: saves/
 * restores each tab's back stack instead of pushing a new instance. Shared by the bottom bar and
 * any in-screen shortcut that should act like a tab switch (e.g. Home's empty-state "Create your
 * first routine" → Routines tab, not the Routine Editor).
 */
fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
