package dev.gouthaman.regimen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.gouthaman.regimen.ui.exercise.EditExerciseScreen
import dev.gouthaman.regimen.ui.exercise.ExerciseDetailScreen
import dev.gouthaman.regimen.ui.exercise.ExerciseLibraryScreen
import dev.gouthaman.regimen.ui.history.HistoryScreen
import dev.gouthaman.regimen.ui.history.SessionDetailScreen
import dev.gouthaman.regimen.ui.home.HomeScreen
import dev.gouthaman.regimen.ui.measurements.MeasurementDetailScreen
import dev.gouthaman.regimen.ui.measurements.MeasurementsScreen
import dev.gouthaman.regimen.ui.profile.ProfileScreen
import dev.gouthaman.regimen.ui.progress.ProgressScreen
import dev.gouthaman.regimen.ui.routines.RoutineEditorScreen
import dev.gouthaman.regimen.ui.routines.RoutinesScreen

/**
 * ─────────────────────────────────────────────────────────────────────────
 *  REGIMEN NAVIGATION MAP  (keep in sync with this NavHost + Destinations.kt)
 *  Compose Navigation is code-only (no XML graph / visual editor). This is the
 *  authoritative overview. Update it whenever destinations change.
 *
 *  Legend:  [✓] wired here   [ ] declared in Destinations.kt, not yet wired
 *
 *  Bottom-tab graph (single-Activity, type-safe routes):
 *
 *    [✓] Home       HomeRoute        ← start destination
 *    [✓] Routines   RoutinesRoute
 *    [✓] History    HistoryRoute
 *    [✓] Progress   ProgressRoute
 *    [✓] Profile    ProfileRoute     ← S9 Profile/Settings lives here (units,
 *                                       theme, dynamic color, rest-timer default)
 *
 *  Detail / secondary destinations (pushed above the tabs):
 *
 *    Profile   ──▶ [✓] Exercise Library   ExerciseLibraryRoute
 *              ──▶ [✓] Body Measurements  MeasurementsRoute (S8, "Measurement types" row)
 *    Library   ──▶ [✓] Exercise Detail    ExerciseDetailRoute(exerciseId)
 *              ──▶ [✓] Add/Edit Exercise  EditExerciseRoute(exerciseId=0)
 *    Detail    ──▶ [✓] Edit Exercise      EditExerciseRoute(exerciseId)
 *    Routines  ──▶ [✓] Routine Editor     RoutineEditorRoute(routineId=0)
 *    Editor    ──▶ [✓] Exercise Picker    (S16 modal bottom sheet, in-screen)
 *              ──▶ [✓] Add Custom Exercise EditExerciseRoute() (from picker)
 *    History   ──▶ [✓] Session Detail     SessionDetailRoute(workoutId)  (S5, read-only + save-as-routine/delete;
 *                                            Repeat/Edit deferred to S13 Active Workout)
 *    Progress  ──▶ [✓] Body Measurements  MeasurementsRoute (S8, temp entry until S6 lands)
 *    Measure.  ──▶ [✓] Measurement Detail MeasurementDetailRoute(typeId)  (S8 → trend + entries)
 *
 *  Full-screen gate (outside this NavHost, in MainActivity):
 *    [✓] Onboarding (S17) — shown first-run while prefs.onboarded == false
 *
 *  Full-screen (outside the tab scaffold), added LAST:
 *    [ ] Active Workout → Rest Timer (sheet) → Workout Summary   (S13/S14/S15)
 * ─────────────────────────────────────────────────────────────────────────
 */
@Composable
fun RegimenNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> { HomeScreen() }
        composable<RoutinesRoute> {
            RoutinesScreen(
                onCreateRoutine = { navController.navigate(RoutineEditorRoute()) },
                onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(
                onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
            )
        }
        composable<SessionDetailRoute> {
            SessionDetailScreen(onBack = navController::popBackStack)
        }
        composable<ProgressRoute> {
            ProgressScreen(
                onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                onOpenExerciseLibrary = { navController.navigate(ExerciseLibraryRoute) },
                onManageMeasurementTypes = { navController.navigate(MeasurementsRoute) },
            )
        }

        composable<MeasurementsRoute> {
            MeasurementsScreen(
                onBack = navController::popBackStack,
                onOpenType = { navController.navigate(MeasurementDetailRoute(it)) },
            )
        }
        composable<MeasurementDetailRoute> {
            MeasurementDetailScreen(onBack = navController::popBackStack)
        }

        composable<ExerciseLibraryRoute> {
            ExerciseLibraryScreen(
                onBack = navController::popBackStack,
                onExerciseClick = { navController.navigate(ExerciseDetailRoute(it)) },
                onAddCustom = { navController.navigate(EditExerciseRoute()) },
            )
        }
        composable<ExerciseDetailRoute> {
            ExerciseDetailScreen(
                onBack = navController::popBackStack,
                onEdit = { navController.navigate(EditExerciseRoute(it)) },
            )
        }
        composable<EditExerciseRoute> {
            EditExerciseScreen(
                onBack = navController::popBackStack,
                onSaved = navController::popBackStack,
            )
        }
        composable<RoutineEditorRoute> {
            RoutineEditorScreen(
                onBack = navController::popBackStack,
                onSaved = navController::popBackStack,
                onCreateCustomExercise = { navController.navigate(EditExerciseRoute()) },
            )
        }
    }
}
