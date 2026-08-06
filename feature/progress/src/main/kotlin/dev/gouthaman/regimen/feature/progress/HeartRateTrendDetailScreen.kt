package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.heartRateTrendRowTransitionKey
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.HistoryRangeSelector
import dev.gouthaman.regimen.designsystem.chart.LineChart
import dev.gouthaman.regimen.domain.model.HeartRateTrendEntry
import dev.gouthaman.regimen.domain.model.HistoryRange
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HeartRateTrendDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeartRateTrendDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HeartRateTrendDetailScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onRangeChange = viewModel::setRange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HeartRateTrendDetailScreen(
    uiState: HeartRateTrendDetailUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRangeChange: (HistoryRange) -> Unit = {},
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Expands from the tapped Heart Rate Trends row via the shared-bounds container transform
    // keyed on this same row's routine id (null = the combined row).
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = heartRateTrendRowTransitionKey(uiState.routineId)),
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
                            ?: stringResource(R.string.heart_rate_trends_combined_row_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.heart_rate_trend_detail_back_description),
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
                    HistoryRangeSelector(selected = uiState.range, onSelect = onRangeChange)
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.heart_rate_trend_detail_trend_header),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (uiState.trend.isNotEmpty()) {
                                LineChart(
                                    points = uiState.trend,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            } else {
                                Text(
                                    stringResource(R.string.heart_rate_trend_detail_no_entries_in_range),
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
                            stringResource(R.string.heart_rate_trend_detail_history_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(uiState.entries, key = { it.workoutId }) { entry ->
                        HeartRateTrendEntryRow(entry)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateTrendEntryRow(entry: HeartRateTrendEntry) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.heart_rate_trend_detail_entry_bpm_label, entry.avgBpm),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            stringResource(
                R.string.heart_rate_trend_detail_entry_subtitle,
                dateFormatter().format(entry.startTime),
                SessionFormat.duration(startMillis = 0L, endMillis = entry.durationMillis),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dateFormatter() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
