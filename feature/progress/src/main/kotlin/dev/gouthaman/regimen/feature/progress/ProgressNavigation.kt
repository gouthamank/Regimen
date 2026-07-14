package dev.gouthaman.regimen.feature.progress

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.MeasurementsRoute
import dev.gouthaman.regimen.navigation.ProgressRoute

fun NavGraphBuilder.progressGraph(navController: NavHostController) {
    composable<ProgressRoute> {
        ProgressScreen(
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
        )
    }
}
