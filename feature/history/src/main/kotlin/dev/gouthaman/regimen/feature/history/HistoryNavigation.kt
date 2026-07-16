package dev.gouthaman.regimen.feature.history

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.navigation.HistoryRoute
import dev.gouthaman.regimen.navigation.SessionDetailRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.historyGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
        )
    }
    composable<SessionDetailRoute>(
        // Detail is the destination of a session row's or single-session day cell's container
        // transform, so its own entrance/exit-back-to-list slide would fight that growth/shrink -
        // suppress it (matches RoutineEditorRoute/MeasurementDetailRoute).
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) { backStackEntry ->
        SessionDetailScreen(
            workoutId = backStackEntry.toRoute<SessionDetailRoute>().workoutId,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onOpenActiveWorkout = { navController.navigate(ActiveWorkoutRoute(it)) },
        )
    }
}
