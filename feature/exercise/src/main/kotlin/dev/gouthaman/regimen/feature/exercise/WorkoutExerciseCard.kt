package dev.gouthaman.regimen.feature.exercise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.util.UnitConverter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** One exercise in a workout (live or being edited), with its logged data ready to edit. Shared by
 * the live ActiveWorkoutSheet (:app) and EditWorkoutScreen (:feature:history) - the only two
 * places that log/edit sets - via [ExerciseCard] below. [restTargetSec] is unused (defaults to 0)
 * when [ExerciseCard]'s showRestTimer is false, since editing a past session has no rest timer. */
data class WorkoutExerciseRow(
    val workoutExercise: WorkoutExercise,
    val name: String,
    val isStrength: Boolean,
    val equipment: Equipment,
    val isSkipped: Boolean,
    val isDone: Boolean,
    val sets: List<SetEntry>,
    val cardio: CardioEntry?,
    val restTargetSec: Int = 0,
    val notes: String? = null,
    /** Whether the user has tapped the notes icon to reveal a blank field to type into - only
     * matters while [notes] is blank; once it has text the field always shows regardless. */
    val notesToggledOpen: Boolean = false,
) {
    val workoutExerciseId: String get() = workoutExercise.id
}

/** Which body [ExerciseCard] shows - skipped and done are mutually exclusive at the toggle level
 * (see ExerciseCard's header icons), skipped wins if both were ever somehow true. */
private enum class ExerciseCardBody { SKIPPED, DONE, NORMAL }

