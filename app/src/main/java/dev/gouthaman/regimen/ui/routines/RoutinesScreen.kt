package dev.gouthaman.regimen.ui.routines

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.local.entity.RoutineWithExercises

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
    val fabModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = routineCreateFabTransitionKey),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Routines") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateRoutine,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New routine") },
                modifier = fabModifier,
            )
        },
    ) { innerPadding ->
        if (routines.isEmpty()) {
            EmptyState(Modifier.padding(innerPadding))
        } else {
            RoutineList(
                routines = routines,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onOpenRoutine = onOpenRoutine,
                onDelete = onDelete,
                onReorder = onReorder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
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
        exercises.isEmpty() -> "No exercises yet"
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
                contentDescription = "Reorder ${routine.routine.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = dragHandleModifier.padding(horizontal = 8.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.routine.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${exercises.size} ${if (exercises.size == 1) "exercise" else "exercises"} · $summary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete routine")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete routine?") },
            text = { Text("\"${routine.routine.name}\" will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No routines yet. Build one to start working out from a template.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
