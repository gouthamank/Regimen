package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.biometricTrendRowTransitionKey
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.HistoryRangeSelector
import dev.gouthaman.regimen.designsystem.chart.LineChart
import dev.gouthaman.regimen.domain.model.BiometricTrendEntry
import dev.gouthaman.regimen.domain.model.HistoryRange
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BiometricTrendDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiometricTrendDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BiometricTrendDetailScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        onMetricChange = viewModel::setMetric,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BiometricTrendDetailScreen(
    uiState: BiometricTrendDetailUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRangeChange: (HistoryRange) -> Unit = {},
    onMetricChange: (BiometricTrendMetric) -> Unit = {},
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // Entries only need their own routine called out when this screen spans every routine.
    val showRoutineName = uiState.routineId == null

    // Expands from the tapped Biometric Trends row via the shared-bounds container transform
    // keyed on this same row's routine id (null = the combined row).
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = biometricTrendRowTransitionKey(uiState.routineId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    Scaffold(
        modifier = containerModifier,
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        uiState.routineName
                            ?: stringResource(R.string.biometric_trends_combined_row_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.biometric_trend_detail_back_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MetricSelector(selected = uiState.metric, onSelect = onMetricChange)
                }
                item {
                    HistoryRangeSelector(selected = uiState.range, onSelect = onRangeChange)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(
                                    if (uiState.metric == BiometricTrendMetric.HEART_RATE) {
                                        R.string.biometric_trend_detail_heart_rate_trend_header
                                    } else {
                                        R.string.biometric_trend_detail_calories_trend_header
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (uiState.trend.isNotEmpty()) {
                                if (uiState.metric == BiometricTrendMetric.CALORIES) {
                                    LineChart(
                                        points = uiState.trend,
                                        modifier = Modifier.padding(top = 12.dp),
                                        valueFormatter = { it.roundToInt().toString() },
                                    )
                                } else {
                                    LineChart(
                                        points = uiState.trend,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            } else {
                                Text(
                                    stringResource(R.string.biometric_trend_detail_no_entries_in_range),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }

                if (uiState.entries.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.biometric_trend_detail_history_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(uiState.entries, key = { it.workoutId }) { entry ->
                        BiometricTrendEntryRow(entry, uiState.metric, showRoutineName)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricSelector(
    selected: BiometricTrendMetric,
    onSelect: (BiometricTrendMetric) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        BiometricTrendMetric.entries.forEachIndexed { index, metric ->
            SegmentedButton(
                selected = selected == metric,
                onClick = { onSelect(metric) },
                shape = SegmentedButtonDefaults.itemShape(index, BiometricTrendMetric.entries.size),
            ) {
                Text(
                    stringResource(
                        if (metric == BiometricTrendMetric.HEART_RATE) {
                            R.string.biometric_trend_detail_metric_heart_rate
                        } else {
                            R.string.biometric_trend_detail_metric_calories
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun BiometricTrendEntryRow(
    entry: BiometricTrendEntry,
    metric: BiometricTrendMetric,
    showRoutineName: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                dateFormatter().format(entry.startTime),
                style = MaterialTheme.typography.titleMedium
            )
            if (showRoutineName) {
                Text(
                    entry.routineName
                        ?: stringResource(R.string.biometric_trend_detail_quick_workout_fallback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val metricLabel = when (metric) {
                BiometricTrendMetric.HEART_RATE -> entry.avgBpm?.let {
                    stringResource(R.string.biometric_trend_detail_entry_bpm_chip, it)
                }

                BiometricTrendMetric.CALORIES -> entry.activeCaloriesKcal?.let {
                    stringResource(
                        R.string.biometric_trend_detail_entry_calories_chip,
                        it.roundToInt()
                    )
                }
            }
            metricLabel?.let { StatChip(it) }
            StatChip(SessionFormat.duration(startMillis = 0L, endMillis = entry.durationMillis))
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryFixed)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryFixed,
        )
    }
}

private fun dateFormatter() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
