package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.label
import dev.gouthaman.regimen.common.measurementsFromProgressTransitionKey
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.HistoryRangeSelector
import dev.gouthaman.regimen.designsystem.chart.LineChart
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.designsystem.component.SectionHeader
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.WeekCount
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onOpenMeasurements = onOpenMeasurements,
        onRangeChange = viewModel::setRange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    onRangeChange: (HistoryRange) -> Unit = {},
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.progress_title)) },
                scrollBehavior = scrollBehavior
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val listModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    val linkModifier = with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = measurementsFromProgressTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                    ListItem(
                        content = { Text(stringResource(R.string.progress_measurements_link_title)) },
                        supportingContent = { Text(stringResource(R.string.progress_measurements_link_subtitle)) },
                        leadingContent = {
                            Icon(Icons.Filled.Straighten, contentDescription = null)
                        },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        },
                        modifier = linkModifier.clickable(onClick = onOpenMeasurements),
                    )
                    HorizontalDivider()
                }

                if (uiState.loaded && uiState.isEmpty) {
                    item {
                        EmptyState(
                            message = stringResource(R.string.progress_empty_state),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (uiState.hasFrequency) {
                    item {
                        SectionHeader(
                            stringResource(R.string.progress_frequency_header),
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp,
                            ),
                        )
                        HistoryRangeSelector(
                            selected = uiState.range,
                            onSelect = onRangeChange,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        FrequencyCard(uiState)
                    }
                }

                if (uiState.hasRecords) {
                    item {
                        SectionHeader(
                            stringResource(R.string.progress_records_header),
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp,
                            ),
                        )
                    }
                    uiState.personalRecordGroups.forEach { group ->
                        item(key = "muscle_group_${group.muscleGroup.name}") {
                            SectionHeader(
                                group.muscleGroup.label(),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(
                                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp,
                                ),
                            )
                        }
                        items(group.records, key = { it.exerciseId }) { pr ->
                            ListItem(
                                content = { Text(pr.exerciseName) },
                                trailingContent = {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(MaterialTheme.colorScheme.tertiaryFixed)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            personalRecordValueLabel(pr.value),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryFixed,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun personalRecordValueLabel(value: PersonalRecordValue): String = when (value) {
    is PersonalRecordValue.Weight ->
        stringResource(
            R.string.progress_weight_value_label,
            value.displayValue,
            value.unitLabel.text()
        )

    is PersonalRecordValue.Reps ->
        pluralStringResource(R.plurals.progress_reps_count, value.count, value.count)
}

private fun weekLabelFormatter() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private fun weekLabelFormatterWithYear() =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

@Composable
private fun FrequencyCard(uiState: ProgressUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(
                    R.string.progress_frequency_summary,
                    pluralStringResource(
                        R.plurals.progress_workout_count,
                        uiState.totalInWindow,
                        uiState.totalInWindow
                    ),
                    pluralStringResource(
                        R.plurals.progress_week_count,
                        uiState.frequency.size,
                        uiState.frequency.size
                    ),
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                pluralStringResource(
                    R.plurals.progress_this_week_count,
                    uiState.thisWeekCount,
                    uiState.thisWeekCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            LineChart(
                points = uiState.frequency.map { it.count.toFloat() },
                modifier = Modifier.padding(top = 16.dp),
                zeroBaseline = true,
            )
            WeekAxis(uiState.frequency, uiState.range)
        }
    }
}

/** Oldest and newest week-start labels bracketing the chart. */
@Composable
private fun WeekAxis(frequency: List<WeekCount>, range: HistoryRange) {
    val first = frequency.firstOrNull()?.weekStart ?: return
    val last = frequency.lastOrNull()?.weekStart ?: return
    val formatter = if (range == HistoryRange.ONE_YEAR || range == HistoryRange.ALL) {
        weekLabelFormatterWithYear()
    } else {
        weekLabelFormatter()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(first.format(formatter), style = style, color = color)
        Text(last.format(formatter), style = style, color = color)
    }
}
