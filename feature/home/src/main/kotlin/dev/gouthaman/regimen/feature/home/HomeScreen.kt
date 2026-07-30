package dev.gouthaman.regimen.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.adaptive.RegimenWindowInfo
import dev.gouthaman.regimen.designsystem.chart.LineChart
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.designsystem.component.Stat
import dev.gouthaman.regimen.domain.util.UnitConverter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun HomeScreen(
    onCreateRoutine: () -> Unit,
    onOpenActiveWorkout: () -> Unit,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val windowInfo = LocalRegimenWindowInfo.current
    // The emitted workoutId itself isn't needed here - the ActiveWorkoutSheet gets it reactively
    // from the DB's in-progress-workout id, not from this event. This is just the "expand" signal.
    LaunchedEffect(Unit) {
        viewModel.startedWorkout.collect { onOpenActiveWorkout() }
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
    onStartWorkout: (String?) -> Unit,
    onCreateRoutine: () -> Unit,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStartSheet by remember { mutableStateOf(false) }
    // enterAlwaysScrollBehavior, not exitUntilCollapsed (used elsewhere) - this bar retracts fully
    // off-screen on scroll-down and slides back on scroll-up, rather than shrinking to a collapsed
    // row like the MediumTopAppBars on detail screens.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.greetingPeriod?.let { greetingLabel(it, uiState.firstName) }
                        ?: stringResource(R.string.home_greeting_fallback))
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            !uiState.loaded -> Unit
            !uiState.hasRoutines -> {
                // Its own centered Box, not the loaded state's scrollable Column - a Column inside
                // verticalScroll can't distribute leftover space via Arrangement, so that's the
                // only way to vertically center this.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        message = stringResource(R.string.home_empty_state_text),
                        // A line of text + button doesn't need a wide pane's full width - cap it like Onboarding's text-only content.
                        modifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                            Modifier.widthIn(max = 480.dp)
                        } else {
                            Modifier
                        },
                        actionLabel = stringResource(R.string.home_create_routine_button),
                        onAction = onCreateRoutine,
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
                        // Hides (rather than just disabling) as the ActiveWorkoutSheet's collapsed
                        // banner docks in below the nav host - Active Workout expands from that
                        // sheet, not from this button, so the button shouldn't linger once redundant.
                        AnimatedVisibility(
                            visible = !uiState.hasWorkoutInProgress,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            StartWorkoutButton(onClick = { showStartSheet = true })
                        }
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
                    // See the BookOrExpanded branch above for why this hides rather than disables.
                    AnimatedVisibility(
                        visible = !uiState.hasWorkoutInProgress,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        StartWorkoutButton(onClick = { showStartSheet = true })
                    }
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
) {
    val startButtonHeight = ButtonDefaults.LargeContainerHeight
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.padding(top = 4.dp),
        contentPadding = ButtonDefaults.contentPaddingFor(startButtonHeight, hasStartIcon = true),
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(startButtonHeight)),
        )
        Text(
            stringResource(R.string.home_start_workout_button),
            modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(startButtonHeight)),
            style = ButtonDefaults.textStyleFor(startButtonHeight),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartWorkoutSheet(
    routines: List<QuickStartRoutine>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.home_start_workout_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            routines.forEach { routine ->
                ListItem(
                    content = { Text(routine.name) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(routine.routineId) },
                )
            }
            HorizontalDivider()
            ListItem(
                content = { Text(stringResource(R.string.home_quick_workout_title)) },
                supportingContent = { Text(stringResource(R.string.home_quick_workout_subtitle)) },
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
        Text(
            stringResource(R.string.home_this_week_header),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.weight(1f)) {
                val displayedWorkouts = animatedInt(uiState.workoutsThisWeek, durationMillis = 550)
                val scale = rememberPopScale(displayedWorkouts)
                Stat(
                    stringResource(R.string.home_stat_workouts_label),
                    displayedWorkouts.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
            Card(modifier = Modifier.weight(1f)) {
                val scale = rememberPopScale(uiState.volumeThisWeek.displayValue, delayMillis = 90)
                Stat(
                    stringResource(R.string.home_stat_volume_label),
                    weightValueLabel(
                        uiState.volumeThisWeek.copy(
                            displayValue = animatedNumericLabel(
                                uiState.volumeThisWeek.displayValue,
                                durationMillis = 900,
                                delayMillis = 90,
                            ),
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
            Card(modifier = Modifier.weight(1f)) {
                val scale = rememberPopScale(uiState.durationMillisThisWeek, delayMillis = 180)
                Stat(
                    stringResource(R.string.home_stat_time_label),
                    SessionFormat.duration(
                        0L,
                        animatedMillis(
                            uiState.durationMillisThisWeek,
                            durationMillis = 750,
                            delayMillis = 180
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
        }
        if (uiState.weekStreak > 0) {
            StreakTile(uiState.weekStreak)
        }
    }
}

// "This month" mirrors the week tiles (no streak - that's a weekly concept). Durations/delays are
// deliberately offset from the week tiles' so the two rows don't tick in lockstep.
@Composable
private fun MonthSummarySection(uiState: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.home_this_month_header),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.weight(1f)) {
                val displayedWorkouts = animatedInt(
                    uiState.workoutsThisMonth,
                    durationMillis = 650,
                    delayMillis = 40
                )
                val scale = rememberPopScale(displayedWorkouts, delayMillis = 40)
                Stat(
                    stringResource(R.string.home_stat_workouts_label),
                    displayedWorkouts.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
            Card(modifier = Modifier.weight(1f)) {
                val scale =
                    rememberPopScale(uiState.volumeThisMonth.displayValue, delayMillis = 140)
                Stat(
                    stringResource(R.string.home_stat_volume_label),
                    weightValueLabel(
                        uiState.volumeThisMonth.copy(
                            displayValue = animatedNumericLabel(
                                uiState.volumeThisMonth.displayValue,
                                durationMillis = 1050,
                                delayMillis = 140,
                            ),
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
            Card(modifier = Modifier.weight(1f)) {
                val scale = rememberPopScale(uiState.durationMillisThisMonth, delayMillis = 220)
                Stat(
                    stringResource(R.string.home_stat_time_label),
                    SessionFormat.duration(
                        0L,
                        animatedMillis(
                            uiState.durationMillisThisMonth,
                            durationMillis = 900,
                            delayMillis = 220
                        ),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    valueModifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                )
            }
        }
    }
}

/** Animates 0 -> [target] over [durationMillis] (after an optional [delayMillis]), re-triggering
 * whenever [target] changes. Used to give Home's stat tiles a staggered "count up" ticker instead
 * of all landing on their values at once. */
@Composable
private fun animatedInt(target: Int, durationMillis: Int, delayMillis: Int = 0): Int {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.snapTo(0f)
        delay(delayMillis.toLong())
        animatable.animateTo(
            target.toFloat(),
            animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing)
        )
    }
    return animatable.value.roundToInt()
}

@Composable
private fun animatedMillis(target: Long, durationMillis: Int, delayMillis: Int = 0): Long {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.snapTo(0f)
        delay(delayMillis.toLong())
        animatable.animateTo(
            target.toFloat(),
            animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing)
        )
    }
    return animatable.value.roundToLong()
}

/** Same idea as [animatedInt], but for a pre-formatted decimal string (e.g. weight/volume display
 * values) - parses the target, ticks a float up to it, then re-formats each frame using the app's
 * own [UnitConverter] formatters (so e.g. a whole-number frame reads "70", not "70.0", and it lands
 * on a clean "0" rather than "0.0"). Handles [UnitConverter.formatCompact]'s "1.23k" abbreviation
 * (volume totals over 1000) by recovering the raw value and re-abbreviating as it climbs, so large
 * volumes still animate instead of snapping straight to their final compact string. Falls back to
 * the raw string if it isn't numeric. */
@Composable
private fun animatedNumericLabel(
    target: String,
    durationMillis: Int,
    delayMillis: Int = 0
): String {
    val isCompact = target.endsWith("k", ignoreCase = true)
    val targetValue = (if (isCompact) target.dropLast(1).toDoubleOrNull()
        ?.times(1000) else target.toDoubleOrNull())
        ?: return target
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.snapTo(0f)
        delay(delayMillis.toLong())
        animatable.animateTo(
            targetValue.toFloat(),
            animationSpec = tween(durationMillis, easing = LinearOutSlowInEasing),
        )
    }
    return if (isCompact) {
        UnitConverter.formatCompact(animatable.value.toDouble())
    } else {
        UnitConverter.formatValue(animatable.value.toDouble())
    }
}

/** A short scale-up-then-settle "pop", re-triggered whenever [key] changes - pairs with the
 * count-up animations above so a changed stat doesn't just tick, it visibly reacts. Quick rise
 * (matching the ticker's snappy-start easing) then a springy settle back to 1x. */
@Composable
private fun rememberPopScale(key: Any?, delayMillis: Int = 0): Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(key) {
        scale.snapTo(1f)
        delay(delayMillis.toLong())
        scale.animateTo(1.16f, animationSpec = tween(120, easing = LinearOutSlowInEasing))
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
        )
    }
    return scale.value
}

// Workout-frequency trend, fixed to the last 4 weeks (no range selector - that's Progress's job).
// Empty state stays minimal (text only, no chart/CTA) since nothing beyond the Start Workout
// button above fixes "no workouts yet".
@Composable
private fun WorkoutFrequencySection(uiState: HomeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_frequency_header),
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.workoutFrequency.all { it == 0 }) {
                Text(
                    stringResource(R.string.home_frequency_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    stringResource(R.string.home_last_4_weeks),
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
                Text(
                    stringResource(R.string.home_bodyweight_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.home_bodyweight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = onOpenMeasurements,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(stringResource(R.string.home_log_bodyweight_button))
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
                Text(
                    stringResource(R.string.home_bodyweight_title),
                    style = MaterialTheme.typography.titleMedium
                )
                val bodyweightScale =
                    rememberPopScale(uiState.bodyweightLatest?.displayValue, delayMillis = 260)
                Text(
                    uiState.bodyweightLatest?.let {
                        weightValueLabel(
                            it.copy(
                                displayValue = animatedNumericLabel(
                                    it.displayValue,
                                    durationMillis = 850,
                                    delayMillis = 260,
                                ),
                            ),
                        )
                    } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.graphicsLayer(
                        scaleX = bodyweightScale,
                        scaleY = bodyweightScale
                    ),
                )
            }
            Text(
                stringResource(R.string.home_last_4_weeks),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LineChart(points = uiState.bodyweightTrend, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun greetingLabel(period: GreetingPeriod, firstName: String?): String = when (period) {
    GreetingPeriod.MORNING -> firstName
        ?.let { stringResource(R.string.home_greeting_morning_named, it) }
        ?: stringResource(R.string.home_greeting_morning)

    GreetingPeriod.AFTERNOON -> firstName
        ?.let { stringResource(R.string.home_greeting_afternoon_named, it) }
        ?: stringResource(R.string.home_greeting_afternoon)

    GreetingPeriod.EVENING -> firstName
        ?.let { stringResource(R.string.home_greeting_evening_named, it) }
        ?: stringResource(R.string.home_greeting_evening)
}

@Composable
private fun weightValueLabel(value: WeightValue): String =
    stringResource(R.string.home_weight_value_label, value.displayValue, value.unitLabel.text())

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreakTile(weeks: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryFixed,
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
                        color = MaterialTheme.colorScheme.primaryFixedDim,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryFixed,
                    modifier = Modifier.size(22.dp),
                )
            }
            val displayedWeeks = animatedInt(weeks, durationMillis = 700, delayMillis = 320)
            val streakScale = rememberPopScale(displayedWeeks, delayMillis = 320)
            Text(
                formatStreak(displayedWeeks),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryFixed,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .graphicsLayer(scaleX = streakScale, scaleY = streakScale),
            )
        }
    }
}

// Weeks roll up to months (4wk) then years (12mo) once the streak is that long, so long streaks stay readable.
@Composable
private fun formatStreak(weeks: Int): String = when {
    weeks < 4 -> pluralStringResource(R.plurals.home_streak_weeks, weeks, weeks)
    weeks < 48 -> pluralStringResource(R.plurals.home_streak_months, weeks / 4, weeks / 4)
    else -> {
        val years = weeks / 48
        val months = (weeks % 48) / 4
        if (months == 0) {
            pluralStringResource(R.plurals.home_streak_years, years, years)
        } else {
            stringResource(
                R.string.home_streak_years_and_months,
                pluralStringResource(R.plurals.home_streak_years_part, years, years),
                pluralStringResource(R.plurals.home_streak_months_part, months, months),
            )
        }
    }
}
