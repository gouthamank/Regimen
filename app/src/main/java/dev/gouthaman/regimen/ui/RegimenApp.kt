package dev.gouthaman.regimen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.WorkoutInProgressBanner
import dev.gouthaman.regimen.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.navigation.HomeRoute
import dev.gouthaman.regimen.navigation.WorkoutSummaryRoute
import dev.gouthaman.regimen.ui.navigation.RegimenNavHost
import dev.gouthaman.regimen.ui.navigation.TopLevelDestination
import dev.gouthaman.regimen.ui.navigation.navigateToTab
import dev.gouthaman.regimen.ui.navigation.topLevelDestinations

@Composable
fun RegimenApp(
    viewModel: RegimenAppViewModel = hiltViewModel(),
    deepLinkWorkoutId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val inProgressWorkoutId by viewModel.inProgressWorkoutId.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current

    // Tracks which bottom tab stays highlighted. Routes live in one flat NavHost (no per-tab
    // nested graphs), so a pushed detail screen (e.g. Session Detail) has no graph-level tie back
    // to its tab — and restoreState can land back on that detail screen rather than the tab root,
    // so this can't be inferred from the resolved destination alone. Set eagerly at the point of
    // intent (see onNavigateToTab / navigateToTab call sites); the listener below only fills in
    // non-explicit cases (cold start, popping back to a tab's own root).
    //
    // Saved as an index into topLevelDestinations via rememberSaveable, not remember or the route
    // object itself — plain `remember` was lost on rotation (Activity recreation), resetting this
    // to null with no tab highlighted after rotating on a non-top-level screen like Session
    // Detail. The listener's immediate re-add callback only matches top-level destinations
    // directly, so it couldn't recover the value either.
    var activeTabIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val activeTabRoute: Any? = activeTabIndex?.let { topLevelDestinations.getOrNull(it)?.route }
    val onNavigateToTab: (Any) -> Unit = { route ->
        activeTabIndex = topLevelDestinations.indexOfFirst { it.route == route }.takeIf { it >= 0 }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    // Hide the resume banner while already inside the workout flow.
    val inWorkoutFlow = currentDestination?.hierarchy?.any {
        it.hasRoute(ActiveWorkoutRoute::class) || it.hasRoute(WorkoutSummaryRoute::class)
    } == true

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val matched =
                topLevelDestinations.indexOfFirst { destination.hasRoute(it.route::class) }
            if (matched >= 0) {
                activeTabIndex = matched
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // Tapping the in-progress/rest-complete notification (MainActivity's EXTRA_WORKOUT_ID) lands
    // here the same way the "Resume" banner's onResume does — anchored under Home regardless of
    // the current tab. Skipped if already inside the workout flow (e.g. re-tapping the
    // notification while the screen's already open) to avoid pushing a duplicate destination.
    LaunchedEffect(deepLinkWorkoutId) {
        if (deepLinkWorkoutId != null) {
            if (!inWorkoutFlow) {
                onNavigateToTab(HomeRoute)
                navController.navigateToTab(HomeRoute)
                navController.navigate(ActiveWorkoutRoute(deepLinkWorkoutId))
            }
            onDeepLinkConsumed()
        }
    }

    // Requested here (app launch, right after onboarding gates past MainActivity) rather than
    // inside Active Workout, so it's always resolved (granted or denied) well before a workout
    // can ever start — the system dialog is modal, so the user can't reach Home/Start Workout
    // while it's showing. This closes a real race (item 11 of the Active Workout punch list):
    // asking from inside Active Workout meant the foreground service's first startForeground()
    // call — fired the instant the workout's DB row is written, before Compose even navigates
    // there — could beat the permission grant, silently suppressing that first notification with
    // nothing to retroactively re-post it.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    NavigationSuiteScaffold(
        layoutType = windowInfo.posture.toNavigationSuiteType(),
        // NavigationRail defaults to colorScheme.surface, NavigationBar to surfaceContainer — the
        // same tone a MediumTopAppBar collapses to on scroll. Pinned to surfaceContainer here so
        // the rail (BookOrExpanded) matches that tone too, not just the bottom bar (Compact/Tabletop).
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationRailContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationSuiteItems = {
            topLevelDestinations.forEach { dest ->
                val selected = activeTabRoute == dest.route
                item(
                    selected = selected,
                    onClick = {
                        onTabSelected(
                            navController,
                            dest,
                            alreadySelected = selected,
                            onNavigateToTab,
                        )
                    },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        // Resume banner sits directly below RegimenNavHost regardless of bottom bar vs. side rail
        // — same "docked next to navigation" convention as a mini-player. Grouped with
        // RegimenNavHost as one unit so both get the same width treatment below; otherwise a
        // capped/centered NavHost with a full-bleed banner looked inconsistent, banner visibly
        // wider than the content above it.
        val activeWorkoutId = inProgressWorkoutId
        val navHostAndBanner: @Composable ColumnScope.() -> Unit = {
            RegimenNavHost(navController, Modifier.weight(1f), onNavigateToTab)
            if (activeWorkoutId != null && !inWorkoutFlow) {
                WorkoutInProgressBanner(
                    message = stringResource(R.string.workout_in_progress_banner_message),
                    viewLabel = stringResource(R.string.workout_in_progress_banner_view_label),
                    onResume = {
                        // Active Workout is Home's child exclusively (see the nav map in
                        // RegimenNavHost.kt) — Resume always anchors there regardless of which tab
                        // it's tapped from, not whichever tab is current. The tab you were on is
                        // saved, not lost, same as tapping a different tab in the bar/rail.
                        onNavigateToTab(HomeRoute)
                        navController.navigateToTab(HomeRoute)
                        navController.navigate(ActiveWorkoutRoute(activeWorkoutId))
                    },
                )
            }
        }

        // NavigationSuiteScaffold doesn't pad the content pane for the bottom system-bar inset
        // (only the nav bar/rail/drawer consume insets for themselves), so this pane does it
        // directly — otherwise the banner sits flush against the gesture-nav area.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // navHostAndBanner (everything inside RegimenNavHost) must be composed from one
            // stable call site regardless of posture. A `when` previously calling it from two
            // branches (bare vs. wrapped in Box/Column) tore down and recomposed the whole
            // nav-host subtree on every posture change, orphaning/restoring-stale every
            // rememberSaveable underneath — the actual cause of sheets spuriously
            // closing/reopening when rotating in and out of Tabletop (see "Modal/dialog dismissed
            // on rotation fix" below). Only the width-cap Modifier value varies by posture now,
            // not the composable structure.
            //
            // Compact and Tabletop both keep the bottom NavigationBar, but the window behind it
            // isn't guaranteed phone-narrow: Tabletop can be genuinely wide (confirmed via a
            // half-opened, 90°-hinge AVD state at ~852dp) since isTabletop overrides the
            // width-based Rail decision. Cap and center content in both cases rather than
            // stretching edge-to-edge under phone-like chrome. The cap is the same Medium-width
            // breakpoint classify() uses to decide "promote to BookOrExpanded"
            // (androidx.window.core.layout.WindowSizeClass) — not arbitrary, and a no-op for the
            // common Compact case since a normal phone is already narrower than this.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                val widthCapModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                        .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                        .fillMaxHeight()
                }
                Column(modifier = widthCapModifier, content = navHostAndBanner)
            }
        }
    }
}

/** Regimen's own posture buckets drive the nav layout choice for now — kept as its own named
 *  mapping (rather than inlined) so a future override (e.g. a user-facing settings toggle) has
 *  one obvious seam to hook into. Tabletop stays on the bottom bar since it's already anchored
 *  to the physical bottom edge, landing in the reachable pane below the hinge — notably, this
 *  matches what `NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo` does by default too. */
private fun RegimenPosture.toNavigationSuiteType(): NavigationSuiteType = when (this) {
    RegimenPosture.Compact, RegimenPosture.Tabletop -> NavigationSuiteType.NavigationBar
    RegimenPosture.BookOrExpanded -> NavigationSuiteType.NavigationRail
}

/**
 * Re-tapping the already-selected tab pops it back to its root; tapping a different tab
 * navigates there, saving/restoring each tab's own back-stack state.
 */
private fun onTabSelected(
    navController: NavHostController,
    dest: TopLevelDestination,
    alreadySelected: Boolean,
    onNavigateToTab: (Any) -> Unit,
) {
    if (alreadySelected) {
        navController.popBackStack(dest.route, inclusive = false)
    } else {
        onNavigateToTab(dest.route)
        navController.navigateToTab(dest.route)
    }
}

