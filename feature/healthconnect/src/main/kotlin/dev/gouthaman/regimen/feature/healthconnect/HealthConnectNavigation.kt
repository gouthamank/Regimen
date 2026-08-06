package dev.gouthaman.regimen.feature.healthconnect

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.HealthConnectSettingsRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.healthConnectGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<HealthConnectSettingsRoute>(
        // Settings' row container-transform is the only entry point - suppress the default
        // slide so it doesn't fight that growth/shrink.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        HealthConnectSettingsScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
        )
    }
}
