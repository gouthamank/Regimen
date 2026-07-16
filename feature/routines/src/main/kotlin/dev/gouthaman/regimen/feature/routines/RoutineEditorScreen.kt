package dev.gouthaman.regimen.feature.routines

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.label
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ExercisePickerSheet
import dev.gouthaman.regimen.designsystem.dragdrop.dragHandle
import dev.gouthaman.regimen.designsystem.dragdrop.rememberDragDropState

@Composable
fun RoutineEditorScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    RoutineEditorScreen(
        uiState = uiState,
        restStep = viewModel.restStep,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onNameChange = viewModel::setName,
        onAddExercises = viewModel::setExercises,
        onCreateCustomExercise = onCreateCustomExercise,
        onRemove = viewModel::removeAt,
        onReorder = viewModel::reorder,
        onSetsChange = viewModel::setSets,
        onRepsChange = viewModel::setReps,
        onRestChange = viewModel::setRest,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RoutineEditorScreen(
    uiState: RoutineEditorUiState,
    restStep: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAddExercises: (List<Long>) -> Unit,
    onCreateCustomExercise: () -> Unit,
    onRemove: (Int) -> Unit,
    onReorder: (orderedExerciseIds: List<Long>) -> Unit,
    onSetsChange: (Int, Int) -> Unit,
    onRepsChange: (Int, Int) -> Unit,
    onRestChange: (Int, Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Expands from the tapped Routines row when editing, or from the "New routine" FAB when
    // creating (see RoutinesScreen's RoutineCard / FAB) via the shared-bounds container transform.
    val transitionKey = if (uiState.routineId != 0L) {
        routineRowTransitionKey(uiState.routineId)
    } else {
        routineCreateFabTransitionKey
    }
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = transitionKey),
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
                        stringResource(
                            if (uiState.isEditing) R.string.routine_editor_edit_title else R.string.routine_editor_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.routine_editor_back_description)
                        )
                    }
                },
                actions = {
                    FilledIconButton(onClick = onSave, enabled = uiState.canSave) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(
                                if (uiState.isEditing) R.string.routine_editor_save_description else R.string.routine_editor_add_description,
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val windowInfo = LocalRegimenWindowInfo.current
        val listState = rememberLazyListState()
        // Exactly one leading item (the name field) sits above the exercise list, so a dragged
        // item's *global* index (what DragDropState tracks against LazyListState's layout info)
        // is always the exercise's local index + 1. The empty-state text item never coexists with
        // actual exercises, so it doesn't affect this.
        val leadingItems = 1

        // Working copy the drag reorders in place, synchronously - swaps go straight here, not
        // through the ViewModel per-swap, which lagged a frame behind the LazyColumn's layout and
        // made the drag look stalled the moment two items swapped. Re-synced from the source when
        // it changes and we're not mid-drag; final order is committed to the ViewModel once, on drop.
        val working = remember { mutableStateListOf<EditorExercise>() }
        val dragState = rememberDragDropState(
            listState,
            // Restricts valid drag starts/targets to the exercise rows themselves - otherwise a
            // drag near the top can match the name field (global index 0) as a target, which
            // underflows `to - leadingItems` to -1 and crashes the working-list mutation below.
            draggableIndices = leadingItems until (leadingItems + working.size),
        ) { draggedKey, targetKey ->
            val from = working.indexOfFirst { it.exerciseId == draggedKey }
            val to = working.indexOfFirst { it.exerciseId == targetKey }
            if (from != -1 && to != -1) working.add(to, working.removeAt(from))
        }

        LaunchedEffect(uiState.exercises) {
            if (dragState.draggingItemKey == null) {
                working.clear()
                working.addAll(uiState.exercises)
            }
        }

        // BookOrExpanded caps and centers the form at the same 600dp breakpoint Onboarding uses;
        // Compact/Tabletop stay full-bleed (scrollable form, no hinge-adjacent controls to protect).
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
                state = listState,
                modifier = listModifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        label = { Text(stringResource(R.string.routine_editor_name_field_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (working.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.routine_editor_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(working, key = { _, e -> e.exerciseId }) { index, exercise ->
                    val globalIndex = index + leadingItems
                    val dragging = exercise.exerciseId == dragState.draggingItemKey
                    val itemModifier = if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragState.draggingItemOffset }
                    } else {
                        Modifier.animateItem()
                    }
                    ExerciseEditorCard(
                        exercise = exercise,
                        elevated = dragging,
                        restStep = restStep,
                        onRemove = { onRemove(index) },
                        dragHandleModifier = Modifier.dragHandle(dragState, globalIndex) {
                            onReorder(working.map { it.exerciseId })
                        },
                        onSetsChange = { onSetsChange(index, it) },
                        onRepsChange = { onRepsChange(index, it) },
                        onRestChange = { onRestChange(index, it) },
                        modifier = itemModifier,
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.routine_editor_add_exercise_button),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = uiState.availableStrength,
            initiallySelected = uiState.usedIds,
            onConfirm = { ids ->
                onAddExercises(ids)
                showPicker = false
            },
            onDismiss = { showPicker = false },
            onCreateCustom = {
                showPicker = false
                onCreateCustomExercise()
            },
        )
    }
}

@Composable
private fun ExerciseEditorCard(
    exercise: EditorExercise,
    elevated: Boolean,
    restStep: Int,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (elevated) Modifier.shadow(8.dp, MaterialTheme.shapes.medium) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(
                        R.string.routine_editor_reorder_description,
                        exercise.name
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(horizontal = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${exercise.muscleGroup.label()} · ${exercise.equipment.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.routine_editor_remove_description)
                    )
                }
            }
            // Three steppers need ~420dp to sit comfortably in one row; below that they split
            // across two rows (Sets+Reps, then Rest) instead. Measured per-card rather than keyed
            // off posture, since a phone in landscape can already be wide enough on its own.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 24.dp, end = 12.dp),
            ) {
                val setsLabel = stringResource(R.string.routine_editor_sets_label)
                val repsLabel = stringResource(R.string.routine_editor_reps_label)
                val restLabel = stringResource(R.string.routine_editor_rest_label)
                if (maxWidth >= 420.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stepper(
                            setsLabel, exercise.targetSets.toString(),
                            onDec = { onSetsChange(exercise.targetSets - 1) },
                            onInc = { onSetsChange(exercise.targetSets + 1) })
                        Stepper(
                            repsLabel, exercise.targetReps.toString(),
                            onDec = { onRepsChange(exercise.targetReps - 1) },
                            onInc = { onRepsChange(exercise.targetReps + 1) })
                        Stepper(
                            restLabel, formatRest(exercise.targetRestSec),
                            onDec = { onRestChange(exercise.targetRestSec - restStep) },
                            onInc = { onRestChange(exercise.targetRestSec + restStep) })
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Stepper(
                                setsLabel, exercise.targetSets.toString(),
                                onDec = { onSetsChange(exercise.targetSets - 1) },
                                onInc = { onSetsChange(exercise.targetSets + 1) })
                            Stepper(
                                repsLabel, exercise.targetReps.toString(),
                                onDec = { onRepsChange(exercise.targetReps - 1) },
                                onInc = { onRepsChange(exercise.targetReps + 1) })
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Stepper(
                                restLabel, formatRest(exercise.targetRestSec),
                                onDec = { onRestChange(exercise.targetRestSec - restStep) },
                                onInc = { onRestChange(exercise.targetRestSec + restStep) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDec) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(
                        R.string.routine_editor_decrease_description,
                        label
                    )
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 44.dp),
            )
            IconButton(onClick = onInc) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(
                        R.string.routine_editor_increase_description,
                        label
                    )
                )
            }
        }
    }
}

/** Rest seconds → "0:45" / "1:30", or "Off" when zero. */
@Composable
private fun formatRest(totalSec: Int): String =
    if (totalSec <= 0) stringResource(R.string.routine_editor_rest_off) else "%d:%02d".format(
        totalSec / 60,
        totalSec % 60
    )
