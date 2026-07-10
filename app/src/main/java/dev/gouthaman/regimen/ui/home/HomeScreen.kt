package dev.gouthaman.regimen.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.ui.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.ui.adaptive.RegimenPosture
import dev.gouthaman.regimen.ui.adaptive.RegimenWindowInfo
import dev.gouthaman.regimen.ui.components.LineChart

@Composable
fun HomeScreen(
    onCreateRoutine: () -> Unit,
    onOpenActiveWorkout: (Long) -> Unit,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current
    LaunchedEffect(Unit) {
        viewModel.startedWorkout.collect { onOpenActiveWorkout(it) }
    }
    HomeScreen(
        uiState = uiState,
        windowInfo = windowInfo,
        onStartWorkout = viewModel::startWorkout,
        onCreateRoutine = onCreateRoutine,
        onOpenMeasurements = onOpenMeasurements,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    windowInfo: RegimenWindowInfo,
    onStartWorkout: (Long?) -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartSheet by remember { mutableStateOf(false) }
    // enterAlwaysScrollBehavior, not exitUntilCollapsed (used elsewhere) — this bar retracts fully
    // off-screen on scroll-down and slides back on scroll-up, rather than shrinking to a collapsed
    // row like the MediumTopAppBars on detail screens.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(uiState.greeting.ifEmpty { "Regimen" }) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            !uiState.loaded -> Unit
            !uiState.hasRoutines -> {
                // Its own centered Box, not the loaded state's scrollable Column — a Column inside
                // verticalScroll can't distribute leftover space via Arrangement, so that's the
                // only way to vertically center this.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyHome(
                        onCreateRoutine = onCreateRoutine,
                        // A line of text + button doesn't need a wide pane's full width — cap it like Onboarding's text-only content.
                        modifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                            Modifier.widthIn(max = 480.dp)
                        } else {
                            Modifier
                        },
                    )
                }
            }

            else -> when (windowInfo.posture) {
                RegimenPosture.BookOrExpanded -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        StartWorkoutButton(
                            onClick = { showStartSheet = true },
                            enabled = !uiState.hasWorkoutInProgress,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) { WeekSummarySection(uiState) }
                            Box(modifier = Modifier.weight(1f)) { MonthSummarySection(uiState) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) { WorkoutFrequencySection(uiState) }
                            Box(modifier = Modifier.weight(1f)) {
                                BodyweightSection(uiState, onOpenMeasurements)
                            }
                        }
                    }
                }

                RegimenPosture.Compact, RegimenPosture.Tabletop -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    StartWorkoutButton(
                        onClick = { showStartSheet = true },
                        enabled = !uiState.hasWorkoutInProgress,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    WeekSummarySection(uiState)
                    MonthSummarySection(uiState)
                    WorkoutFrequencySection(uiState)
                    BodyweightSection(uiState, onOpenMeasurements)
                }
            }
        }
    }

    if (showStartSheet) {
        StartWorkoutSheet(
            routines = uiState.routines,
            onPick = {
                showStartSheet = false
                onStartWorkout(it)
            },
            onDismiss = { showStartSheet = false },
        )
    }
}

@Composable
private fun StartWorkoutButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val startButtonHeight = ButtonDefaults.LargeContainerHeight
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.padding(top = 4.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(startButtonHeight, hasStartIcon = true),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(startButtonHeight)),
        )
        Text(
            "Start Workout",
            modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(startButtonHeight)),
            style = ButtonDefaults.textStyleFor(startButtonHeight),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkoutSheet(
    routines: List<QuickStartRoutine>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Start a workout",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            routines.forEach { routine ->
                ListItem(
                    headlineContent = { Text(routine.name) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(routine.routineId) },
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Quick workout") },
                supportingContent = { Text("Freeform — no routine") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(null) },
            )
        }
    }
}

// "This week" as individually styled tiles (per-stat + a streak tile) rather than one card, for a livelier dashboard.
@Composable
private fun WeekSummarySection(uiState: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This week", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile("Workouts", uiState.workoutsThisWeek.toString(), Modifier.weight(1f))
            StatTile("Volume", uiState.volumeLabel, Modifier.weight(1f))
            StatTile("Time", uiState.timeLabel, Modifier.weight(1f))
        }
        if (uiState.weekStreak > 0) {
            StreakTile(uiState.weekStreak)
        }
    }
}

// "This month" mirrors the week tiles (no streak — that's a weekly concept).
@Composable
private fun MonthSummarySection(uiState: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This month", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile("Workouts", uiState.workoutsThisMonth.toString(), Modifier.weight(1f))
            StatTile("Volume", uiState.volumeLabelMonth, Modifier.weight(1f))
            StatTile("Time", uiState.timeLabelMonth, Modifier.weight(1f))
        }
    }
}

// Workout-frequency trend, fixed to the last 4 weeks (no range selector — that's Progress's job).
// Empty state stays minimal (text only, no chart/CTA) since nothing beyond the Start Workout
// button above fixes "no workouts yet".
@Composable
private fun WorkoutFrequencySection(uiState: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Workout frequency", style = MaterialTheme.typography.titleMedium)
            if (uiState.workoutFrequency.all { it == 0 }) {
                Text(
                    "Finish a workout to start tracking your frequency.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    "Last 4 weeks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LineChart(
                    points = uiState.workoutFrequency.map { it.toFloat() },
                    modifier = Modifier.padding(top = 12.dp),
                    zeroBaseline = true,
                )
            }
        }
    }
}

// Bodyweight trend, fixed to the last 4 weeks. Empty state (no entries logged yet) gets a
// single CTA into Body Measurements, since there's a concrete action that fixes it.
@Composable
private fun BodyweightSection(uiState: HomeUiState, onOpenMeasurements: () -> Unit) {
    if (uiState.bodyweightTrend.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bodyweight", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Log your bodyweight to see a trend here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onOpenMeasurements,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Log bodyweight")
                }
            }
        }
        return
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Bodyweight", style = MaterialTheme.typography.titleMedium)
                Text(
                    uiState.bodyweightLatestLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Last 4 weeks",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LineChart(points = uiState.bodyweightTrend, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreakTile(weeks: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                formatStreak(weeks),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

// Weeks roll up to months (4wk) then years (12mo) once the streak is that long, so long streaks stay readable.
private fun formatStreak(weeks: Int): String = when {
    weeks < 4 -> "$weeks-week streak"
    weeks < 48 -> "${weeks / 4}-month streak"
    else -> {
        val years = weeks / 48
        val months = (weeks % 48) / 4
        if (months == 0) "$years-year streak" else "$years-year, $months-month streak"
    }
}

@Composable
private fun EmptyHome(onCreateRoutine: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Build a routine to start tracking your workouts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onCreateRoutine) {
            Text("Create your first routine")
        }
    }
}
