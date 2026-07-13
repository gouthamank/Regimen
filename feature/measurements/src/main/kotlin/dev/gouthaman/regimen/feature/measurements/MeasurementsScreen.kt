package dev.gouthaman.regimen.feature.measurements

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.MeasurementFormat
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.Sparkline
import dev.gouthaman.regimen.designsystem.component.EmptyState

@Composable
fun MeasurementsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenType: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementsScreen(
        rows = uiState.rows,
        loaded = uiState.loaded,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onOpenType = onOpenType,
        onAddType = viewModel::addType,
        onAddEntry = viewModel::addEntry,
        weightUnit = uiState.weightUnit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MeasurementsScreen(
    rows: List<MeasurementRow>,
    loaded: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onOpenType: (Long) -> Unit,
    onAddType: (String, String) -> Unit,
    onAddEntry: (Long, Long, Double) -> Unit,
    weightUnit: dev.gouthaman.regimen.domain.model.UnitSystem,
    modifier: Modifier = Modifier,
) {
    var showAddType by rememberSaveable { mutableStateOf(false) }
    var showAddEntry by rememberSaveable { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.measurements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.measurements_back_description)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddType = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.measurements_add_type_description)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (rows.isNotEmpty()) {
                with(sharedTransitionScope) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddEntry = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.measurements_add_entry_fab)) },
                        modifier = Modifier.sharedElement(
                            rememberSharedContentState(key = measurementFabTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    )
                }
            }
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
                    message = stringResource(R.string.measurements_empty_state_description),
                    modifier = (if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                        Modifier.widthIn(max = 480.dp)
                    } else {
                        Modifier
                    }).fillMaxSize(),
                    actionLabel = stringResource(R.string.measurements_empty_state_button),
                    onAction = { showAddType = true },
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
                    items(rows, key = { it.type.id }) { row ->
                        MeasurementCard(
                            row = row,
                            weightUnit = weightUnit,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onOpenType(row.type.id) },
                        )
                    }
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
            weightUnit = weightUnit,
            onDismiss = { showAddEntry = false },
            onSave = { typeId, date, value ->
                onAddEntry(typeId, date, value)
                showAddEntry = false
            },
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MeasurementCard(
    row: MeasurementRow,
    weightUnit: dev.gouthaman.regimen.domain.model.UnitSystem,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val cardModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = measurementRowTransitionKey(row.type.id)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
    }
    Card(
        modifier = cardModifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.type.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = row.latestValue?.let {
                        MeasurementFormat.format(
                            row.type,
                            it,
                            weightUnit
                        )
                    }
                        ?: stringResource(R.string.measurements_no_entries_yet),
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

@Composable
private fun AddTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measurements_new_type_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.measurements_new_type_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(R.string.measurements_new_type_unit_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), unit.trim()) },
                enabled = name.isNotBlank() && unit.isNotBlank(),
            ) { Text(stringResource(R.string.measurements_add_type_confirm_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.measurements_add_type_cancel_button)) } },
    )
}
