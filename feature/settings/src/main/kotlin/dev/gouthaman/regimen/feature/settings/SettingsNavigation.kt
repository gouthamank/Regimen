package dev.gouthaman.regimen.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.ExerciseLibraryRoute
import dev.gouthaman.regimen.navigation.SettingsRoute

fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<SettingsRoute> {
        SettingsScreen(
            onOpenExerciseLibrary = { navController.navigate(ExerciseLibraryRoute) },
        )
    }
}
