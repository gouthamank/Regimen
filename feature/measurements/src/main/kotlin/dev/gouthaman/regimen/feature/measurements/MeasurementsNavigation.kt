package dev.gouthaman.regimen.feature.measurements

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.MeasurementDetailRoute
import dev.gouthaman.regimen.navigation.MeasurementsRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.measurementsGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<MeasurementsRoute> {
        MeasurementsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onOpenType = { navController.navigate(MeasurementDetailRoute(it)) },
        )
    }
    composable<MeasurementDetailRoute>(
        // Detail is the destination of the Measurements row's container transform, so its
        // own entrance/exit-back-to-list slide would fight that growth/shrink — suppress it.
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
