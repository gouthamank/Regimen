package dev.gouthaman.regimen.feature.progress

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import dev.gouthaman.regimen.common.heartRateTrendRowTransitionKey
import dev.gouthaman.regimen.common.heartRateTrendsFromProgressTransitionKey
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.Sparkline
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.domain.model.HeartRateTrendRow
import kotlin.math.roundToInt

@Composable
fun HeartRateTrendsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenTrend: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HeartRateTrendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HeartRateTrendsScreen(
        rows = uiState.rows,
        loaded = uiState.loaded,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onOpenTrend = onOpenTrend,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HeartRateTrendsScreen(
    rows: List<HeartRateTrendRow>,
    loaded: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenTrend: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val rootModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = heartRateTrendsFromProgressTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }

    Scaffold(
        modifier = rootModifier,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.heart_rate_trends_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.heart_rate_trends_back_description),
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
            if (loaded && rows.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.heart_rate_trends_empty_state),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
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
                    items(rows, key = { it.routineId ?: "combined" }) { row ->
                        HeartRateTrendCard(
                            row = row,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onOpenTrend(row.routineId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeartRateTrendCard(
    row: HeartRateTrendRow,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val cardModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = heartRateTrendRowTransitionKey(row.routineId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
    }
    Card(modifier = cardModifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.routineName
                        ?: stringResource(R.string.heart_rate_trends_combined_row_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = row.trend.lastOrNull()?.let {
                        stringResource(R.string.heart_rate_trends_latest_avg_bpm, it.roundToInt())
                    } ?: stringResource(R.string.heart_rate_trends_no_data_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.trend.size >= 2) {
                Sparkline(
                    points = row.trend,
                    modifier = Modifier
                        .width(88.dp)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}
