package dev.gouthaman.regimen.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import dev.gouthaman.regimen.ui.active.ActiveWorkoutScreen
import dev.gouthaman.regimen.ui.active.WorkoutSummaryScreen
import dev.gouthaman.regimen.ui.exercise.EditExerciseSheet
import dev.gouthaman.regimen.ui.exercise.ExerciseDetailScreen
import dev.gouthaman.regimen.ui.exercise.ExerciseLibraryScreen
import dev.gouthaman.regimen.ui.history.HistoryScreen
import dev.gouthaman.regimen.ui.history.SessionDetailScreen
import dev.gouthaman.regimen.ui.home.HomeScreen
import dev.gouthaman.regimen.ui.measurements.MeasurementDetailScreen
import dev.gouthaman.regimen.ui.measurements.MeasurementsScreen
import dev.gouthaman.regimen.ui.progress.ProgressScreen
import dev.gouthaman.regimen.ui.routines.RoutineEditorScreen
import dev.gouthaman.regimen.ui.routines.RoutinesScreen
import dev.gouthaman.regimen.ui.settings.SettingsScreen

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
 *    [✓] Home       HomeRoute        ← start destination (S1 dashboard: greeting, Start Workout
 *                                       CTA, quick-start routine chips, this-week summary, streak)
 *    [✓] Routines   RoutinesRoute
 *    [✓] History    HistoryRoute
 *    [✓] Progress   ProgressRoute
 *    [✓] Settings   SettingsRoute    ← S9 Settings lives here (units,
 *                                       theme, dynamic color, rest-timer default)
 *
 *  Detail / secondary destinations (pushed above the tabs):
 *
 *    Home      ──▶ [✓] Routine Editor     RoutineEditorRoute() (empty-state "create first routine")
 *              ──▶ [✓] Active Workout     ActiveWorkoutRoute(workoutId) (Start/quick-start/Quick-workout)
 *    Settings  ──▶ [✓] Exercise Library   ExerciseLibraryRoute
 *    Library   ──▶ [✓] Exercise Detail    ExerciseDetailRoute(exerciseId)
 *              ──▶ [✓] Add/Edit Exercise  EditExerciseRoute(exerciseId=0)
 *    Detail    ──▶ [✓] Edit Exercise      EditExerciseRoute(exerciseId)
 *    Routines  ──▶ [✓] Routine Editor     RoutineEditorRoute(routineId=0) (row or "New routine" FAB
 *                                            row-expand container transform)
 *    Editor    ──▶ [✓] Exercise Picker    (S16 modal bottom sheet, in-screen)
 *              ──▶ [✓] Add Custom Exercise EditExerciseRoute() (from picker)
 *    History   ──▶ [✓] Session Detail     SessionDetailRoute(workoutId)  (S5; read-only + repeat/edit/
 *                                            save-as-routine/delete — Repeat/Edit open Active Workout)
 *    Progress  ──▶ [✓] Body Measurements  MeasurementsRoute (S8; S6 PR list + frequency chart now on the tab root)
 *    Measure.  ──▶ [✓] Measurement Detail MeasurementDetailRoute(typeId)  (S8 → trend + entries)
 *
 *  Full-screen gate (outside this NavHost, in MainActivity):
 *    [✓] Onboarding (S17) — shown first-run while prefs.onboarded == false
 *
 *  Core loop (pushed above the tabs; #15):
 *    [✓] Active Workout   ActiveWorkoutRoute(workoutId)   (S13; per-set logging, skip, cardio, notes)
 *    [✓] Workout Summary  WorkoutSummaryRoute(workoutId)  (S15; recap + PRs + save-as-routine)
 *    [✓] Rest Timer (sheet, within Active Workout)         (S14; manual, adjustable, vibrate+chime)
 *    [✓] In-progress "Resume" banner (above the tab bar) + resume/single-active + notif permission (Phase 3a)
 *    [✓] Foreground service (ActiveWorkoutService) + persistent Pause/End notification + Pause (Phase 3b)
 *    [✓] Session-Detail Repeat/Edit → Active Workout (Phase 3c)  — #15 COMPLETE
 * ─────────────────────────────────────────────────────────────────────────
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RegimenNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onNavigateToTab: (Any) -> Unit,
) {
    // Shared-axis-x transitions between all destinations (push slides in from the end + fades in,
    // popping reverses it) instead of the platform's abrupt default cross-fade.
    val transitionSpec = tween<Float>(220)

    // SharedTransitionLayout hosts the row-expand container transforms for Exercise Library ->
    // Detail, Measurements -> Measurement Detail, and Routines -> Routine Editor (row or "New
    // routine" FAB): each destination expands from the tapped row/FAB instead of sliding in. See
    // exerciseRowTransitionKey / measurementRowTransitionKey / routineRowTransitionKey / routineCreateFabTransitionKey.
    SharedTransitionLayout(modifier = modifier) {
        val sharedTransitionScope = this
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                ) +
                        fadeIn(transitionSpec)
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220)
                ) +
                        fadeOut(transitionSpec)
            },
            popEnterTransition = {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) +
                        fadeIn(transitionSpec)
            },
            popExitTransition = {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) +
                        fadeOut(transitionSpec)
            },
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    // The empty-state CTA switches to the Routines tab (where routine creation lives) rather than pushing the editor directly from Home.
                    onCreateRoutine = {
                        onNavigateToTab(RoutinesRoute)
                        navController.navigateToTab(RoutinesRoute)
                    },
                    onOpenActiveWorkout = { navController.navigate(ActiveWorkoutRoute(it)) },
                    onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
                )
            }
            composable<RoutinesRoute> {
                RoutinesScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
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
                SessionDetailScreen(
                    onBack = navController::popBackStack,
                    onOpenActiveWorkout = { navController.navigate(ActiveWorkoutRoute(it)) },
                )
            }
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
            composable<ProgressRoute> {
                ProgressScreen(
                    onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenExerciseLibrary = { navController.navigate(ExerciseLibraryRoute) },
                )
            }

            composable<MeasurementsRoute> {
                MeasurementsScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::popBackStack,
                    onOpenType = { navController.navigate(MeasurementDetailRoute(it)) },
                )
            }
            composable<MeasurementDetailRoute>(
                // Detail is the destination of the Measurements row's container transform, so its
                // own entrance/exit-back-to-list slide would fight that growth/shrink — suppress it.
                enterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                MeasurementDetailScreen(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = this,
                    onBack = navController::popBackStack,
                )
            }

            composable<ExerciseLibraryRoute> {
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
                // entrance/exit-back-to-Library slide would fight that growth/shrink — suppress it.
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
                // the screen that launched it (Library, Detail, Active Workout, Routine Editor) —
                // that screen stays composed and visible underneath. Content is a plain
                // ModalBottomSheet (same as StartWorkoutSheet/FilterSheet elsewhere).
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                EditExerciseSheet(
                    onBack = navController::popBackStack,
                    onSaved = navController::popBackStack,
                )
            }
            composable<RoutineEditorRoute>(
                // Editor is the destination of the Routines row's (or "New routine" FAB's)
                // container transform, so its own entrance/exit-back-to-list slide would fight
                // that growth/shrink — suppress it.
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
    }
}
