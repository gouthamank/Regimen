package dev.gouthaman.regimen.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.navigation.HistoryRoute
import dev.gouthaman.regimen.navigation.SessionDetailRoute

fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable<HistoryRoute> {
        HistoryScreen(
            onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
        )
    }
    composable<SessionDetailRoute> {
        SessionDetailScreen(
            onBack = navController::popBackStack,
            onOpenActiveWorkout = { navController.navigate(ActiveWorkoutRoute(it)) },
        )
    }
}
