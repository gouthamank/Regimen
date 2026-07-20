package dev.gouthaman.regimen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapTo
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.designsystem.dialog.ExercisePickerSheet
import dev.gouthaman.regimen.feature.active.ActiveWorkoutViewModel
import dev.gouthaman.regimen.feature.active.RestTimerState
import dev.gouthaman.regimen.feature.exercise.ExerciseCard
import dev.gouthaman.regimen.navigation.EditExerciseRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal enum class WorkoutSheetValue { Collapsed, Expanded }

// internal, not private - RegimenApp reads this to reserve the same amount of bottom clearance in
// every screen's content so the collapsed banner docks below it instead of overlapping it.
internal val CollapsedHeight = 72.dp
private val CollapsedCornerRadius = 16.dp

/** Hoisted control surface for [ActiveWorkoutSheet] - lets callers outside the sheet itself
 * (Home's "Start workout" button, the persistent-notification tap, tapping a different bottom tab)
 * request it expand/collapse, without needing to know anything about its internal drag/anchor
 * mechanics. */
class ActiveWorkoutSheetState internal constructor(
    internal val draggableState: AnchoredDraggableState<WorkoutSheetValue>,
) {
    suspend fun expand() = draggableState.animateTo(WorkoutSheetValue.Expanded)
    suspend fun collapse() = draggableState.animateTo(WorkoutSheetValue.Collapsed)

    /** Instantly resets to Collapsed with no animation - this state outlives any single workout
     * (created once for the whole `RegimenApp` session), so without this a new workout mounting
     * the sheet would inherit whatever expand/collapse state a *previous* workout left it in
     * (e.g. finishing/discarding while Expanded), showing the full screen immediately instead of
     * the collapsed banner. Called once, right as a new workout starts being tracked - not while
     * one is already in progress, which would undo the user's own drag/tap. */
    internal suspend fun resetToCollapsed() = draggableState.snapTo(WorkoutSheetValue.Collapsed)
}

@Composable
fun rememberActiveWorkoutSheetState(): ActiveWorkoutSheetState {
    val draggableState =
        remember { AnchoredDraggableState(initialValue = WorkoutSheetValue.Collapsed) }
    return remember(draggableState) { ActiveWorkoutSheetState(draggableState) }
}

/**
 * The live in-progress workout, modeled as a two-state draggable sheet rather than a NavHost
 * destination: collapsed shows a mini-player-style banner docked above the bottom nav; expanded
 * fills the screen with the full workout-logging UI, with a continuous drag/crossfade between the
 * two (no third, resting midway state - releasing mid-drag snaps to whichever anchor is closer).
 * Editing a past (already-finished) session goes through :feature:history's EditWorkoutScreen (a
 * normal NavHost push) instead - there's no "in progress" state to collapse an edit session to,
 * and it has none of this sheet's pause/rest-timer/finish machinery.
 *
 * Shown regardless of which NavHost destination is current, top-level tab or pushed screen alike
 * (Session Detail, Workout Summary, Exercise Library, "add custom exercise") - collapsed, it's
 * always reachable rather than only from the five tab roots.
 *
 * [workoutId] is bound directly to the DB's "is a workout in progress" signal by the caller - no
 * sticky/cached value needed. [onFinished]/[onDiscarded] fire synchronously from this composable's
 * own Finish/Discard confirm dialogs, right alongside the `finish()`/`discard()` calls that write
 * the change, rather than reactively off a round-tripped `uiState` flip - so there's no race to
 * avoid between the caller unmounting this composable and those callbacks getting a chance to run.
 * Deliberately does NOT navigate to Workout Summary itself either - that's the caller's job (it
 * owns the `NavController`, which this composable has no reason to reach into directly).
 */
