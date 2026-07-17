package dev.gouthaman.regimen.feature.active

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.WorkoutSummaryRoute

/** Only the post-workout recap lives here now - the live in-progress workout is a persistent
 * ActiveWorkoutSheet (:app), not a NavHost destination, and editing a finished session is
 * :feature:history's EditWorkoutScreen (see HistoryNavigation.kt). */
fun NavGraphBuilder.activeGraph(navController: NavHostController) {
    composable<WorkoutSummaryRoute> {
        WorkoutSummaryScreen(
            onDone = { navController.popBackStack(HomeRoute, inclusive = false) },
        )
    }
}
