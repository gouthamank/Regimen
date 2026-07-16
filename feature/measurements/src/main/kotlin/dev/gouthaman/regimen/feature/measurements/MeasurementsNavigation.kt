package dev.gouthaman.regimen.feature.measurements

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.MeasurementDetailRoute
import dev.gouthaman.regimen.navigation.MeasurementsRoute
import dev.gouthaman.regimen.navigation.ProgressRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.measurementsGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<MeasurementsRoute>(
        // Only suppress the default slide when the container transform from Progress's row is
        // actually in flight - opened from Home's "Log bodyweight" button instead, there's no
        // matching shared element, so it keeps the ordinary slide+fade.
        enterTransition = {
            if (initialState.destination.hasRoute<ProgressRoute>()) {
                EnterTransition.None
            } else {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                ) +
                        fadeIn(tween(220))
            }
        },
        popExitTransition = {
            if (targetState.destination.hasRoute<ProgressRoute>()) {
                ExitTransition.None
            } else {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                ) +
                        fadeOut(tween(220))
            }
        },
    ) {
        val cameFromProgress =
            navController.previousBackStackEntry?.destination?.hasRoute<ProgressRoute>() == true
        MeasurementsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            cameFromProgress = cameFromProgress,
            onBack = navController::popBackStack,
            onOpenType = { navController.navigate(MeasurementDetailRoute(it)) },
        )
    }
    composable<MeasurementDetailRoute>(
        // Detail is the destination of the Measurements row's container transform, so its
        // own entrance/exit-back-to-list slide would fight that growth/shrink - suppress it.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        MeasurementDetailScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
        )
    }
}
