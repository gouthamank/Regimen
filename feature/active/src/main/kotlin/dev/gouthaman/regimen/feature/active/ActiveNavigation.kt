package dev.gouthaman.regimen.feature.active

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.navigation.EditExerciseRoute
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.WorkoutSummaryRoute

fun NavGraphBuilder.activeGraph(navController: NavHostController) {
    composable<ActiveWorkoutRoute> {
        ActiveWorkoutScreen(
            onFinished = { workoutId ->
                navController.navigate(WorkoutSummaryRoute(workoutId)) {
                    // Leave the finished session behind; back from the summary shouldn't reopen it.
                    popUpTo(ActiveWorkoutRoute(workoutId)) { inclusive = true }
                }
            },
            onDiscarded = { navController.popBackStack() },
            onCreateCustomExercise = { navController.navigate(EditExerciseRoute()) },
        )
    }
    composable<WorkoutSummaryRoute> {
        WorkoutSummaryScreen(
            onDone = { navController.popBackStack(HomeRoute, inclusive = false) },
        )
    }
}
