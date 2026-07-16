package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
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
        )
    }
}
