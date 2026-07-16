package dev.gouthaman.regimen.feature.routines

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.designsystem.dialog.ConfirmDialog
import dev.gouthaman.regimen.designsystem.dragdrop.dragHandle
import dev.gouthaman.regimen.designsystem.dragdrop.rememberDragDropState
import dev.gouthaman.regimen.domain.model.RoutineWithExercises

@Composable
fun RoutinesScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutinesListViewModel = hiltViewModel(),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    RoutinesScreen(
        routines = routines,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onCreateRoutine = onCreateRoutine,
        onOpenRoutine = onOpenRoutine,
        onDelete = viewModel::delete,
        onReorder = viewModel::reorder,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RoutinesScreen(
    routines: List<RoutineWithExercises>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (Long) -> Unit,
    onDelete: (RoutineWithExercises) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fabModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = routineCreateFabTransitionKey),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .then(modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.routines_title)) },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoutine,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.routines_new_fab)) },
                modifier = fabModifier,
            )
        },
    ) { innerPadding ->
        // BookOrExpanded caps and centers content, same 600dp breakpoint as Onboarding/the nav
        // shell; Compact/Tabletop stay full-bleed. Flat list, not Home's two-column dashboard,
        // so a single wider column reads better than a grid.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentModifier = if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                Modifier
                    .widthIn(max = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND.dp)
                    .fillMaxSize()
            } else {
                Modifier.fillMaxSize()
            }
            if (routines.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.routines_empty_state),
                    modifier = (if (windowInfo.posture == RegimenPosture.BookOrExpanded) {
                        Modifier.widthIn(max = 480.dp)
                    } else {
                        Modifier
                    }).fillMaxSize(),
                )
            } else {
                RoutineList(
                    routines = routines,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onOpenRoutine = onOpenRoutine,
                    onDelete = onDelete,
                    onReorder = onReorder,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RoutineList(
    routines: List<RoutineWithExercises>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenRoutine: (Long) -> Unit,
    onDelete: (RoutineWithExercises) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Working copy the drag reorders in place; re-synced from the source whenever it changes and
    // we're not mid-drag (the VM's optimistic ordering keeps the source stable during a drag).
    val working = remember { mutableStateListOf<RoutineWithExercises>() }
    val listState = rememberLazyListState()
    val dragState = rememberDragDropState(listState) { draggedKey, targetKey ->
        val from = working.indexOfFirst { it.routine.id == draggedKey }
        val to = working.indexOfFirst { it.routine.id == targetKey }
        if (from != -1 && to != -1) working.add(to, working.removeAt(from))
    }

    LaunchedEffect(routines) {
        if (dragState.draggingItemKey == null) {
            working.clear()
            working.addAll(routines)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(working, key = { _, it -> it.routine.id }) { index, routine ->
            val dragging = routine.routine.id == dragState.draggingItemKey
            val itemModifier = if (dragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer { translationY = dragState.draggingItemOffset }
            } else {
                Modifier.animateItem()
            }
            RoutineCard(
                routine = routine,
                elevated = dragging,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onClick = { onOpenRoutine(routine.routine.id) },
                onDelete = { onDelete(routine) },
                dragHandleModifier = Modifier.dragHandle(dragState, index) {
                    onReorder(working.map { it.routine.id })
                },
                modifier = itemModifier,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RoutineCard(
    routine: RoutineWithExercises,
    elevated: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exercises = routine.exercises.sortedBy { it.routineExercise.position }
    val summary = when {
        exercises.isEmpty() -> stringResource(R.string.routines_no_exercises_yet)
        else -> exercises.joinToString(", ") { it.exercise.name }
    }

    val cardModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = routineRowTransitionKey(routine.routine.id)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
    }
    Card(
        modifier = cardModifier
            .then(if (elevated) Modifier.shadow(8.dp, MaterialTheme.shapes.medium) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(
                    R.string.routines_reorder_description,
                    routine.routine.name
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(horizontal = 8.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        R.string.routines_card_summary,
                        pluralStringResource(
                            R.plurals.routines_exercise_count,
                            exercises.size,
                            exercises.size
                        ),
                        summary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.routines_delete_description)
                )
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.routines_delete_dialog_title),
            text = stringResource(R.string.routines_delete_dialog_text, routine.routine.name),
            confirmLabel = stringResource(R.string.routines_delete_confirm_button),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            dismissLabel = stringResource(R.string.routines_cancel_button),
            onDismiss = { showDeleteDialog = false },
            destructive = true,
        )
    }
}
