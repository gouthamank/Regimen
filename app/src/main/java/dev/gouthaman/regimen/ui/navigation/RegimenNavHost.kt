package dev.gouthaman.regimen.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.gouthaman.regimen.feature.account.accountGraph
import dev.gouthaman.regimen.feature.active.activeGraph
import dev.gouthaman.regimen.feature.exercise.exerciseGraph
import dev.gouthaman.regimen.feature.healthconnect.healthConnectGraph
import dev.gouthaman.regimen.feature.history.historyGraph
import dev.gouthaman.regimen.feature.home.homeGraph
import dev.gouthaman.regimen.feature.measurements.measurementsGraph
import dev.gouthaman.regimen.feature.progress.progressGraph
import dev.gouthaman.regimen.feature.routines.routinesGraph
import dev.gouthaman.regimen.feature.settings.settingsGraph
import dev.gouthaman.regimen.navigation.HistoryRoute
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.ProgressRoute
import dev.gouthaman.regimen.navigation.RoutinesRoute
import dev.gouthaman.regimen.navigation.SettingsRoute

/** True for the five bottom-tab destinations - used to tell a tab switch (via
 * [dev.gouthaman.regimen.ui.navigation.navigateToTab]) apart from a hierarchical drill-down, so
 * the two can get different transitions below. */
