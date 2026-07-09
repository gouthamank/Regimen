package dev.gouthaman.regimen.ui.measurements

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.gouthaman.regimen.ui.components.Sparkline

@Composable
fun MeasurementsScreen(
    onBack: () -> Unit,
    onOpenType: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementsScreen(
        rows = uiState.rows,
        loaded = uiState.loaded,
        onBack = onBack,
        onOpenType = onOpenType,
        onAddType = viewModel::addType,
        onAddEntry = viewModel::addEntry,
        unitSystem = uiState.unitSystem,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(
    rows: List<MeasurementRow>,
    loaded: Boolean,
    onBack: () -> Unit,
    onOpenType: (Long) -> Unit,
    onAddType: (String, String) -> Unit,
    onAddEntry: (Long, Long, Double) -> Unit,
    unitSystem: dev.gouthaman.regimen.domain.model.UnitSystem,
    modifier: Modifier = Modifier,
) {
    var showAddType by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Body measurements") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddType = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add measurement type")
                    }
                },
            )
        },
        floatingActionButton = {
            if (rows.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddEntry = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add entry") },
                )
            }
        },
    ) { innerPadding ->
        if (loaded && rows.isEmpty()) {
            EmptyState(Modifier.padding(innerPadding)) { showAddType = true }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.type.id }) { row ->
                    MeasurementCard(row = row, onClick = { onOpenType(row.type.id) })
                }
            }
        }
    }

    if (showAddType) {
        AddTypeDialog(
            onDismiss = { showAddType = false },
            onConfirm = { name, unit ->
                onAddType(name, unit)
                showAddType = false
            },
        )
    }

    if (showAddEntry) {
        AddMeasurementSheet(
            types = rows.map { it.type },
            unitSystem = unitSystem,
            onDismiss = { showAddEntry = false },
            onSave = { typeId, date, value ->
                onAddEntry(typeId, date, value)
                showAddEntry = false
            },
        )
    }
}

@Composable
private fun MeasurementCard(row: MeasurementRow, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.type.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.latestLabel ?: "No entries yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.trend.size >= 2) {
                Sparkline(
                    points = row.trend,
                    modifier = Modifier.width(88.dp).padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun AddTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New measurement type") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Waist)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. cm, %)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), unit.trim()) },
                enabled = name.isNotBlank() && unit.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier, onAddType: () -> Unit) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Track bodyweight and your own measurements (waist, arm, body-fat %…) over time.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onAddType, modifier = Modifier.padding(top = 12.dp)) {
                Text("Add a measurement type")
            }
        }
    }
}
