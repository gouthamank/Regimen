package dev.gouthaman.regimen.ui.active

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.Stat
import dev.gouthaman.regimen.designsystem.dialog.SaveAsRoutineDialog
import dev.gouthaman.regimen.ui.util.text

@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutSummaryScreen(
        uiState = uiState,
        onDone = onDone,
        onSaveAsRoutine = viewModel::saveAsRoutine,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSummaryScreen(
    uiState: WorkoutSummaryUiState,
    onDone: () -> Unit,
    onSaveAsRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.workout_summary_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (uiState.notFound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.workout_summary_not_found)) }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    when {
                        !uiState.loaded -> ""
                        uiState.routineName != null -> uiState.routineName
                        else -> stringResource(R.string.workout_summary_quick_workout_fallback)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Stat(
                            stringResource(R.string.workout_summary_duration_label),
                            SessionFormat.duration(
                                uiState.startTime,
                                uiState.endTime,
                                uiState.accumulatedPausedMs
                            )
                        )
                        Stat(
                            stringResource(R.string.workout_summary_volume_label),
                            stringResource(
                                R.string.workout_summary_weight_value_label,
                                uiState.volume.displayValue,
                                uiState.volume.unitLabel.text()
                            ),
                        )
                        Stat(
                            stringResource(R.string.workout_summary_sets_label),
                            uiState.completedSets.toString()
                        )
                    }
                }

                if (uiState.prsHit.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.workout_summary_records_header),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            uiState.prsHit.forEach { name ->
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                if (savedName != null) {
                    Text(
                        stringResource(R.string.workout_summary_saved_as_routine, savedName!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (uiState.canSaveAsRoutine) {
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.workout_summary_save_as_routine_action)) }
                }

                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.workout_summary_done_button)) }
            }
        }
    }

    if (showSaveDialog) {
        SaveAsRoutineDialog(
            title = stringResource(R.string.workout_summary_save_as_routine_action),
            dialogText = stringResource(R.string.workout_summary_save_as_routine_dialog_text),
            nameLabel = stringResource(R.string.workout_summary_routine_name_label),
            saveLabel = stringResource(R.string.workout_summary_save_button),
            cancelLabel = stringResource(R.string.workout_summary_cancel_button),
            defaultName = uiState.routineName.orEmpty(),
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                savedName = name
                onSaveAsRoutine(name)
            },
        )
    }
}
