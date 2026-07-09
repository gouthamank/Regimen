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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gouthaman.regimen.ui.navigation.ActiveWorkoutRoute
import dev.gouthaman.regimen.ui.navigation.RegimenNavHost
import dev.gouthaman.regimen.ui.navigation.WorkoutSummaryRoute
import dev.gouthaman.regimen.ui.navigation.topLevelDestinations

@Composable
fun RegimenApp(
    viewModel: RegimenAppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val inProgressWorkoutId by viewModel.inProgressWorkoutId.collectAsStateWithLifecycle()

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
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(dest.route::class)
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        RegimenNavHost(navController, Modifier.padding(innerPadding))
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
