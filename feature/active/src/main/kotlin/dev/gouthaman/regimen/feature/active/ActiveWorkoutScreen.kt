package dev.gouthaman.regimen.feature.active

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.SessionFormat
import dev.gouthaman.regimen.common.text
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.designsystem.dialog.ExercisePickerSheet
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.util.UnitConverter
import dev.gouthaman.regimen.feature.exercise.ExerciseIcon
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigates off the observed workout state rather than directly off the Finish button tap, so
    // any path that flips status to COMPLETE also moves the screen. Skipped while editing a past
    // session - endTime never changes there (isEditingPastSession), so Done/Cancel-edit navigate
    // directly instead (below).
    LaunchedEffect(uiState.finished, uiState.isEditingPastSession) {
        if (uiState.finished && !uiState.isEditingPastSession) onFinished(viewModel.workoutId)
    }
    LaunchedEffect(uiState.loaded, uiState.notFound) {
        if (uiState.loaded && uiState.notFound) onDiscarded()
    }
    val restSetInvalidMessage = stringResource(R.string.workout_rest_set_invalid_snackbar)
    LaunchedEffect(Unit) {
        viewModel.restSetInvalidEvents.collect {
            snackbarHostState.showSnackbar(
                restSetInvalidMessage
            )
        }
    }

    ActiveWorkoutScreen(
        uiState = uiState,
        addableExercises = allExercises,
        rest = rest,
        snackbarHostState = snackbarHostState,
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
        onStartRest = viewModel::startRest,
        onAddRestTime = viewModel::addRestTime,
        onStopRest = viewModel::stopRest,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        // Discard only flips state; the LaunchedEffect above navigates. Finish is edit-aware: a
        // live workout goes through finishWorkoutUseCase + that same LaunchedEffect; an edit
        // flips back out of EDITING (endTime is untouched) and navigates to the summary directly.
        onFinish = {
            if (uiState.isEditingPastSession) {
                viewModel.doneEditing()
                onFinished(viewModel.workoutId)
            } else {
                viewModel.finish()
            }
        },
        onDiscard = viewModel::discard,
        // Cancel-edit flips back out of EDITING (editing doesn't touch endTime) and navigates
        // straight back rather than to Workout Summary.
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
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    addableExercises: List<Exercise>,
    rest: RestTimerState?,
    snackbarHostState: SnackbarHostState,
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
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val isEditing = uiState.isEditingPastSession
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Ephemeral (item 10) - resets every time this screen is (re)opened, not a saved preference.
    var keepScreenOn by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Elapsed derives from startTime (survives rotation/process death); unused while re-editing a past session.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.startTime, isEditing) {
        if (isEditing) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }
    // Elapsed excludes accumulated pause; while paused it freezes at the moment of pausing.
    val elapsed = when {
        uiState.startTime == 0L -> 0L
        uiState.status == WorkoutStatus.PAUSED && uiState.pausedAt != null ->
            uiState.pausedAt - uiState.startTime - uiState.accumulatedPausedMs

        else -> now - uiState.startTime - uiState.accumulatedPausedMs
    }.coerceAtLeast(0)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        snackbarHost = {
            // The floating ActiveWorkoutToolbar (Pause/Resume/Finish) isn't a Scaffold bottomBar
            // (it's positioned BottomCenter inside the content Box, over the scrollable list), so
            // the default snackbar placement doesn't know to avoid it - pad above its footprint
            // (16.dp bottom padding + 64.dp height = 80.dp) with a little breathing room.
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 96.dp))
        },
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        when {
                            uiState.routineName != null -> uiState.routineName
                            uiState.loaded -> stringResource(R.string.workout_quick_workout_fallback)
                            else -> stringResource(R.string.workout_title_fallback)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showDiscard = true }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(
                                if (isEditing) R.string.workout_cancel_edit_action else R.string.workout_discard_action,
                            ),
                        )
                    }
                },
                actions = {
                    // Ephemeral (item 10) - resets every time this screen is (re)opened, not a
                    // saved preference. Tinted primary while on, like a lit toggle.
                    IconButton(onClick = { keepScreenOn = !keepScreenOn }) {
                        Icon(
                            Icons.Filled.Coffee,
                            contentDescription = stringResource(
                                if (keepScreenOn) {
                                    R.string.workout_allow_screen_sleep_description
                                } else {
                                    R.string.workout_keep_screen_on_description
                                },
                            ),
                            tint = if (keepScreenOn) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            },
                        )
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
            ) { Text(stringResource(R.string.workout_not_found)) }
            return@Scaffold
        }

        // BookOrExpanded caps and centers content at the same 600dp breakpoint as other
        // LazyColumn-of-cards screens (Routine Editor, Session Detail, Measurement Detail);
        // Compact/Tabletop stay full-bleed. The floating toolbar shares this inner Box so it's
        // capped with the list, same convention as RegimenNavHost + WorkoutInProgressBanner. No
        // Onboarding-style hinge split for Tabletop: the toolbar anchors to the bottom edge
        // regardless of content, same reasoning as the bottom nav bar in RegimenApp.kt.
        val focusManager = LocalFocusManager.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Taps that land on a button/text field/checkbox are consumed by that element's
                // own pointer input before this ever sees them, so this only fires for blank
                // space (card backgrounds, padding, labels) - exactly "anywhere that isn't one
                // of those."
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
            Box(modifier = contentModifier) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra bottom inset so the last item clears the floating toolbar instead of sitting under it.
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.exercises.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.workout_no_exercises_yet),
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
                            onAutofillWeight = { setId, kg ->
                                onAutofillWeight(exercise.workoutExerciseId, setId, kg)
                            },
                            onAutofillReps = { setId, reps ->
                                onAutofillReps(exercise.workoutExerciseId, setId, reps)
                            },
                            onToggleSkip = onToggleSkip,
                            onToggleDone = onToggleDone,
                            onUpdateCardio = onUpdateCardio,
                            onStartRest = onStartRest,
                            showRestTimer = !isEditing,
                            enabled = !uiState.isPaused,
                            // Animates a newly-added exercise's appearance (also reorder/removal, though neither happens here yet).
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { showPicker = true },
                            enabled = !uiState.isPaused,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(
                                stringResource(R.string.workout_add_exercise_button),
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
                            label = { Text(stringResource(R.string.workout_session_note_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (uiState.loaded && !uiState.notFound) {
                    ActiveWorkoutToolbar(
                        isEditing = isEditing,
                        isPaused = uiState.isPaused,
                        elapsed = elapsed,
                        onPause = onPause,
                        onResume = onResume,
                        onFinish = { if (isEditing) onFinish() else showFinishConfirm = true },
                        // No fillMaxWidth/height here - the pill sizes itself (full-width while
                        // running/editing, a compact wrap-content FAB while paused) and animates
                        // between the two via its own AnimatedContent size transform.
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    )
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

    if (rest != null) {
        RestTimerSheet(
            rest = rest,
            onAddTime = onAddRestTime,
            onStop = onStopRest,
        )
    }

    if (showDiscard) {
        if (isEditing) {
            ConfirmDialog(
                title = stringResource(R.string.workout_cancel_edit_dialog_title),
                text = stringResource(R.string.workout_cancel_edit_dialog_text),
                confirmLabel = stringResource(R.string.workout_cancel_edit_action),
                onConfirm = {
                    showDiscard = false
                    onCancelEdit()
                },
                dismissLabel = stringResource(R.string.workout_keep_editing_button),
                onDismiss = { showDiscard = false },
                destructive = true,
            )
        } else {
            ConfirmDialog(
                title = stringResource(R.string.workout_discard_dialog_title),
                text = stringResource(R.string.workout_discard_dialog_text),
                confirmLabel = stringResource(R.string.workout_discard_action),
                onConfirm = {
                    showDiscard = false
                    onDiscard()
                },
                dismissLabel = stringResource(R.string.workout_keep_going_button),
                onDismiss = { showDiscard = false },
                destructive = true,
            )
        }
    }

    if (showFinishConfirm) {
        // "Complete" per exercise: skipped, explicitly marked done, or (for strength exercises)
        // every logged set ticked - a cardio exercise with no sets to tick only counts via
        // isSkipped/isDone.
        val allExercisesComplete = uiState.exercises.all { exercise ->
            exercise.isSkipped || exercise.isDone ||
                    (exercise.sets.isNotEmpty() && exercise.sets.all { it.isComplete })
        }
        ConfirmDialog(
            title = stringResource(
                if (allExercisesComplete) {
                    R.string.workout_finish_dialog_title
                } else {
                    R.string.workout_finish_dialog_title_incomplete
                },
            ),
            text = stringResource(
                if (allExercisesComplete) {
                    R.string.workout_finish_dialog_text
                } else {
                    R.string.workout_finish_dialog_text_incomplete
                },
            ),
            confirmLabel = stringResource(R.string.workout_finish_action),
            onConfirm = {
                showFinishConfirm = false
                onFinish()
            },
            dismissLabel = stringResource(R.string.workout_keep_going_button),
            onDismiss = { showFinishConfirm = false },
            // Green/positive only when everything's actually logged - otherwise a neutral color
            // plus a three-second delay before Finish becomes tappable, so an incomplete finish
            // takes a deliberate beat rather than a reflexive double-tap.
            positive = allExercisesComplete,
            confirmEnableDelayMillis = if (allExercisesComplete) 0L else 3000L,
        )
    }
}

/**
 * Fits a [Morph]'s interpolated outline, at [progress], to the actual layout bounds - rather than
 * assuming the morph's own coordinate space already spans the target size (as the fixed-size
 * MaterialShapes presets assume) - so the two end shapes can live in different coordinate spaces
 * (a wide rectangle vs. a square-normalized preset) without either one stretching.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class ToolbarMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress = progress)
        val bounds = path.getBounds()
        path.transform(
            Matrix().apply {
                scale(x = size.width / bounds.width, y = size.height / bounds.height)
            }
        )
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

/**
 * The session's prominent controls (timer, Pause/Resume, Finish), pulled out of the top bar so it
 * stays title-only. A plain [Surface] pill (shadow+clip+fill), not the alpha
 * `HorizontalFloatingToolbar` API - same building blocks as a FAB, which reads correctly in dark
 * theme. Tinted with the primary color; paused swaps to the secondary Fixed pairing. Pausing
 * collapses the whole pill down to a compact "Resume" FAB (Finish isn't reachable while paused -
 * resume first) via [AnimatedContent]'s size transform, rather than animating individual buttons'
 * widths within a fixed-size bar - the previous approach had two adjacent elements independently
 * resizing/fading, which never quite read as one coordinated motion. Collapses to a status label +
 * Done while editing a past session.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveWorkoutToolbar(
    isEditing: Boolean,
    isPaused: Boolean,
    elapsed: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A real Morph (not a hard shape swap) between the wide running pill and the compact paused
    // Slanted shape. The active polygon is built directly in a wide rectangle's own coordinate
    // space (not one of the square-normalized MaterialShapes presets) so its corner rounding
    // - matching the RoundedCornerShape(percent = 30) look this replaced - doesn't get stretched
    // when Morph interpolates toward Slanted's near-square space; ToolbarMorphShape below fits
    // each interpolated frame to the box's actual bounds rather than assuming a fixed scale.
    val activeToolbarPolygon = remember {
        RoundedPolygon.rectangle(width = 6f, height = 1f, rounding = CornerRounding(radius = 0.4f))
    }
    val toolbarMorph = remember(activeToolbarPolygon) {
        Morph(start = activeToolbarPolygon, end = MaterialShapes.Slanted)
    }
    val toolbarShapeProgress by animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "toolbarShapeMorph",
    )
    // Paused swaps to the secondary Fixed pairing - a real container/content pair keeps contrast
    // guaranteed in both light/dark (a lerp toward surfaceDim, tried earlier, drifted away from
    // the tone onPrimaryFixed was designed to contrast against and read as illegible).
    val targetColor = if (isPaused) {
        MaterialTheme.colorScheme.secondaryFixedDim
    } else {
        MaterialTheme.colorScheme.primaryFixedDim
    }
    val targetContentColor = if (isPaused) {
        MaterialTheme.colorScheme.onSecondaryFixed
    } else {
        MaterialTheme.colorScheme.onPrimaryFixed
    }
    val baseColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(420))
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(420)
    )

    // "Live" cue while counting: breathing dot + ambient glow; absent once paused (that whole
    // branch of content goes away) or editing.
    val isRunning = !isEditing && !isPaused
    val breathe by rememberInfiniteTransition(label = "timerBreathe").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Presses the whole pill down slightly on touch-down (released/cancelled snaps back).
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "toolbarPress",
    )
    val indication = LocalIndication.current
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = contentColor,
        contentColor = baseColor,
    )
    val haptics = LocalHapticFeedback.current
    val hapticPause = {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
        onPause()
    }
    val hapticResume = {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        onResume()
    }

    val toolbarShape = remember(toolbarMorph, toolbarShapeProgress) {
        ToolbarMorphShape(toolbarMorph, toolbarShapeProgress)
    }
    Box(
        modifier = modifier
            .scale(pressScale)
            .shadow(elevation = 8.dp, shape = toolbarShape)
            .clip(toolbarShape)
            .let {
                // Tapping anywhere on the pill also triggers Pause/Resume (mini-player pattern) - disabled
                // while editing; Finish's own click still wins as the nested target.
                if (isEditing) it else it.clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = { if (isPaused) hapticResume() else hapticPause() },
                )
            }
            .background(baseColor)
            .drawBehind {
                if (isRunning) {
                    // Ambient "breathing" glow signals the timer is live.
                    drawRect(color = contentColor.copy(alpha = 0.1f * breathe))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPaused,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(310, delayMillis = 90)
                ))
                    .togetherWith(
                        fadeOut(tween(140)) + scaleOut(
                            targetScale = 0.92f,
                            animationSpec = tween(220)
                        )
                    )
                    .using(SizeTransform(clip = false) { _, _ ->
                        tween(
                            420,
                            easing = FastOutSlowInEasing
                        )
                    })
            },
            label = "toolbarShape",
        ) { paused ->
            if (paused) {
                PausedToolbarContent(elapsed = elapsed, contentColor = contentColor)
            } else {
                RunningToolbarContent(
                    isEditing = isEditing,
                    elapsed = elapsed,
                    contentColor = contentColor,
                    breathe = breathe,
                    buttonColors = buttonColors,
                    onPause = hapticPause,
                    onFinish = onFinish,
                )
            }
        }
    }
}

/** Wide pill: elapsed time (+ live pulse dot), Pause and Finish - shown while running or editing. */
@Composable
private fun RunningToolbarContent(
    isEditing: Boolean,
    elapsed: Long,
    contentColor: Color,
    breathe: Float,
    buttonColors: IconButtonColors,
    onPause: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (isEditing) {
                    stringResource(R.string.workout_editing_session_status)
                } else {
                    formatElapsed(elapsed)
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
            if (!isEditing) {
                // Live pulse dot: breathes while running.
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.4f + 0.6f * breathe)),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isEditing) {
                FilledIconButton(onClick = onPause, colors = buttonColors) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.workout_pause_description),
                    )
                }
            }
            FilledIconButton(onClick = onFinish, colors = buttonColors) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(
                        if (isEditing) R.string.workout_done_description else R.string.workout_finish_action,
                    ),
                )
            }
        }
    }
}

