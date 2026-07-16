package dev.gouthaman.regimen.feature.exercise

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import dev.gouthaman.regimen.navigation.EditExerciseRoute
import dev.gouthaman.regimen.navigation.ExerciseDetailRoute
import dev.gouthaman.regimen.navigation.ExerciseLibraryRoute

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.exerciseGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<ExerciseLibraryRoute>(
        // Library is the destination of Settings' "Exercise Library" row's container transform
        // (its only entry point), so its own entrance/exit-back-to-Settings slide would fight
        // that growth/shrink - suppress it.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        ExerciseLibraryScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onExerciseClick = { navController.navigate(ExerciseDetailRoute(it)) },
            onAddCustom = { navController.navigate(EditExerciseRoute()) },
        )
    }
    composable<ExerciseDetailRoute>(
        // Detail is the destination of the Library row's container transform, so its own
        // entrance/exit-back-to-Library slide would fight that growth/shrink - suppress it.
        enterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        ExerciseDetailScreen(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = this,
            onBack = navController::popBackStack,
            onEdit = { navController.navigate(EditExerciseRoute(it)) },
        )
    }
    dialog<EditExerciseRoute>(
        // A real dialog destination: unlike composable<Route>, it does NOT replace/dispose
        // the screen that launched it (Library, Detail, Active Workout, Routine Editor) -
        // that screen stays composed and visible underneath. Content is a plain
        // ModalBottomSheet (same as StartWorkoutSheet/FilterSheet elsewhere).
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        EditExerciseSheet(
            onBack = navController::popBackStack,
            onSaved = navController::popBackStack,
        )
    }
}
