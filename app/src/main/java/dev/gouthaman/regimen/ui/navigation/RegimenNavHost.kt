package dev.gouthaman.regimen.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.gouthaman.regimen.feature.active.activeGraph
import dev.gouthaman.regimen.feature.exercise.exerciseGraph
import dev.gouthaman.regimen.feature.history.historyGraph
import dev.gouthaman.regimen.feature.home.homeGraph
import dev.gouthaman.regimen.feature.measurements.measurementsGraph
import dev.gouthaman.regimen.feature.progress.progressGraph
import dev.gouthaman.regimen.feature.routines.routinesGraph
import dev.gouthaman.regimen.feature.settings.settingsGraph
import dev.gouthaman.regimen.navigation.HomeRoute

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
 *
 *  Each feature module owns its own destinations via a `NavGraphBuilder.xGraph()` extension
 *  (homeGraph, routinesGraph, historyGraph, activeGraph, progressGraph, settingsGraph,
 *  measurementsGraph, exerciseGraph) — this NavHost only wires them together, it doesn't declare
 *  any `composable<Route>` itself. Onboarding is the one screen NOT routed through here at all
 *  (see MainActivity's first-launch gate).
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
            homeGraph(
                navController = navController,
                onSwitchToTab = { route ->
                    onNavigateToTab(route)
                    navController.navigateToTab(route)
                },
            )
            routinesGraph(navController, sharedTransitionScope)
            historyGraph(navController)
            activeGraph(navController)
            progressGraph(navController)
            settingsGraph(navController)
            measurementsGraph(navController, sharedTransitionScope)
            exerciseGraph(navController, sharedTransitionScope)
        }
    }
}
