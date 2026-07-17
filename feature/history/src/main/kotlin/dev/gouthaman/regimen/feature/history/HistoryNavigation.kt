package dev.gouthaman.regimen.feature.history

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.gouthaman.regimen.navigation.EditExerciseRoute
import dev.gouthaman.regimen.navigation.EditWorkoutRoute
import dev.gouthaman.regimen.navigation.HistoryRoute
import dev.gouthaman.regimen.navigation.SessionDetailRoute

/** [onWorkoutStarted] expands the persistent ActiveWorkoutSheet (`:app`'s RegimenApp/
 * ActiveWorkoutSheet) rather than navigating anywhere - Repeat starts a brand new live workout,
 * which isn't a NavHost destination (same as Home's "Start workout" - see HomeNavigation.kt). */
@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.historyGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    onWorkoutStarted: () -> Unit,
) {
    composable<HistoryRoute> {
        HistoryScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
        )
    }
    composable<SessionDetailRoute>(
        // Detail is the destination of a session row's or single-session day cell's container
        // transform, so its own entrance/exit-back-to-list slide would fight that growth/shrink -
        // suppress it (matches RoutineEditorRoute/MeasurementDetailRoute).
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) { backStackEntry ->
        SessionDetailScreen(
            workoutId = backStackEntry.toRoute<SessionDetailRoute>().workoutId,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onWorkoutStarted = onWorkoutStarted,
            onEditWorkout = { navController.navigate(EditWorkoutRoute(it)) },
        )
    }
    composable<EditWorkoutRoute> {
        // Done editing a past session - just return to Session Detail. No Workout Summary here:
        // that recap (PRs, save-as-routine) is for a session that just happened, not one you were
        // only revisiting to tweak.
        EditWorkoutScreen(
            onFinished = { navController.popBackStack() },
            onDiscarded = { navController.popBackStack() },
            onCreateCustomExercise = { navController.navigate(EditExerciseRoute()) },
        )
    }
}