private fun isTopLevelDestination(destination: NavDestination): Boolean =
    destination.hasRoute<HomeRoute>() ||
            destination.hasRoute<RoutinesRoute>() ||
            destination.hasRoute<HistoryRoute>() ||
            destination.hasRoute<ProgressRoute>() ||
            destination.hasRoute<SettingsRoute>()

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
 *    [✓] Home       HomeRoute        ← start destination (dashboard: greeting, Start Workout
 *                                       CTA, quick-start routine chips, this-week summary, streak)
 *    [✓] Routines   RoutinesRoute
 *    [✓] History    HistoryRoute
 *    [✓] Progress   ProgressRoute
 *    [✓] Settings   SettingsRoute    ← units, theme, dynamic color, rest-timer default
 *
 *  Detail / secondary destinations (pushed above the tabs):
 *
 *    Home      ──▶ [✓] Routine Editor     RoutineEditorRoute() (empty-state "create first routine")
 *    Settings  ──▶ [✓] Exercise Library   ExerciseLibraryRoute
 *              ──▶ [✓] Account            AccountRoute (sign in/out, sync status)
 *              ──▶ [✓] Health Connect     HealthConnectSettingsRoute (biometrics auto-pull toggle,
 *                                            retry frequency, backfill window, manual pull)
 *    Library   ──▶ [✓] Exercise Detail    ExerciseDetailRoute(exerciseId)
 *              ──▶ [✓] Add/Edit Exercise  EditExerciseRoute(exerciseId=0)
 *    Detail    ──▶ [✓] Edit Exercise      EditExerciseRoute(exerciseId)
 *    Routines  ──▶ [✓] Routine Editor     RoutineEditorRoute(routineId=0) (row or "New routine" FAB
 *                                            row-expand container transform)
 *    Editor    ──▶ [✓] Exercise Picker    (modal bottom sheet, in-screen)
 *              ──▶ [✓] Add Custom Exercise EditExerciseRoute() (from picker)
 *    History   ──▶ [✓] Session Detail     SessionDetailRoute(workoutId)  (read-only + repeat/edit/
 *                                            save-as-routine/delete - Repeat starts the live sheet,
 *                                            Edit opens Edit Workout below)
 *              ──▶ [✓] Edit Workout       EditWorkoutRoute(workoutId)  (reopen a finished session
 *                                            for editing - sets/cardio/note only, no timer)
 *    Progress  ──▶ [✓] Body Measurements  MeasurementsRoute (PR list + frequency chart live on the tab root)
 *    Measure.  ──▶ [✓] Measurement Detail MeasurementDetailRoute(typeId)  (trend + entries)
 *    Progress  ──▶ [✓] Heart Rate Trends  HeartRateTrendsRoute (per-routine + combined rows, Health
 *                                            Connect-gated)
 *    HR Trends ──▶ [✓] Heart Rate Detail  HeartRateTrendDetailRoute(routineId?)  (trend + entries)
 *    Active    ──▶ [✓] Workout Summary    WorkoutSummaryRoute(workoutId)  (recap + PRs + save-as-routine)
 *    Workout   ──▶ [✓] Rest Timer (sheet, within the live ActiveWorkoutSheet; manual, adjustable, vibrate+chime)
 *
 *  EditWorkoutRoute(workoutId) (owned by :feature:history) is ONLY for editing a past
 *  (already-finished) session - the live in-progress workout isn't a destination in this NavHost
 *  at all; it's a persistent ActiveWorkoutSheet (in RegimenApp, alongside this NavHost, not
 *  inside it) that collapses to a mini-player banner or expands to the full workout-logging UI.
 *  See RegimenApp.kt/ActiveWorkoutSheet.kt and docs/architecture.md's Navigation section.
 *
 *  Full-screen gate (outside this NavHost, in MainActivity):
 *    [✓] Onboarding - shown first-run while prefs.onboarded == false
 *
 *  Active Workout support:
 *    [✓] Persistent ActiveWorkoutSheet (collapsed banner ↔ expanded full screen, drag or tap) +
 *        single-active + notif permission
 *    [✓] Foreground service (ActiveWorkoutService) + persistent Pause/Resume notification
 *    [✓] Session-Detail Repeat → live ActiveWorkoutSheet; Edit → Edit Workout (NavHost push,
 *        past-session editing only)
 *
 *  Each feature module owns its own destinations via a `NavGraphBuilder.xGraph()` extension
 *  (homeGraph, routinesGraph, historyGraph, activeGraph, progressGraph, settingsGraph,
 *  accountGraph, healthConnectGraph, measurementsGraph, exerciseGraph) - this NavHost only wires
 *  them together, it doesn't declare any `composable<Route>` itself. Onboarding is the one screen
 *  NOT routed through here at all (see MainActivity's first-launch gate).
 * ─────────────────────────────────────────────────────────────────────────
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RegimenNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onNavigateToTab: (Any) -> Unit,
    onWorkoutStarted: () -> Unit,
) {
    // Shared-axis-x transitions between hierarchical destinations (push slides in from the end +
    // fades in, popping reverses it) instead of the platform's abrupt default cross-fade.
    val transitionSpec = tween<Float>(220)

    // Bottom-tab switches (Home/Routines/History/Progress/Settings via navigateToTab) get a
    // Material "fade through" instead of the shared-axis slide above: tabs are parallel
    // destinations, not a hierarchy, so a directional slide would imply an ordering that isn't
    // there. The outgoing tab fades+shrinks out fully before the incoming one fades+grows in.
    val tabExitSpec = tween<Float>(90)
    val tabEnterSpec = tween<Float>(130, delayMillis = 90)

    // SharedTransitionLayout hosts every row/link-expand container transform in the app (Routines
    // row/FAB -> Routine Editor, Library row -> Exercise Detail, Measurements row -> Measurement
    // Detail, Settings row -> Exercise Library, Progress row -> Measurements, History row/day
    // cell -> Session Detail): each destination expands from the tapped element instead of
    // sliding in. All the keys live in :core:common-ui's SharedTransitionKeys.kt.
    SharedTransitionLayout(modifier = modifier) {
        val sharedTransitionScope = this
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (isTopLevelDestination(initialState.destination) && isTopLevelDestination(
                        targetState.destination
                    )
                ) {
                    fadeIn(tabEnterSpec) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tabEnterSpec
                    )
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(220)
                    ) +
                            fadeIn(transitionSpec)
                }
            },
            exitTransition = {
                if (isTopLevelDestination(initialState.destination) && isTopLevelDestination(
                        targetState.destination
                    )
                ) {
                    fadeOut(tabExitSpec) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tabExitSpec
                    )
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(220)
                    ) +
                            fadeOut(transitionSpec)
                }
            },
            popEnterTransition = {
                if (isTopLevelDestination(initialState.destination) && isTopLevelDestination(
                        targetState.destination
                    )
                ) {
                    fadeIn(tabEnterSpec) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tabEnterSpec
                    )
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(220)
                    ) +
                            fadeIn(transitionSpec)
                }
            },
            popExitTransition = {
                if (isTopLevelDestination(initialState.destination) && isTopLevelDestination(
                        targetState.destination
                    )
                ) {
                    fadeOut(tabExitSpec) + scaleOut(
                        targetScale = 1.04f,
                        animationSpec = tabExitSpec
                    )
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(220)
                    ) +
                            fadeOut(transitionSpec)
                }
            },
        ) {
            homeGraph(
                navController = navController,
                onSwitchToTab = { route ->
                    onNavigateToTab(route)
                    navController.navigateToTab(route)
                },
                onWorkoutStarted = onWorkoutStarted,
            )
            routinesGraph(navController, sharedTransitionScope)
            historyGraph(
                navController = navController,
                sharedTransitionScope = sharedTransitionScope,
                onWorkoutStarted = onWorkoutStarted,
            )
            activeGraph(navController)
            progressGraph(navController, sharedTransitionScope)
            settingsGraph(navController, sharedTransitionScope)
            accountGraph(navController, sharedTransitionScope)
            healthConnectGraph(navController, sharedTransitionScope)
            measurementsGraph(navController, sharedTransitionScope)
            exerciseGraph(navController, sharedTransitionScope)
        }
    }
}
