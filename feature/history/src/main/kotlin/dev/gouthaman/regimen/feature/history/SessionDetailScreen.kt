package dev.gouthaman.regimen.feature.history

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.sessionRowTransitionKey
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.designsystem.dialog.SaveAsRoutineDialog
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.feature.exercise.ExerciseIcon
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(
    workoutId: Long,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onWorkoutStarted: () -> Unit,
    onEditWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Repeat starts a brand new live workout (expands the ActiveWorkoutSheet, doesn't push a
    // NavHost destination); Edit reopens this specific historical session, which is a real push.
    LaunchedEffect(Unit) {
        viewModel.startedWorkout.collect { onWorkoutStarted() }
    }
    LaunchedEffect(Unit) {
        viewModel.editWorkout.collect { onEditWorkout(it) }
    }

    SessionDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        workoutId = workoutId,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onDelete = {
            viewModel.delete()
            onBack()
        },
        onSaveAsRoutine = viewModel::saveAsRoutine,
        onRepeat = viewModel::repeat,
        onEdit = viewModel::edit,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SessionDetailScreen(
    uiState: SessionDetailUiState,
    snackbarHostState: SnackbarHostState,
    workoutId: Long,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSaveAsRoutine: (String) -> Unit,
    onRepeat: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showSaveAsRoutine by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = sessionRowTransitionKey(workoutId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }
    Scaffold(
        modifier = containerModifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        when {
                            !uiState.loaded -> stringResource(R.string.session_detail_title_fallback)
                            uiState.routineName != null -> uiState.routineName
                            else -> stringResource(R.string.session_detail_quick_workout_fallback)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.session_detail_back_description)
                        )
                    }
                },
                actions = {
                    if (!uiState.notFound && uiState.loaded) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.session_detail_more_description)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (uiState.exercises.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_detail_repeat_menu_item)) },
                                    onClick = {
                                        menuExpanded = false
                                        onRepeat()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_detail_edit_menu_item)) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            if (uiState.canSaveAsRoutine) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_detail_save_as_routine_menu_item)) },
                                    onClick = {
                                        menuExpanded = false
                                        showSaveAsRoutine = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_detail_delete_menu_item)) },
                                onClick = {
                                    menuExpanded = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
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
            ) { Text(stringResource(R.string.session_detail_not_found)) }
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
                item { SessionSummaryCard(uiState) }

                items(uiState.exercises, key = { it.workoutExerciseId }) { exercise ->
                    ExerciseCard(exercise, uiState.weightUnit, uiState.distanceUnit)
                }
            }
        }
    }

    if (showSaveAsRoutine) {
        val savedAsRoutineMessage =
            stringResource(R.string.session_detail_saved_as_routine_snackbar)
        SaveAsRoutineDialog(
            title = stringResource(R.string.session_detail_save_as_routine_menu_item),
            dialogText = stringResource(R.string.session_detail_save_as_routine_dialog_text),
            nameLabel = stringResource(R.string.session_detail_routine_name_label),
            saveLabel = stringResource(R.string.session_detail_save_button),
            cancelLabel = stringResource(R.string.session_detail_cancel_button),
            defaultName = uiState.routineName.orEmpty(),
            onDismiss = { showSaveAsRoutine = false },
            onConfirm = { name ->
                showSaveAsRoutine = false
                onSaveAsRoutine(name)
                scope.launch { snackbarHostState.showSnackbar(savedAsRoutineMessage) }
            },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.session_detail_delete_dialog_title),
            text = stringResource(R.string.session_detail_delete_dialog_text),
            confirmLabel = stringResource(R.string.session_detail_delete_confirm_button),
            onConfirm = {
                showDelete = false
                onDelete()
            },
            dismissLabel = stringResource(R.string.session_detail_cancel_button),
            onDismiss = { showDelete = false },
            destructive = true,
        )
    }
}

/** Hero card: date + at-a-glance stats, in a tonal container so it reads as the session's summary. */
@Composable
private fun SessionSummaryCard(uiState: SessionDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    uiState.dateLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (uiState.autoEnded) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        stringResource(R.string.session_detail_auto_ended_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                val exerciseCount = uiState.exercises.size
                SessionStat(
                    icon = Icons.Filled.FitnessCenter,
                    value = exerciseCount.toString(),
                    label = pluralStringResource(
                        R.plurals.session_detail_exercise_count,
                        exerciseCount
                    ),
                )
                SessionStat(
                    icon = Icons.Filled.Schedule,
                    value = SessionFormat.duration(
                        uiState.startTime,
                        uiState.endTime,
                        uiState.accumulatedPausedMs
                    ),
                    label = stringResource(R.string.session_detail_duration_label),
                )
                if (uiState.volume != null) {
                    SessionStat(
                        icon = Icons.Filled.MonitorWeight,
                        value = stringResource(
                            R.string.session_detail_volume_value_label,
                            uiState.volume.displayValue,
                            uiState.volume.unitLabel.text(),
                        ),
                        label = stringResource(R.string.session_detail_volume_label),
                    )
                }
            }
            if (uiState.note != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
                Row {
                    Icon(
                        Icons.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                    )
                    Text(
                        uiState.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStat(icon: ImageVector, value: String, label: String) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium, color = tint)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = tint.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: SessionExercise,
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseIcon(
                    type = if (exercise.isStrength) ExerciseType.STRENGTH else ExerciseType.CARDIO,
                    equipment = exercise.equipment,
                )
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                if (exercise.isSkipped) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.session_detail_skipped_chip)) },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }

            when {
                exercise.isSkipped -> Unit
                exercise.isStrength -> {
                    if (exercise.sets.isEmpty()) {
                        EmptyDetail(stringResource(R.string.session_detail_no_sets_logged))
                    } else {
                        exercise.sets.forEachIndexed { index, set ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    SessionFormat.setLabel(set, weightUnit),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (exercise.cardio.isEmpty()) {
                        EmptyDetail(stringResource(R.string.session_detail_no_cardio_logged))
                    } else {
                        exercise.cardio.forEach { cardio ->
                            Text(
                                SessionFormat.cardioLabel(cardio, distanceUnit),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDetail(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