@Composable
fun ExerciseCard(
    exercise: WorkoutExerciseRow,
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem,
    onUpdateSet: (SetEntry) -> Unit,
    onAddSet: (String, SetEntry?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onAutofillWeight: (String, Double) -> Unit,
    onAutofillReps: (String, Int) -> Unit,
    onToggleSkip: (WorkoutExercise) -> Unit,
    onToggleDone: (WorkoutExercise) -> Unit,
    onUpdateCardio: (CardioEntry) -> Unit,
    onStartRest: (String, Int) -> Unit,
    onToggleNotes: (String) -> Unit,
    onUpdateNotes: (WorkoutExercise, String) -> Unit,
    modifier: Modifier = Modifier,
    showRestTimer: Boolean = true,
    enabled: Boolean = true,
    animateSets: Boolean = true,
) {
    val bodyState = when {
        exercise.isSkipped -> ExerciseCardBody.SKIPPED
        exercise.isDone -> ExerciseCardBody.DONE
        else -> ExerciseCardBody.NORMAL
    }
    val haptics = LocalHapticFeedback.current
    // Cross-fades the container tint on skip/unskip/done (no instant cut). Content color rides
    // along so text/icons keep proper contrast against the tinted (done) background, rather than
    // the default card content color (meant for a plain surface).
    val containerColor by animateColorAsState(
        targetValue = when (bodyState) {
            ExerciseCardBody.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
            ExerciseCardBody.DONE -> MaterialTheme.colorScheme.tertiaryFixedDim
            ExerciseCardBody.NORMAL -> CardDefaults.cardColors().containerColor
        },
        animationSpec = tween(300),
        label = "exerciseCardContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (bodyState == ExerciseCardBody.DONE) {
            MaterialTheme.colorScheme.onTertiaryFixed
        } else {
            CardDefaults.cardColors().contentColor
        },
        animationSpec = tween(300),
        label = "exerciseCardContent",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseIcon(
                    type = if (exercise.isStrength) ExerciseType.STRENGTH else ExerciseType.CARDIO,
                    equipment = exercise.equipment,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (exercise.isStrength) {
                    // Hidden while done - can't skip something already marked done without
                    // reopening it for editing first (Edit clears isDone).
                    if (!exercise.isDone) {
                        IconButton(
                            onClick = {
                                onToggleSkip(exercise.workoutExercise)
                                haptics.performHapticFeedback(
                                    if (exercise.isSkipped) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                            },
                            enabled = enabled
                        ) {
                            AnimatedContent(
                                targetState = exercise.isSkipped,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.6f) + fadeIn())
                                        .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
                                },
                                label = "skipToggleIcon",
                            ) { skipped ->
                                Icon(
                                    if (skipped) Icons.Filled.AddCircleOutline
                                    else Icons.Filled.RemoveCircleOutline,
                                    contentDescription = stringResource(
                                        if (skipped) R.string.workout_include_description else R.string.workout_skip_description,
                                    ),
                                )
                            }
                        }
                    }
                    // Hidden while skipped - same reasoning, mirrored.
                    if (!exercise.isSkipped) {
                        val canMarkDone =
                            exercise.sets.isNotEmpty() && exercise.sets.all { it.isComplete }
                        IconButton(
                            onClick = {
                                onToggleDone(exercise.workoutExercise)
                                haptics.performHapticFeedback(
                                    if (exercise.isDone) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                            },
                            enabled = enabled && (exercise.isDone || canMarkDone),
                        ) {
                            AnimatedContent(
                                targetState = exercise.isDone,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.6f) + fadeIn())
                                        .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
                                },
                                label = "doneToggleIcon",
                            ) { done ->
                                Icon(
                                    if (done) Icons.Filled.Edit else Icons.Filled.CheckCircle,
                                    contentDescription = stringResource(
                                        if (done) R.string.workout_edit_exercise_description else R.string.workout_mark_done_description,
                                    ),
                                )
                            }
                        }
                    }
                }
                // Only while there's nothing to show yet - once notes has text the field below
                // is already visible and can't be hidden by this button (only by clearing it).
                if (bodyState == ExerciseCardBody.NORMAL && exercise.notes.isNullOrBlank()) {
                    IconButton(
                        onClick = { onToggleNotes(exercise.workoutExerciseId) },
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.Filled.Notes,
                            contentDescription = stringResource(R.string.workout_add_notes_description),
                        )
                    }
                }
            }

            // Cross-fades skipped/done labels vs the sets/cardio body, resizing smoothly instead
            // of an abrupt height jump - bodies differ a lot in height (one line vs. full set list).
            AnimatedContent(
                targetState = bodyState,
                transitionSpec = {
                    fadeIn(tween(220)).togetherWith(fadeOut(tween(150)))
                        .using(SizeTransform(clip = false))
                },
                label = "exerciseCardBody",
            ) { state ->
                when (state) {
                    ExerciseCardBody.SKIPPED -> Text(
                        stringResource(R.string.workout_skipped_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    ExerciseCardBody.DONE -> Text(
                        exercise.sets.map { SessionFormat.setLabel(it, weightUnit) }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    ExerciseCardBody.NORMAL -> if (exercise.isStrength) {
                        Column {
                            exercise.sets.forEach { set ->
                                key(set.id) {
                                    AnimatedSetRow(
                                        set = set,
                                        weightUnit = weightUnit,
                                        isBodyweight = exercise.equipment == Equipment.BODYWEIGHT,
                                        enabled = enabled,
                                        animated = animateSets,
                                        onUpdate = onUpdateSet,
                                        onDelete = { onDeleteSet(set) },
                                        onAutofillWeight = { kg -> onAutofillWeight(set.id, kg) },
                                        onAutofillReps = { reps -> onAutofillReps(set.id, reps) },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        onAddSet(
                                            exercise.workoutExerciseId,
                                            exercise.sets.lastOrNull()
                                        )
                                    },
                                    enabled = enabled,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Text(
                                        stringResource(R.string.workout_add_set_button),
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                            // Own full-width row + filled-tonal styling (not a TextButton sharing
                            // a row with Add set) - bigger, easier-to-hit target after gym-use
                            // feedback that the shared-row TextButton was fiddly to tap.
                            if (showRestTimer) FilledTonalButton(
                                onClick = {
                                    onStartRest(
                                        exercise.workoutExerciseId,
                                        exercise.restTargetSec
                                    )
                                },
                                enabled = enabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null)
                                Text(
                                    stringResource(R.string.workout_rest_label),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    } else {
                        CardioRow(
                            cardio = exercise.cardio,
                            workoutExerciseId = exercise.workoutExerciseId,
                            distanceUnit = distanceUnit,
                            enabled = enabled,
                            onUpdate = onUpdateCardio,
                        )
                    }
                }
            }
            val showNotes = if (bodyState == ExerciseCardBody.NORMAL) {
                exercise.notesToggledOpen || !exercise.notes.isNullOrBlank()
            } else {
                !exercise.notes.isNullOrBlank()
            }
            if (showNotes && bodyState == ExerciseCardBody.NORMAL) {
                var notesText by remember(exercise.workoutExerciseId, exercise.notes) {
                    mutableStateOf(exercise.notes.orEmpty())
                }
                OutlinedTextField(
                    value = notesText,
                    onValueChange = {
                        notesText = it
                        onUpdateNotes(exercise.workoutExercise, it)
                    },
                    label = { Text(stringResource(R.string.workout_notes_label)) },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            } else if (showNotes) {
                // Skipped/done - read-only, matches the collapsed summary's plain-text styling.
                Text(
                    exercise.notes.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Wraps [SetRow] with an enter animation for new sets and a delayed exit for deletion - the real
 * [onDelete] fires only after the shrink/fade finishes, since [AnimatedVisibility] needs the
 * composable to stay mounted for its exit duration. Set [animated] to false to skip both and fire
 * [onDelete] immediately.
 *
 * [animated] is read directly into `visible`'s initial value and the enter/exit specs, rather than
 * branching into a different composable structure - toggling it later (e.g. an external "don't
 * animate yet" window elapsing) would otherwise reset this row's remembered state, since that
 * subtree wouldn't have existed in composition while animated was false.
 */
@Composable
private fun AnimatedSetRow(
    set: SetEntry,
    weightUnit: UnitSystem,
    isBodyweight: Boolean,
    enabled: Boolean,
    onUpdate: (SetEntry) -> Unit,
    onDelete: () -> Unit,
    onAutofillWeight: (Double) -> Unit,
    onAutofillReps: (Int) -> Unit,
    animated: Boolean = true,
) {
    var visible by remember { mutableStateOf(!animated) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(removing) {
        if (removing) {
            if (animated) delay(SetRowExitDurationMs.milliseconds)
            onDelete()
        }
    }

    AnimatedVisibility(
        visible = visible && !removing,
        enter = if (animated) fadeIn() + expandVertically() else EnterTransition.None,
        exit = if (animated) {
            fadeOut(tween(SetRowExitDurationMs.toInt())) +
                    shrinkVertically(tween(SetRowExitDurationMs.toInt()))
        } else {
            ExitTransition.None
        },
    ) {
        SetRow(
            set = set,
            weightUnit = weightUnit,
            isBodyweight = isBodyweight,
            enabled = enabled,
            onUpdate = onUpdate,
            onDelete = { removing = true },
            onAutofillWeight = onAutofillWeight,
            onAutofillReps = onAutofillReps,
        )
    }
}

private const val SetRowExitDurationMs = 220L

@Composable
private fun SetRow(
    set: SetEntry,
    weightUnit: UnitSystem,
    isBodyweight: Boolean,
    enabled: Boolean,
    onUpdate: (SetEntry) -> Unit,
    onDelete: () -> Unit,
    onAutofillWeight: (Double) -> Unit,
    onAutofillReps: (Int) -> Unit,
) {
    // Fields are local (seeded once per set id) rather than reading `set` directly, so a
    // same-row round-trip through Room mid-edit can't clobber what's being typed; every write
    // rebuilds the entry from local state, so editing one field can't revert another not yet
    // round-tripped. Resynced from `set` while unfocused below, so an external write (e.g.
    // another row's autofill-on-blur) still reaches an already-composed, non-active row.
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
    val haptics = LocalHapticFeedback.current
    // Reflects externally-driven completion (rest-timer auto-tick) without re-seeding the numeric fields the user typed.
    LaunchedEffect(set.isComplete) { complete = set.isComplete }

    fun push() = onUpdate(
        set.copy(
            weightKg = if (isBodyweight) null else {
                weight.toDoubleOrNull()?.let { UnitConverter.displayToKg(it, weightUnit) }
            },
            reps = reps.toIntOrNull(),
            isComplete = complete,
        )
    )

    // Trims the raw typed text to its canonical formatting (e.g. "10.00" -> "10") and, if it's a
    // real value, fills every later empty set in the same column (item 3 of the punch list).
    var weightWasFocused by remember(set.id) { mutableStateOf(false) }
    var repsWasFocused by remember(set.id) { mutableStateOf(false) }
    // Picks up an autofill written into THIS row from another row's blur - guarded by "not
    // currently focused" so a same-row round-trip mid-keystroke (this row's own push()
    // reflected back through Room) can't clobber what's still being typed here.
    LaunchedEffect(set.weightKg) {
        if (!weightWasFocused) {
            weight = set.weightKg?.let {
                UnitConverter.formatValue(UnitConverter.kgToDisplay(it, weightUnit))
            } ?: ""
        }
    }
    LaunchedEffect(set.reps) {
        if (!repsWasFocused) reps = set.reps?.toString() ?: ""
    }

    // A set can only be checked complete once every numeric field it shows is actually filled in
    // (unchecking is always allowed, no validation). Once checked, the fields lock - uncheck to
    // edit them again.
    val fieldsValid =
        (isBodyweight || weight.toDoubleOrNull() != null) && reps.toIntOrNull() != null
    val fieldsEnabled = enabled && !complete

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
        if (!isBodyweight) {
            OutlinedTextField(
                value = weight,
                // Discards anything that isn't a digit or a decimal point on every keystroke.
                onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }; push() },
                label = { Text(UnitConverter.weightLabel(weightUnit).text()) },
                singleLine = true,
                enabled = fieldsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (weightWasFocused && !focusState.isFocused) {
                            val parsed = weight.toDoubleOrNull()
                            weight = parsed?.let { UnitConverter.formatValue(it) } ?: ""
                            if (parsed != null) {
                                onAutofillWeight(UnitConverter.displayToKg(parsed, weightUnit))
                            }
                        }
                        weightWasFocused = focusState.isFocused
                    },
            )
        }
        OutlinedTextField(
            value = reps,
            // Reps are a whole-number count, so only digits survive each keystroke (no period).
            onValueChange = { reps = it.filter { c -> c.isDigit() }; push() },
            label = { Text(stringResource(R.string.workout_reps_label)) },
            singleLine = true,
            enabled = fieldsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (repsWasFocused && !focusState.isFocused) {
                        val parsed = reps.toIntOrNull()
                        reps = parsed?.toString() ?: ""
                        if (parsed != null) onAutofillReps(parsed)
                    }
                    repsWasFocused = focusState.isFocused
                },
        )
        Checkbox(
            checked = complete,
            onCheckedChange = {
                complete = it
                push()
                // Only on check, not uncheck - a tick both ways would read as a buzzer during a
                // quick fix-a-mistake toggle.
                if (it) haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            },
            enabled = enabled && (complete || fieldsValid),
        )
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.workout_delete_set_description)
            )
        }
    }
}

@Composable
private fun CardioRow(
    cardio: CardioEntry?,
    workoutExerciseId: String,
    distanceUnit: UnitSystem,
    enabled: Boolean,
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
            label = { Text(stringResource(R.string.workout_minutes_label)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = distance,
            onValueChange = { distance = it; push() },
            label = {
                Text(
                    stringResource(
                        R.string.workout_distance_label,
                        UnitConverter.distanceLabel(distanceUnit).text()
                    )
                )
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}
