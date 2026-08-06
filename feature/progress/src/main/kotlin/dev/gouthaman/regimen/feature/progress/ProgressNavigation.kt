package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.HeartRateTrendDetailRoute
import dev.gouthaman.regimen.navigation.HeartRateTrendsRoute
import dev.gouthaman.regimen.navigation.MeasurementsRoute
import dev.gouthaman.regimen.navigation.ProgressRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.progressGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<ProgressRoute> {
        ProgressScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
            onOpenHeartRateTrends = { navController.navigate(HeartRateTrendsRoute) },
        )
    }

    // Progress is the only entry point for both, so their entrance/exit slide is unconditionally
    // suppressed in favor of the row's container-transform (matches AccountNavigation.kt).
    composable<HeartRateTrendsRoute>(
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        HeartRateTrendsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onOpenTrend = { routineId -> navController.navigate(HeartRateTrendDetailRoute(routineId)) },
        )
    }

    composable<HeartRateTrendDetailRoute>(
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        HeartRateTrendDetailScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
        )
    }
}