/** Compact "Resume" FAB the pill collapses to while paused - no Finish here; resume first. Tapping
 * anywhere on the pill (see [ActiveWorkoutToolbar]) resumes, so this itself isn't its own button. */
@Composable
private fun PausedToolbarContent(elapsed: Long, contentColor: Color) {
    Row(
        modifier = Modifier
            .wrapContentWidth()
            .height(64.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = contentColor)
        Column {
            Text(
                stringResource(R.string.workout_resume_description),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
            Text(
                formatElapsed(elapsed),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

/** Which body [ExerciseCard] shows - skipped and done are mutually exclusive at the toggle level
 * (see ExerciseCard's header icons), skipped wins if both were ever somehow true. */
private enum class ExerciseCardBody { SKIPPED, DONE, NORMAL }

@Composable
private fun ExerciseCard(
    exercise: ActiveExercise,
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem,
    onUpdateSet: (SetEntry) -> Unit,
    onAddSet: (Long, SetEntry?) -> Unit,
    onDeleteSet: (SetEntry) -> Unit,
    onAutofillWeight: (Long, Double) -> Unit,
    onAutofillReps: (Long, Int) -> Unit,
    onToggleSkip: (WorkoutExercise) -> Unit,
    onToggleDone: (WorkoutExercise) -> Unit,
    onUpdateCardio: (CardioEntry) -> Unit,
    onStartRest: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    showRestTimer: Boolean = true,
    enabled: Boolean = true,
) {
    val bodyState = when {
        exercise.isSkipped -> ExerciseCardBody.SKIPPED
        exercise.isDone -> ExerciseCardBody.DONE
        else -> ExerciseCardBody.NORMAL
    }
    val haptics = LocalHapticFeedback.current
    // Cross-fades the container tint on skip/unskip/done (no instant cut), same duration as the toolbar's pause/resume tint fade.
    // Content color rides along so text/icons keep proper contrast against the tinted (done)
    // background, rather than the default card content color (meant for a plain surface).
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
                            // Same scale+fade swap the toolbar uses for its pause/resume icon.
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
        }
    }
}

/**
 * Wraps [SetRow] with an enter animation for new sets and a delayed exit for deletion - the real
 * [onDelete] (which removes the set from the backing list) fires only after the shrink/fade
 * finishes, since [AnimatedVisibility] needs the composable to stay mounted for its exit
 * duration ([SetRowExitDurationMs]).
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
) {
    var visible by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(removing) {
        if (removing) {
            delay(SetRowExitDurationMs.milliseconds)
            onDelete()
        }
    }

    AnimatedVisibility(
        visible = visible && !removing,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut(tween(SetRowExitDurationMs.toInt())) +
                shrinkVertically(tween(SetRowExitDurationMs.toInt())),
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
    workoutExerciseId: Long,
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
            delay(200.milliseconds)
        }
    }
    val remainingMs = (rest.endAtMillis - now).coerceAtLeast(0)
    val remainingSec = ceil(remainingMs / 1000.0).toInt()
    val progress = if (rest.totalSec > 0) (remainingMs / 1000f) / rest.totalSec else 0f

    // Cross-fades to a more prominent color in the last 15s so the countdown reads as urgent
    // right before rest ends, instead of staying visually identical the whole way down.
    val timerColor by animateColorAsState(
        targetValue = if (remainingSec <= 15) MaterialTheme.colorScheme.error else LocalContentColor.current,
        animationSpec = tween(400),
        label = "restTimerColor",
    )

    // Undismissable except via the explicit "Skip rest" button below: no scrim-tap/back-press
    // dismiss, no swipe-to-dismiss, no drag handle (there's nothing left to drag for).
    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.workout_rest_label),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                formatRest(remainingSec),
                style = MaterialTheme.typography.displayMedium,
                color = timerColor,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAddTime(-15) }) { Text(stringResource(R.string.workout_rest_minus_15s)) }
                OutlinedButton(onClick = { onAddTime(15) }) { Text(stringResource(R.string.workout_rest_plus_15s)) }
            }
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_skip_rest_button))
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
