package dev.gouthaman.regimen.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.domain.model.WeekCount
import dev.gouthaman.regimen.ui.components.LineChart
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(
        uiState = uiState,
        onOpenMeasurements = onOpenMeasurements,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onOpenMeasurements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Progress") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Body measurements") },
                    supportingContent = { Text("Bodyweight and custom measurement trends") },
                    leadingContent = { Icon(Icons.Filled.Straighten, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable(onClick = onOpenMeasurements),
                )
                HorizontalDivider()
            }

            if (uiState.loaded && uiState.isEmpty) {
                item { EmptyProgress() }
            }

            if (uiState.hasFrequency) {
                item {
                    SectionHeader("Workout frequency")
                    FrequencyCard(uiState)
                }
            }

            if (uiState.hasRecords) {
                item { SectionHeader("Personal records") }
                items(uiState.personalRecords, key = { it.exerciseId }) { pr ->
                    ListItem(
                        headlineContent = { Text(pr.exerciseName) },
                        trailingContent = {
                            Text(
                                pr.weightLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

private val weekLabelFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

@Composable
private fun FrequencyCard(uiState: ProgressUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${uiState.totalInWindow} workouts in the last ${uiState.frequency.size} weeks",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${uiState.thisWeekCount} this week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            LineChart(
                points = uiState.frequency.map { it.count.toFloat() },
                modifier = Modifier.padding(top = 16.dp),
            )
            WeekAxis(uiState.frequency)
        }
    }
}

/** Oldest and newest week-start labels bracketing the chart. */
@Composable
private fun WeekAxis(frequency: List<WeekCount>) {
    val first = frequency.firstOrNull()?.weekStart ?: return
    val last = frequency.lastOrNull()?.weekStart ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(first.format(weekLabelFormatter), style = style, color = color)
        Text(last.format(weekLabelFormatter), style = style, color = color)
    }
}

@Composable
private fun EmptyProgress() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Finish a workout to see your personal records and weekly activity here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
