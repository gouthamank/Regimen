package dev.gouthaman.regimen.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.designsystem.dialog.ExercisePickerSheet
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.feature.exercise.ExerciseCard

@Composable
fun EditWorkoutScreen(
    onFinished: () -> Unit,
    onDiscarded: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()

    if (uiState.loaded && uiState.notFound) {
        onDiscarded()
        return
    }

    EditWorkoutScreen(
        uiState = uiState,
        addableExercises = allExercises,
        onUpdateSet = viewModel::updateSet,
        onAddSet = viewModel::addSet,
        onDeleteSet = viewModel::deleteSet,
        onAutofillWeight = viewModel::autofillWeightBelow,
        onAutofillReps = viewModel::autofillRepsBelow,
        onToggleSkip = viewModel::toggleSkip,
        onToggleDone = viewModel::toggleDone,
        onAddExercises = viewModel::addExercises,
        onUpdateCardio = viewModel::updateCardio,
        onUpdateNote = viewModel::updateNote,
        onDone = {
            viewModel.doneEditing()
            onFinished()
        },
        onCancelEdit = {
            viewModel.doneEditing()
            onDiscarded()
        },
        onCreateCustomExercise = onCreateCustomExercise,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(
    uiState: EditWorkoutUiState,
    addableExercises: List<Exercise>,
    onUpdateSet: (SetEntry) -> Unit,
    onAddSet: (Long, SetEntry?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onAutofillWeight: (Long, Long, Double) -> Unit,
    onAutofillReps: (Long, Long, Int) -> Unit,
    onToggleSkip: (WorkoutExercise) -> Unit,
    onToggleDone: (WorkoutExercise) -> Unit,
    onAddExercises: (List<Long>) -> Unit,
    onUpdateCardio: (CardioEntry) -> Unit,
    onUpdateNote: (String) -> Unit,
    onDone: () -> Unit,
    onCancelEdit: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCancelEdit by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        when {
                            uiState.routineName != null -> uiState.routineName
                            uiState.loaded -> stringResource(R.string.edit_workout_quick_workout_fallback)
                            else -> stringResource(R.string.edit_workout_title_fallback)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showCancelEdit = true }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.edit_workout_cancel_edit_action),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.edit_workout_done_button),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
            Modifier
                .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                .fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = contentModifier) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.exercises.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.edit_workout_no_exercises_yet),
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }

                    items(uiState.exercises, key = { it.workoutExerciseId }) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            weightUnit = uiState.weightUnit,
                            distanceUnit = uiState.distanceUnit,
                            onUpdateSet = onUpdateSet,
                            onAddSet = onAddSet,
                            onDeleteSet = onDeleteSet,
                            onAutofillWeight = { setId, kg ->
                                onAutofillWeight(exercise.workoutExerciseId, setId, kg)
                            },
                            onAutofillReps = { setId, reps ->
                                onAutofillReps(exercise.workoutExerciseId, setId, reps)
                            },
                            onToggleSkip = onToggleSkip,
                            onToggleDone = onToggleDone,
                            onUpdateCardio = onUpdateCardio,
                            onStartRest = { _, _ -> },
                            showRestTimer = false,
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { showPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(
                                stringResource(R.string.edit_workout_add_exercise_button),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    item {
                        var note by remember(uiState.note) { mutableStateOf(uiState.note) }
                        OutlinedTextField(
                            value = note,
                            onValueChange = {
                                note = it
                                onUpdateNote(it)
                            },
                            label = { Text(stringResource(R.string.edit_workout_session_note_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            exercises = addableExercises,
            onConfirm = {
                showPicker = false
                onAddExercises(it)
            },
            onDismiss = { showPicker = false },
            onCreateCustom = {
                showPicker = false
                onCreateCustomExercise()
            },
        )
    }

    if (showCancelEdit) {
        ConfirmDialog(
            title = stringResource(R.string.edit_workout_cancel_edit_dialog_title),
            text = stringResource(R.string.edit_workout_cancel_edit_dialog_text),
            confirmLabel = stringResource(R.string.edit_workout_cancel_edit_action),
            onConfirm = {
                showCancelEdit = false
                onCancelEdit()
            },
            dismissLabel = stringResource(R.string.edit_workout_keep_editing_button),
            onDismiss = { showCancelEdit = false },
            destructive = true,
        )
    }
}
