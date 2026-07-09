package dev.gouthaman.regimen.ui.active

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.local.entity.CardioEntry
import dev.gouthaman.regimen.data.local.entity.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.ui.routines.ExercisePickerSheet
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun ActiveWorkoutScreen(
    onFinished: (Long) -> Unit,
    onDiscarded: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()

    // Set when the user explicitly cancels an edit (below), so the finished-flag effect doesn't
    // also fire onFinished (which would send them to the Workout Summary screen instead of just
    // closing back out — cancelling an edit restores the original endTime the same way finishing
    // does, so uiState.finished flips true either way).
    var cancellingEdit by remember { mutableStateOf(false) }

    // Navigate reactively off the observed workout state, so ending/discarding from the notification
    // (not just the in-app buttons) moves the screen too. Guarded so each fires once.
    LaunchedEffect(uiState.finished) {
        if (uiState.finished && !cancellingEdit) onFinished(viewModel.workoutId)
    }
    LaunchedEffect(uiState.loaded, uiState.notFound) {
        if (uiState.loaded && uiState.notFound) onDiscarded()
    }

    // Rest-complete + (Phase 3) foreground-service notifications need POST_NOTIFICATIONS on 13+.
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ActiveWorkoutScreen(
        uiState = uiState,
        addableExercises = allExercises,
        rest = rest,
        onUpdateSet = viewModel::updateSet,
        onAddSet = viewModel::addSet,
        onDeleteSet = viewModel::deleteSet,
        onToggleSkip = viewModel::toggleSkip,
        onRemoveExercise = viewModel::removeExercise,
        onAddExercises = viewModel::addExercises,
        onUpdateCardio = viewModel::updateCardio,
        onUpdateNote = viewModel::updateNote,
        onStartRest = viewModel::startRest,
        onAddRestTime = viewModel::addRestTime,
        onStopRest = viewModel::stopRest,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        // Finish/discard only flip the workout state; the LaunchedEffects above handle navigation.
        onFinish = viewModel::finish,
        onDiscard = viewModel::discard,
        // Cancel-edit restores the session to its original finished state (same write finish()
        // does when re-editing) but navigates straight back instead of to Workout Summary.
        onCancelEdit = {
            cancellingEdit = true
            viewModel.finish()
            onDiscarded()
        },
        onCreateCustomExercise = onCreateCustomExercise,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    addableExercises: List<dev.gouthaman.regimen.data.local.entity.Exercise>,
    rest: RestTimerState?,
    onUpdateSet: (SetEntry) -> Unit,
    onAddSet: (Long, SetEntry?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onToggleSkip: (dev.gouthaman.regimen.data.local.entity.WorkoutExercise) -> Unit,
    onRemoveExercise: (dev.gouthaman.regimen.data.local.entity.WorkoutExercise) -> Unit,
    onAddExercises: (List<Long>) -> Unit,
    onUpdateCardio: (CardioEntry) -> Unit,
    onUpdateNote: (String) -> Unit,
    onStartRest: (Long, Int) -> Unit,
    onAddRestTime: (Int) -> Unit,
    onStopRest: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    onCancelEdit: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val isEditing = uiState.isEditingPastSession

    // Session timer: elapsed derives from startTime, so it survives rotation / process death.
    // Not relevant while re-editing a past session — there's no live session to time.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.startTime, isEditing) {
        if (isEditing) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    // Elapsed excludes accumulated pause; while paused it freezes at the moment of pausing.
    val elapsed = when {
        uiState.startTime == 0L -> 0L
        uiState.pausedAt != null -> uiState.pausedAt - uiState.startTime - uiState.accumulatedPausedMs
        else -> now - uiState.startTime - uiState.accumulatedPausedMs
    }.coerceAtLeast(0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.title.ifEmpty { "Workout" })
                        if (uiState.loaded && !uiState.notFound) {
                            Text(
                                when {
                                    isEditing -> "Editing session"
                                    uiState.isPaused -> "${formatElapsed(elapsed)} · Paused"
                                    else -> formatElapsed(elapsed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showDiscard = true }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (isEditing) "Cancel edit" else "Discard",
                        )
                    }
                },
                actions = {
                    if (uiState.loaded && !uiState.notFound) {
                        if (!isEditing) {
                            IconButton(onClick = { if (uiState.isPaused) onResume() else onPause() }) {
                                if (uiState.isPaused) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                                } else {
                                    Icon(Icons.Filled.Pause, contentDescription = "Pause")
                                }
                            }
                        }
                        TextButton(onClick = onFinish) { Text(if (isEditing) "Done" else "Finish") }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.notFound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text("Workout not found") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.exercises.isEmpty()) {
                item {
                    Text(
                        "No exercises yet. Add one to start logging.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onToggleSkip = onToggleSkip,
                    onRemoveExercise = onRemoveExercise,
                    onUpdateCardio = onUpdateCardio,
                    onStartRest = onStartRest,
                    showRestTimer = !isEditing,
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

            item {
                var note by remember(uiState.note) { mutableStateOf(uiState.note) }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        onUpdateNote(it)
                    },
                    label = { Text("Session note") },
                    modifier = Modifier.fillMaxWidth(),
                )
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

    if (rest != null) {
        RestTimerSheet(
            rest = rest,
            onAddTime = onAddRestTime,
            onStop = onStopRest,
        )
    }

    if (showDiscard) {
        if (isEditing) {
            AlertDialog(
                onDismissRequest = { showDiscard = false },
                title = { Text("Cancel edit?") },
                text = {
                    Text(
                        "Changes you've made so far are already saved. This closes editing and " +
                            "returns the session to its original finished state."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscard = false
                        onCancelEdit()
                    }) { Text("Cancel edit") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscard = false }) { Text("Keep editing") }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDiscard = false },
                title = { Text("Discard this workout?") },
                text = { Text("Everything logged in this session will be deleted. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscard = false
                        onDiscard()
                    }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscard = false }) { Text("Keep going") }
                },
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ActiveExercise,
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem,
    onUpdateSet: (SetEntry) -> Unit,
    onAddSet: (Long, SetEntry?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onToggleSkip: (dev.gouthaman.regimen.data.local.entity.WorkoutExercise) -> Unit,
    onRemoveExercise: (dev.gouthaman.regimen.data.local.entity.WorkoutExercise) -> Unit,
    onUpdateCardio: (CardioEntry) -> Unit,
    onStartRest: (Long, Int) -> Unit,
    showRestTimer: Boolean = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (exercise.isSkipped) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (exercise.isStrength) {
                            DropdownMenuItem(
                                text = { Text(if (exercise.isSkipped) "Include" else "Skip") },
                                onClick = {
                                    menuExpanded = false
                                    onToggleSkip(exercise.workoutExercise)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove") },
                            onClick = {
                                menuExpanded = false
                                onRemoveExercise(exercise.workoutExercise)
                            },
                        )
                    }
                }
            }

            when {
                exercise.isSkipped -> Text(
                    "Skipped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                exercise.isStrength -> {
                    exercise.sets.forEach { set ->
                        SetRow(
                            set = set,
                            weightUnit = weightUnit,
                            onUpdate = onUpdateSet,
                            onDelete = { onDeleteSet(set) },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                onAddSet(
                                    exercise.workoutExerciseId,
                                    exercise.sets.lastOrNull()
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("Add set", modifier = Modifier.padding(start = 8.dp))
                        }
                        if (showRestTimer) TextButton(
                            onClick = {
                                onStartRest(
                                    exercise.workoutExerciseId,
                                    exercise.restTargetSec
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null)
                            Text("Rest", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                else -> CardioRow(
                    cardio = exercise.cardio,
                    workoutExerciseId = exercise.workoutExerciseId,
                    distanceUnit = distanceUnit,
                    onUpdate = onUpdateCardio,
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    set: SetEntry,
    weightUnit: UnitSystem,
    onUpdate: (SetEntry) -> Unit,
    onDelete: () -> Unit,
) {
    // All fields are local, seeded once per set id, so incoming flow emissions don't clobber a
    // field mid-edit. Every write rebuilds the whole entry from local state, so editing one field
    // never reverts another that hasn't round-tripped through Room yet.
    var weight by remember(set.id) {
        mutableStateOf(set.weightKg?.let {
            UnitConverter.formatValue(
                UnitConverter.kgToDisplay(
                    it,
                    weightUnit
                )
            )
        } ?: "")
    }
    var reps by remember(set.id) { mutableStateOf(set.reps?.toString() ?: "") }
    var complete by remember(set.id) { mutableStateOf(set.isComplete) }
    // Reflect externally-driven completion (e.g. rest-timer auto-tick) without re-seeding the
    // numeric fields, which must stay as the user typed them.
    LaunchedEffect(set.isComplete) { complete = set.isComplete }

    fun push() = onUpdate(
        set.copy(
            weightKg = weight.toDoubleOrNull()?.let { UnitConverter.displayToKg(it, weightUnit) },
            reps = reps.toIntOrNull(),
            isComplete = complete,
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${set.setNumber}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it; push() },
            label = { Text(UnitConverter.weightLabel(weightUnit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = reps,
            onValueChange = { reps = it; push() },
            label = { Text("Reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = complete,
            onCheckedChange = { complete = it; push() },
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete set")
        }
    }
}

@Composable
private fun CardioRow(
    cardio: CardioEntry?,
    workoutExerciseId: Long,
    distanceUnit: UnitSystem,
    onUpdate: (CardioEntry) -> Unit,
) {
    val base = cardio ?: CardioEntry(workoutExerciseId = workoutExerciseId, durationSec = 0)
    var minutes by remember(cardio?.id) {
        mutableStateOf(if (base.durationSec > 0) (base.durationSec / 60).toString() else "")
    }
    var distance by remember(cardio?.id) {
        mutableStateOf(base.distanceMeters?.let {
            UnitConverter.formatValue(
                UnitConverter.metersToDisplay(
                    it,
                    distanceUnit
                )
            )
        } ?: "")
    }

    fun push() = onUpdate(
        base.copy(
            durationSec = (minutes.toLongOrNull() ?: 0L) * 60,
            distanceMeters = distance.toDoubleOrNull()
                ?.let { UnitConverter.displayToMeters(it, distanceUnit) },
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it; push() },
            label = { Text("Minutes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it; push() },
            label = { Text("Distance (${UnitConverter.distanceLabel(distanceUnit)})") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestTimerSheet(
    rest: RestTimerState,
    onAddTime: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    val remainingMs = (rest.endAtMillis - now).coerceAtLeast(0)
    val remainingSec = ceil(remainingMs / 1000.0).toInt()
    val progress = if (rest.totalSec > 0) (remainingMs / 1000f) / rest.totalSec else 0f

    ModalBottomSheet(onDismissRequest = onStop, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Rest", style = MaterialTheme.typography.titleMedium)
            Text(formatRest(remainingSec), style = MaterialTheme.typography.displayMedium)
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAddTime(-15) }) { Text("−15s") }
                OutlinedButton(onClick = { onAddTime(15) }) { Text("+15s") }
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Skip rest")
            }
        }
    }
}

/** "M:SS". */
private fun formatRest(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)

/** "MM:SS" under an hour, otherwise "H:MM:SS". */
private fun formatElapsed(millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
