package dev.gouthaman.regimen.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.MeasurementsRoute
import dev.gouthaman.regimen.navigation.RoutinesRoute

/**
 * [onSwitchToTab] combines "act like tapping the Routines tab" (bottom-bar selected-tab state and
 * the NavHost's tab back stack) into one callback built by `:app`'s composition root, so this
 * module doesn't depend on either directly. [onWorkoutStarted] expands `:app`'s persistent
 * ActiveWorkoutSheet rather than navigating anywhere - it isn't a NavHost destination.
 */
fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    onSwitchToTab: (Any) -> Unit,
    onWorkoutStarted: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            // The empty-state CTA switches to the Routines tab (where routine creation lives) rather than pushing the editor directly from Home.
            onCreateRoutine = { onSwitchToTab(RoutinesRoute) },
            onOpenActiveWorkout = onWorkoutStarted,
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
        )
    }
}
