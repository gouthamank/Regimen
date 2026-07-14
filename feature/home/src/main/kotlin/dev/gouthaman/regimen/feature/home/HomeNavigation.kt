package dev.gouthaman.regimen.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.MeasurementsRoute
import dev.gouthaman.regimen.navigation.RoutinesRoute

/**
 * [onSwitchToTab] is a single combined callback for "act like tapping the Routines tab" (updates
 * the bottom-bar's own selected-tab state *and* the NavHost's tab back stack) — both halves of
 * that live in `:app` (`RegimenApp`'s tab state, `NavHostController.navigateToTab`), so the
 * composition root builds this callback and hands it down rather than this module depending on
 * either directly.
 */
fun NavGraphBuilder.homeGraph(
    navController: NavHostController,
    onSwitchToTab: (Any) -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(
            // The empty-state CTA switches to the Routines tab (where routine creation lives) rather than pushing the editor directly from Home.
            onCreateRoutine = { onSwitchToTab(RoutinesRoute) },
            onOpenActiveWorkout = { navController.navigate(ActiveWorkoutRoute(it)) },
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
        )
    }
}
