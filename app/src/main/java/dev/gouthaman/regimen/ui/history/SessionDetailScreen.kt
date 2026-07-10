package dev.gouthaman.regimen.ui.history

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.ui.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.ui.adaptive.RegimenPosture
import dev.gouthaman.regimen.ui.exercise.ExerciseIcon
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenActiveWorkout: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.openWorkout.collect { onOpenActiveWorkout(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    SessionDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    uiState: SessionDetailUiState,
    snackbarHostState: SnackbarHostState,
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = { Text(uiState.title.ifEmpty { "Session" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!uiState.notFound && uiState.loaded) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            if (uiState.exercises.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Repeat workout") },
                                    onClick = {
                                        menuExpanded = false
                                        onRepeat()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit session") },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                },
                            )
                            if (uiState.canSaveAsRoutine) {
                                DropdownMenuItem(
                                    text = { Text("Save as routine") },
                                    onClick = {
                                        menuExpanded = false
                                        showSaveAsRoutine = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete session") },
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
            ) { Text("Session not found") }
            return@Scaffold
        }

        // BookOrExpanded caps and centers the card list at the same 600dp breakpoint as
        // Onboarding/Routines; Compact/Tabletop unchanged.
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
                    ExerciseCard(exercise)
                }
            }
        }
    }

    if (showSaveAsRoutine) {
        SaveAsRoutineDialog(
            defaultName = uiState.title,
            onDismiss = { showSaveAsRoutine = false },
            onConfirm = { name ->
                showSaveAsRoutine = false
                onSaveAsRoutine(name)
                scope.launch { snackbarHostState.showSnackbar("Saved as routine") }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete this session?") },
            text = { Text("This past workout and all its logged sets will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
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
                    label = if (exerciseCount == 1) "Exercise" else "Exercises",
                )
                SessionStat(
                    icon = Icons.Filled.Schedule,
                    value = uiState.durationLabel,
                    label = "Duration",
                )
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
private fun ExerciseCard(exercise: SessionExercise) {
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
                        label = { Text("Skipped") },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                }
            }

            when {
                exercise.isSkipped -> Unit
                exercise.isStrength -> {
                    if (exercise.setLabels.isEmpty()) {
                        EmptyDetail("No sets logged")
                    } else {
                        exercise.setLabels.forEachIndexed { index, label ->
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
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }

                else -> {
                    if (exercise.cardioLabels.isEmpty()) {
                        EmptyDetail("No cardio logged")
                    } else {
                        exercise.cardioLabels.forEach { label ->
                            Text(
                                label,
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

@Composable
private fun SaveAsRoutineDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as routine") },
        text = {
            Column {
                Text(
                    "Creates a new routine from this session's strength exercises.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
