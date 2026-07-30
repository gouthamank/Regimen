package dev.gouthaman.regimen.feature.account

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.AccountRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.accountGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<AccountRoute>(
        // Account is the destination of Settings' "Account" row's container transform (its only
        // entry point), so its own entrance/exit-back-to-Settings slide would fight that
        // growth/shrink - suppress it.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        AccountScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
        )
    }
}
