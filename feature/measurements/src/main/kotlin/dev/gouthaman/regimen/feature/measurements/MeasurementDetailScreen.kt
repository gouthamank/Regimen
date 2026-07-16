package dev.gouthaman.regimen.feature.measurements

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.MeasurementFormat
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.chart.HistoryRangeSelector
import dev.gouthaman.regimen.designsystem.chart.LineChart
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MeasurementDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementDetailScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onAddEntry = viewModel::addEntry,
        onDeleteEntry = viewModel::deleteEntry,
        onDeleteType = {
            viewModel.deleteType()
            onBack()
        },
        onRangeChange = viewModel::setRange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MeasurementDetailScreen(
    uiState: MeasurementDetailUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onAddEntry: (Long, Double) -> Unit,
    onDeleteEntry: (MeasurementEntry) -> Unit,
    onDeleteType: () -> Unit,
    modifier: Modifier = Modifier,
    onRangeChange: (HistoryRange) -> Unit = {},
) {
    val type = uiState.type
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteType by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Expands from the tapped Measurements row (see MeasurementsScreen's MeasurementCard) via the
    // shared-bounds container transform keyed on this measurement type's id.
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = measurementRowTransitionKey(uiState.typeId)),
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
                        type?.name ?: stringResource(R.string.measurement_detail_title_fallback)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.measurement_detail_back_description)
                        )
                    }
                },
                actions = {
                    if (uiState.canDeleteType) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.measurement_detail_more_description)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.measurement_detail_delete_type_menu_item)) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteType = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // Stay mounted through the pre-load frame (type == null, loaded == false) - otherwise
            // this shared-element FAB isn't in the tree when the entry transition starts and just
            // pops in instead of animating in anchored. Hidden only for the genuine not-found case.
            if (!uiState.loaded || type != null) {
                with(sharedTransitionScope) {
                    ExtendedFloatingActionButton(
                        onClick = { showAddEntry = true },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.measurement_detail_add_entry_fab)) },
                        modifier = Modifier.sharedElement(
                            rememberSharedContentState(key = measurementFabTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                        ),
                    )
                }
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
                if (uiState.loaded) Text(stringResource(R.string.measurement_detail_not_found))
            }
            return@Scaffold
        }

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
                if (uiState.entries.isNotEmpty()) {
                    item {
                        HistoryRangeSelector(
                            selected = uiState.range,
                            onSelect = onRangeChange,
                        )
                    }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.measurement_detail_trend_header),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (uiState.trend.isNotEmpty()) {
                                    LineChart(
                                        points = uiState.trend,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.measurement_detail_no_entries_in_range),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.entries.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                R.string.measurement_detail_no_entries_yet,
                                type.name.lowercase()
                            ),
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
                            stringResource(R.string.measurement_detail_history_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    items(uiState.entries, key = { it.metric.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            type = type,
                            weightUnit = uiState.weightUnit,
                            onDelete = { onDeleteEntry(entry) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddEntry && type != null) {
        AddMeasurementSheet(
            types = listOf(type),
            weightUnit = uiState.weightUnit,
            fixedTypeId = type.id,
            onDismiss = { showAddEntry = false },
            onSave = { _, date, value ->
                onAddEntry(date, value)
                showAddEntry = false
            },
        )
    }

    if (showDeleteType && type != null) {
        ConfirmDialog(
            title = stringResource(R.string.measurement_detail_delete_dialog_title, type.name),
            text = stringResource(R.string.measurement_detail_delete_dialog_text),
            confirmLabel = stringResource(R.string.measurement_detail_delete_confirm_button),
            onConfirm = {
                showDeleteType = false
                onDeleteType()
            },
            dismissLabel = stringResource(R.string.measurement_detail_cancel_button),
            onDismiss = { showDeleteType = false },
            destructive = true,
        )
    }
}

@Composable
private fun EntryRow(
    entry: MeasurementEntry,
    type: MeasurementType,
    weightUnit: UnitSystem,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                MeasurementFormat.format(type, entry.metric.value, weightUnit),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                dateFormatter().format(entry.dateMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.measurement_detail_delete_entry_description)
            )
        }
    }
}

private fun dateFormatter() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
