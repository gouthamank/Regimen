package dev.gouthaman.regimen.feature.settings

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.ExerciseLibraryRoute
import dev.gouthaman.regimen.navigation.SettingsRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onOpenExerciseLibrary = { navController.navigate(ExerciseLibraryRoute) },
        )
    }
}
