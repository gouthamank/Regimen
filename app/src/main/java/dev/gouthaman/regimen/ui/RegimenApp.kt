package dev.gouthaman.regimen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.ui.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.ui.adaptive.RegimenPosture
import dev.gouthaman.regimen.ui.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.ui.navigation.RegimenNavHost
import dev.gouthaman.regimen.ui.navigation.TopLevelDestination
import dev.gouthaman.regimen.ui.navigation.WorkoutSummaryRoute
import dev.gouthaman.regimen.ui.navigation.navigateToTab
import dev.gouthaman.regimen.ui.navigation.topLevelDestinations

@Composable
fun RegimenApp(
    viewModel: RegimenAppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val inProgressWorkoutId by viewModel.inProgressWorkoutId.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current

    // Tracks which bottom tab should stay highlighted. All routes live in a single flat NavHost
    // (no per-tab nested graphs), so a pushed detail screen (e.g. Session Detail) has no graph-level
    // tie back to the tab it was opened from. Switching tabs away and back restores that tab's saved
    // stack via restoreState, which can land on the detail screen rather than the tab root itself —
    // so this can't be inferred purely from whichever destination the restore happens to resolve to.
    // It's set eagerly at the point of intent (see onNavigateToTab below, passed to every
    // navigateToTab call site) and the destination-changed listener below only fills in the cases
    // that aren't an explicit tab switch (cold start, popping back up to a tab's own root).
    //
    // Saved as an index into topLevelDestinations (rememberSaveable, not remember) rather than the
    // route object itself — a plain `remember` was lost on rotation (which recreates the Activity),
    // so after rotating while on a non-top-level screen like Session Detail, this reset to null and
    // no tab stayed highlighted. The destination-changed listener's immediate callback on re-adding
    // only matches top-level destinations directly, so it couldn't recover the value either.
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

    NavigationSuiteScaffold(
        layoutType = windowInfo.posture.toNavigationSuiteType(),
        // NavigationRail defaults to colorScheme.surface, while NavigationBar defaults to
        // colorScheme.surfaceContainer — the same tone a MediumTopAppBar collapses to on scroll.
        // Pinned to surfaceContainer here so the rail always matches that tone too, instead of
        // only the bottom bar (Compact/Tabletop) matching it while the rail (BookOrExpanded)
        // looks different.
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
        // The resume banner sits directly below RegimenNavHost regardless of whether
        // NavigationSuiteScaffold is rendering a bottom bar or a side rail — same "docked next
        // to navigation" convention as a mini-player, just adjacent to whichever nav UI is active.
        // Grouped with RegimenNavHost as one unit so both get the same width treatment below —
        // a capped/centered NavHost with a full-bleed banner underneath looked inconsistent,
        // the banner visibly wider than the content above it.
        val activeWorkoutId = inProgressWorkoutId
        val navHostAndBanner: @Composable ColumnScope.() -> Unit = {
            RegimenNavHost(navController, Modifier.weight(1f), onNavigateToTab)
            if (activeWorkoutId != null && !inWorkoutFlow) {
                WorkoutInProgressBanner(
                    onResume = {
                        navController.navigate(ActiveWorkoutRoute(activeWorkoutId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        // NavigationSuiteScaffold doesn't pad the content pane for the bottom system-bar inset
        // itself (only the nav bar/rail/drawer consume insets for themselves), so this pane
        // does it directly — otherwise the banner sits flush against the gesture-nav area.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            when (windowInfo.posture) {
                // Both of these keep the bottom NavigationBar (phone-like chrome), but the
                // window behind it isn't guaranteed to be phone-narrow: Tabletop in particular
                // can be genuinely wide — confirmed via a half-opened, 90°-hinge AVD state that
                // landed here at ~852dp wide — since isTabletop overrides the width-based Rail
                // decision. Cap and center content in both cases rather than stretching it
                // edge-to-edge under nav chrome that still reads as a phone bar. The cap is the
                // same Medium-width breakpoint classify() itself uses to decide "promote to
                // BookOrExpanded" (androidx.window.core.layout.WindowSizeClass), not an
                // arbitrary number — a normal phone is already narrower than this, so it's a
                // no-op for the common Compact case.
                RegimenPosture.Compact, RegimenPosture.Tabletop -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                            .fillMaxHeight(),
                        content = navHostAndBanner,
                    )
                }

                RegimenPosture.BookOrExpanded -> navHostAndBanner()
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

@Composable
private fun WorkoutInProgressBanner(onResume: () -> Unit) {
    // Rounded only on top — reads as a mini-player peeking up from the nav-bar edge it's flush
    // against, rather than a sharp-edged strip.
    Card(
        onClick = onResume,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                "Workout in progress",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Text("View", style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
