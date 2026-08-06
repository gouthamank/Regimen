package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.BiometricTrendDetailRoute
import dev.gouthaman.regimen.navigation.BiometricTrendsRoute
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
            onOpenBiometricTrends = { navController.navigate(BiometricTrendsRoute) },
        )
    }

    // Progress is the only entry point for both, so their entrance/exit slide is unconditionally
    // suppressed in favor of the row's container-transform (matches AccountNavigation.kt).
    composable<BiometricTrendsRoute>(
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        BiometricTrendsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onOpenTrend = { routineId -> navController.navigate(BiometricTrendDetailRoute(routineId)) },
        )
    }

    composable<BiometricTrendDetailRoute>(
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        BiometricTrendDetailScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
        )
    }
}