@Composable
fun ActiveWorkoutSheet(
    workoutId: Long,
    state: ActiveWorkoutSheetState,
    navController: NavHostController,
    onFinished: (Long) -> Unit,
    onDiscarded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = state.draggableState
    val density = LocalDensity.current
    val collapsedHeightPx = with(density) { CollapsedHeight.toPx() }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    // Created here, unconditionally - not inside LiveWorkoutContent's `if (progress > 0f)` gate -
    // so its combine()/stateIn() query chain starts running the instant the sheet mounts, even
    // fully Collapsed, rather than only once the user first expands it. Data is warm (or at least
    // well on its way) by the time someone actually taps to expand, instead of that first expand
    // racing the initial query.
    val viewModel: ActiveWorkoutViewModel =
        hiltViewModel<ActiveWorkoutViewModel, ActiveWorkoutViewModel.Factory>(
            key = "active-workout-$workoutId",
            creationCallback = { factory -> factory.create(workoutId) },
        )

    BackHandler(enabled = sheetState.targetValue == WorkoutSheetValue.Expanded) {
        scope.launch { sheetState.animateTo(WorkoutSheetValue.Collapsed) }
    }

    // Set-row enter/exit animations are disabled both while Collapsed and for the first second
    // after expanding - the sheet's own expand transition plus every visible set row's enter
    // animation firing at once was enough real, measured jank that it's not worth having both
    // compete for frame budget at the same time. Keyed on targetValue (not currentValue) so this
    // starts counting the instant expansion is requested, and immediately turns back off - not
    // just pauses the countdown - if collapsed again before that second is up.
    var setRowAnimationsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState.targetValue) {
        if (sheetState.targetValue == WorkoutSheetValue.Expanded) {
            delay(1000)
            setRowAnimationsEnabled = true
        } else {
            setRowAnimationsEnabled = false
        }
    }

    val currentHeightPx =
        if (sheetState.offset.isNaN()) collapsedHeightPx else sheetState.requireOffset()
    val progress = if (containerHeightPx > collapsedHeightPx) {
        ((currentHeightPx - collapsedHeightPx) / (containerHeightPx - collapsedHeightPx)).coerceIn(
            0f,
            1f
        )
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                containerHeightPx = size.height.toFloat()
                if (containerHeightPx > collapsedHeightPx) {
                    sheetState.updateAnchors(
                        DraggableAnchors {
                            WorkoutSheetValue.Collapsed at collapsedHeightPx
                            WorkoutSheetValue.Expanded at containerHeightPx
                        },
                    )
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Decorative background only (color/shape/shadow, no children) - resizing this every
        // animation frame is cheap since there's nothing expensive inside it to remeasure. The
        // actual content below is laid out at this Box's own constant full size instead of being
        // nested inside this Surface, so it's never forced through the same remeasure - expanding/
        // collapsing used to also remeasure the full Scaffold+LazyColumn of exercise cards every
        // frame just because it lived inside the element whose height was being animated.
        //
        // anchoredDraggable lives here, not on the outer Box, specifically *because* this Surface
        // is sized to the sheet's current visible bounds (currentHeightPx) - putting it on the
        // always-full-screen outer Box instead made the whole screen capture drag/scroll gestures
        // even while collapsed, since a modifier's touch-detection region is its own layout
        // bounds, not "whatever's visually on top."
        // The pill's pronounced primaryContainer tint only ever showed up incidentally, via
        // LiveWorkoutContent's own (alpha-fading) Scaffold background happening to reveal this
        // Surface's fixed color underneath as it faded out - which read as a deliberate color
        // morph while collapsing, but on expand the same incidental reveal gets covered back up by
        // that same content fading IN almost immediately, so the collapsed tint barely registers
        // before it's gone. Interpolating this Surface's own color directly against progress (not
        // an animateColorAsState - that would add its own lag on top of an already-live drag)
        // makes the morph deliberate and identical in both directions, instead of a side effect of
        // one direction's alpha fade.
        val expandedBackground = MaterialTheme.colorScheme.background
        val expandedOnBackground = MaterialTheme.colorScheme.onBackground
        val collapsedBackground = MaterialTheme.colorScheme.primaryContainer
        val collapsedOnBackground = MaterialTheme.colorScheme.onPrimaryContainer
        Surface(
            color = lerp(collapsedBackground, expandedBackground, progress),
            contentColor = lerp(collapsedOnBackground, expandedOnBackground, progress),
            shadowElevation = 2.dp,
            shape = RoundedCornerShape(
                topStart = CollapsedCornerRadius * (1 - progress),
                topEnd = CollapsedCornerRadius * (1 - progress),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { currentHeightPx.toDp() })
                .anchoredDraggable(
                    state = sheetState,
                    orientation = Orientation.Vertical,
                    // Dragging up should grow the sheet (toward Expanded, the larger anchor).
                    reverseDirection = true,
                ),
        ) {}

        // Collapsed content: fades out as the sheet expands, and stops intercepting taps once
        // it's mostly gone so the full screen underneath can take over interaction. Fixed at
        // CollapsedHeight (not sized off the Surface above) since it's now a sibling, not a child.
        if (progress < 1f) {
            CollapsedBannerContent(
                onTap = { scope.launch { sheetState.animateTo(WorkoutSheetValue.Expanded) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(CollapsedHeight)
                    .alpha(1 - progress),
            )
        }
        // Full content: slides up + fades in as the sheet expands, rather than fading in place -
        // a flat crossfade doesn't visually track the drag at all (the whole screen's worth of
        // content just appears behind the pill, disconnected from where your finger actually is),
        // most apparent when slowly dragging the collapsed pill up by hand. Translating by however
        // much of the container is still "uncovered" (containerHeightPx - currentHeightPx) ties
        // its position directly to the same drag progress that's growing the pill, so it reads as
        // being pulled up out of it instead of materializing on its own. Still alpha-faded too -
        // pure translation alone would mean the still-full-size top app bar visibly overlaps the
        // collapsed banner's own fading-out content in the same 72dp space early in the drag.
        // Always composed (not gated behind an if) once past the very start of the drag, so the
        // live workout's state (scroll position, the ViewModel's collected StateFlow) survives
        // being dragged back down rather than being torn down and recreated. Sized against this
        // Box's constant full size, not the animated Surface, so the drag/expand animation never
        // remeasures it - only repaints (alpha + a graphicsLayer transform, both draw-phase-only).
        if (progress > 0f) {
            LiveWorkoutContent(
                viewModel = viewModel,
                onFinished = onFinished,
                onDiscarded = onDiscarded,
                onCreateCustomExercise = { navController.navigate(EditExerciseRoute()) },
                animateSets = setRowAnimationsEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = progress
                        translationY = containerHeightPx - currentHeightPx
                    },
            )
        }
    }
}

@Composable
private fun CollapsedBannerContent(onTap: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            stringResource(R.string.workout_in_progress_banner_message),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        Text(
            stringResource(R.string.workout_in_progress_banner_view_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** The live in-progress workout's full-screen content - the sheet's expanded state. [viewModel] is
 * created by the caller (not defaulted here via `hiltViewModel()`) since it needs to exist before
 * this composable is ever entered - see [ActiveWorkoutSheet]. */
@Composable
private fun LiveWorkoutContent(
    viewModel: ActiveWorkoutViewModel,
    onFinished: (Long) -> Unit,
    onDiscarded: () -> Unit,
    onCreateCustomExercise: () -> Unit,
    animateSets: Boolean,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle()
    val rest by viewModel.rest.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val restSetInvalidMessage = stringResource(R.string.workout_rest_set_invalid_snackbar)
    LaunchedEffect(Unit) {
        viewModel.restSetInvalidEvents.collect {
            snackbarHostState.showSnackbar(restSetInvalidMessage)
        }
    }

    var showDiscard by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Ephemeral - resets every time this screen is (re)opened, not a saved preference.
    var keepScreenOn by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Elapsed derives from startTime (survives rotation/process death).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(uiState.startTime) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }
    // Elapsed excludes accumulated pause; while paused it freezes at the moment of pausing.
    val elapsed = when {
        uiState.startTime == 0L -> 0L
        uiState.isPaused && uiState.pausedAt != null ->
            uiState.pausedAt!! - uiState.startTime - uiState.accumulatedPausedMs

        else -> now - uiState.startTime - uiState.accumulatedPausedMs
    }.coerceAtLeast(0)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                    when {
                        uiState.routineName != null -> uiState.routineName
                        uiState.loaded -> stringResource(R.string.workout_quick_workout_fallback)
                        else -> stringResource(R.string.workout_title_fallback)
                    }?.let {
                        Text(
                            it,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showDiscard = true }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.workout_discard_action),
                        )
                    }
                },
                actions = {
                    // Ephemeral - resets every time this screen is (re)opened, not a saved
                    // preference. Filled + tinted while on, outline + muted while off - same
                    // glyph (sun) both states so it can't misread as a light/dark theme toggle.
                    val keepScreenOnMessage =
                        stringResource(R.string.workout_keep_screen_on_snackbar)
                    IconButton(
                        onClick = {
                            keepScreenOn = !keepScreenOn
                            if (keepScreenOn) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(keepScreenOnMessage)
                                }
                            }
                        },
                    ) {
                        Icon(
                            if (keepScreenOn) Icons.Filled.WbSunny else Icons.Outlined.WbSunny,
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

        // Query kicks off as soon as the sheet mounts (see ActiveWorkoutSheet), well before this
        // content is ever composed - but a very fast expand right as a workout starts could still
        // beat it. Rather than the exercise list/toolbar rendering against still-default/empty
        // state, show an indeterminate spinner until the first real emission lands.
        if (!uiState.loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // BookOrExpanded caps and centers content at the same 600dp breakpoint as other
        // LazyColumn-of-cards screens (Routine Editor, Session Detail, Measurement Detail);
        // Compact/Tabletop stay full-bleed. The floating toolbar shares this inner Box so it's
        // capped with the list. No Onboarding-style hinge split for Tabletop: the toolbar anchors
        // to the bottom edge regardless of content, same reasoning as the bottom nav bar.
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
                            onUpdateSet = viewModel::updateSet,
                            onAddSet = viewModel::addSet,
                            onDeleteSet = viewModel::deleteSet,
                            onAutofillWeight = { setId, kg ->
                                viewModel.autofillWeightBelow(exercise.workoutExerciseId, setId, kg)
                            },
                            onAutofillReps = { setId, reps ->
                                viewModel.autofillRepsBelow(exercise.workoutExerciseId, setId, reps)
                            },
                            onToggleSkip = viewModel::toggleSkip,
                            onToggleDone = viewModel::toggleDone,
                            onUpdateCardio = viewModel::updateCardio,
                            onStartRest = viewModel::startRest,
                            enabled = !uiState.isPaused,
                            animateSets = animateSets,
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
                                viewModel.updateNote(it)
                            },
                            label = { Text(stringResource(R.string.workout_session_note_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (uiState.loaded && !uiState.notFound) {
                    ActiveWorkoutToolbar(
                        isPaused = uiState.isPaused,
                        elapsed = elapsed,
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onFinish = { showFinishConfirm = true },
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
            exercises = allExercises,
            onConfirm = {
                showPicker = false
                viewModel.addExercises(it)
            },
            onDismiss = { showPicker = false },
            onCreateCustom = {
                showPicker = false
                onCreateCustomExercise()
            },
        )
    }

    rest?.let {
        RestTimerSheet(
            rest = it,
            onAddTime = viewModel::addRestTime,
            onStop = viewModel::stopRest,
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.workout_discard_dialog_title),
            text = stringResource(R.string.workout_discard_dialog_text),
            confirmLabel = stringResource(R.string.workout_discard_action),
            onConfirm = {
                showDiscard = false
                viewModel.discard()
                onDiscarded()
            },
            dismissLabel = stringResource(R.string.workout_keep_going_button),
            onDismiss = { showDiscard = false },
            destructive = true,
        )
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
                viewModel.finish()
                onFinished(viewModel.workoutId)
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
 * resizing/fading, which never quite read as one coordinated motion.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveWorkoutToolbar(
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
    // when Morph interpolates toward Slanted's near-square space; ToolbarMorphShape above fits
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

    // "Live" cue while counting: breathing dot + ambient glow; absent once paused.
    val isRunning = !isPaused
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
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                // Tapping anywhere on the pill also triggers Pause/Resume (mini-player pattern) -
                // Finish's own click still wins as the nested target.
                onClick = { if (isPaused) hapticResume() else hapticPause() },
            )
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

/** Wide pill: elapsed time (+ live pulse dot), Pause and Finish - shown while running. */
@Composable
private fun RunningToolbarContent(
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
                formatElapsed(elapsed),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
            )
            // Live pulse dot: breathes while running.
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.4f + 0.6f * breathe)),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onPause, colors = buttonColors) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = stringResource(R.string.workout_pause_description),
                )
            }
            FilledIconButton(onClick = onFinish, colors = buttonColors) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.workout_finish_action),
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
