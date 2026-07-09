package dev.gouthaman.regimen.ui.measurements

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.ui.components.LineChart
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MeasurementDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onAddEntry = viewModel::addEntry,
        onDeleteEntry = viewModel::deleteEntry,
        onDeleteType = {
            viewModel.deleteType()
            onBack()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDetailScreen(
    uiState: MeasurementDetailUiState,
    onBack: () -> Unit,
    onAddEntry: (Long, Double) -> Unit,
    onDeleteEntry: (MeasurementEntry) -> Unit,
    onDeleteType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = uiState.type
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteType by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(type?.name ?: "Measurement") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.canDeleteType) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete measurement type") },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteType = true
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (type != null) {
                ExtendedFloatingActionButton(
                    onClick = { showAddEntry = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add entry") },
                )
            }
        },
    ) { innerPadding ->
        if (type == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.loaded) Text("Measurement not found")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.trend.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Trend", style = MaterialTheme.typography.titleMedium)
                            LineChart(
                                points = uiState.trend,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }

            if (uiState.entries.isEmpty()) {
                item {
                    Text(
                        "No entries yet. Tap Add entry to log your first ${type.name.lowercase()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                    )
                }
            } else {
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(uiState.entries, key = { it.metric.id }) { entry ->
                    EntryRow(entry = entry, onDelete = { onDeleteEntry(entry) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAddEntry && type != null) {
        AddMeasurementSheet(
            types = listOf(type),
            unitSystem = uiState.unitSystem,
            fixedTypeId = type.id,
            onDismiss = { showAddEntry = false },
            onSave = { _, date, value ->
                onAddEntry(date, value)
                showAddEntry = false
            },
        )
    }

    if (showDeleteType && type != null) {
        AlertDialog(
            onDismissRequest = { showDeleteType = false },
            title = { Text("Delete ${type.name}?") },
            text = { Text("This measurement type and all its logged entries will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteType = false
                    onDeleteType()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteType = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EntryRow(entry: MeasurementEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.valueLabel, style = MaterialTheme.typography.bodyLarge)
            Text(
                dateFormatter.format(entry.dateMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
        }
    }
}

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
