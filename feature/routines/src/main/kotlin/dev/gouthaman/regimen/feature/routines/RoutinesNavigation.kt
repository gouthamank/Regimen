package dev.gouthaman.regimen.feature.routines

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.EditExerciseRoute
import dev.gouthaman.regimen.navigation.RoutineEditorRoute
import dev.gouthaman.regimen.navigation.RoutinesRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.routinesGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<RoutinesRoute> {
        RoutinesScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onCreateRoutine = { navController.navigate(RoutineEditorRoute()) },
            onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) },
        )
    }
    composable<RoutineEditorRoute>(
        // Editor is the destination of the Routines row's (or "New routine" FAB's)
        // container transform, so its own entrance/exit-back-to-list slide would fight
        // that growth/shrink - suppress it.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        RoutineEditorScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onSaved = navController::popBackStack,
            onCreateCustomExercise = { navController.navigate(EditExerciseRoute()) },
        )
    }
}
