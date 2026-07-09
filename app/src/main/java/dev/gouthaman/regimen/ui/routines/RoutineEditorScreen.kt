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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
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
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
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
        LazyColumn(
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

            if (uiState.exercises.isEmpty()) {
                item {
                    Text(
                        "Add strength exercises to build this routine.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(uiState.exercises, key = { _, e -> e.exerciseId }) { index, exercise ->
                ExerciseEditorCard(
                    exercise = exercise,
                    isFirst = index == 0,
                    isLast = index == uiState.exercises.lastIndex,
                    restStep = restStep,
                    onRemove = { onRemove(index) },
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) },
                    onSetsChange = { onSetsChange(index, it) },
                    onRepsChange = { onRepsChange(index, it) },
                    onRestChange = { onRestChange(index, it) },
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
    isFirst: Boolean,
    isLast: Boolean,
    restStep: Int,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSetsChange: (Int) -> Unit,
    onRepsChange: (Int) -> Unit,
    onRestChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
                end = 4.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        exercise.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stepper(
                    "Sets", exercise.targetSets.toString(),
                    onDec = { onSetsChange(exercise.targetSets - 1) },
                    onInc = { onSetsChange(exercise.targetSets + 1) })
                Stepper(
                    "Reps", exercise.targetReps.toString(),
                    onDec = { onRepsChange(exercise.targetReps - 1) },
                    onInc = { onRepsChange(exercise.targetReps + 1) })
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
