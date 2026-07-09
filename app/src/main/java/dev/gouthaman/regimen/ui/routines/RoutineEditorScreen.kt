package dev.gouthaman.regimen.ui.routines

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RoutineEditorScreen(
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
        onBack = onBack,
        onNameChange = viewModel::setName,
        onAddExercises = viewModel::addExercises,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    uiState: RoutineEditorUiState,
    restStep: Int,
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
    var showPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit routine" else "New routine") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledIconButton(onClick = onSave, enabled = uiState.canSave) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add"
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        // Exactly one leading item (the name field) sits above the exercise list in this
        // LazyColumn, so a dragged item's *global* index (what DragDropState tracks, matched
        // against LazyListState's layout info) is always the exercise's local index + 1. The
        // empty-state text item never coexists with actual exercises, so it doesn't affect this.
        val leadingItems = 1

        // Working copy the drag reorders in place, synchronously — swaps go straight here, not
        // through the ViewModel per-swap, which lagged a frame behind the LazyColumn's layout and
        // made the drag look like it stalled the moment two items swapped. Re-synced from the
        // source whenever it changes and we're not mid-drag; the final order is committed to the
        // ViewModel once, on drop.
        val working = remember { mutableStateListOf<EditorExercise>() }
        val dragState = rememberDragDropState(
            listState,
            // Restricts valid drag starts/targets to the exercise rows themselves — otherwise a
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text("Routine name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (working.isEmpty()) {
                item {
                    Text(
                        "Add strength exercises to build this routine.",
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
                    Text("Add exercise", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = uiState.addableExercises,
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
                    contentDescription = "Reorder ${exercise.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(horizontal = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        exercise.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
            // Data entries split across two rows (Sets+Reps, then Rest) instead of cramming all
            // three steppers into one — each gets more breathing room.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 24.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Stepper(
                    "Sets", exercise.targetSets.toString(),
                    onDec = { onSetsChange(exercise.targetSets - 1) },
                    onInc = { onSetsChange(exercise.targetSets + 1) })
                Stepper(
                    "Reps", exercise.targetReps.toString(),
                    onDec = { onRepsChange(exercise.targetReps - 1) },
                    onInc = { onRepsChange(exercise.targetReps + 1) })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 24.dp, end = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Stepper(
                    "Rest", formatRest(exercise.targetRestSec),
                    onDec = { onRestChange(exercise.targetRestSec - restStep) },
                    onInc = { onRestChange(exercise.targetRestSec + restStep) })
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
                    contentDescription = "Decrease $label"
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
                    contentDescription = "Increase $label"
                )
            }
        }
    }
}

/** Rest seconds → "0:45" / "1:30", or "Off" when zero. */
private fun formatRest(totalSec: Int): String =
    if (totalSec <= 0) "Off" else "%d:%02d".format(totalSec / 60, totalSec % 60)
