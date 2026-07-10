package dev.gouthaman.regimen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

    // Tracks which bottom tab should stay highlighted. All routes live in a single flat NavHost
    // (no per-tab nested graphs), so a pushed detail screen (e.g. Session Detail) has no graph-level
    // tie back to the tab it was opened from. Switching tabs away and back restores that tab's saved
    // stack via restoreState, which can land on the detail screen rather than the tab root itself —
    // so this can't be inferred purely from whichever destination the restore happens to resolve to.
    // It's set eagerly at the point of intent (see onNavigateToTab below, passed to every
    // navigateToTab call site) and the destination-changed listener below only fills in the cases
    // that aren't an explicit tab switch (cold start, popping back up to a tab's own root).
    var activeTabRoute by remember { mutableStateOf<Any?>(null) }
    val onNavigateToTab: (Any) -> Unit = { route -> activeTabRoute = route }

    Scaffold(
        // The top status-bar inset is handled by each screen's own TopAppBar. Zeroing the
        // top inset here avoids double-counting it (which left a gap under the status bar);
        // the bottom bar still reserves its own space + handles the navigation-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Hide the resume banner while already inside the workout flow.
            val inWorkoutFlow = currentDestination?.hierarchy?.any {
                it.hasRoute(ActiveWorkoutRoute::class) || it.hasRoute(WorkoutSummaryRoute::class)
            } == true

            DisposableEffect(navController) {
                val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    val matched =
                        topLevelDestinations.firstOrNull { destination.hasRoute(it.route::class) }
                    if (matched != null) {
                        activeTabRoute = matched.route
                    }
                }
                navController.addOnDestinationChangedListener(listener)
                onDispose { navController.removeOnDestinationChangedListener(listener) }
            }

            Column {
                val activeId = inProgressWorkoutId
                if (activeId != null && !inWorkoutFlow) {
                    WorkoutInProgressBanner(
                        onResume = {
                            navController.navigate(ActiveWorkoutRoute(activeId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                NavigationBar {
                    topLevelDestinations.forEach { dest ->
                        val selected = activeTabRoute == dest.route
                        NavigationBarItem(
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
                }
            }
        },
    ) { innerPadding ->
        RegimenNavHost(navController, Modifier.padding(innerPadding), onNavigateToTab)
    }
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
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onResume),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(
                "Workout in progress",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Text("Resume", style = MaterialTheme.typography.labelLarge)
        }
    }
}
