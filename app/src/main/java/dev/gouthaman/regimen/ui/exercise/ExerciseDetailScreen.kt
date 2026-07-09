package dev.gouthaman.regimen.ui.exercise

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.local.entity.Exercise

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExerciseDetailScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseDetailScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = {
            viewModel.deleteCurrent()
            onBack()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ExerciseDetailScreen(
    uiState: ExerciseDetailUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exercise = uiState.exercise

    // Expands from the tapped Library row (see ExerciseLibraryScreen's ExerciseRow) via the
    // shared-bounds container transform keyed on this exercise's id.
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = exerciseRowTransitionKey(uiState.exerciseId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
    }
    Scaffold(
        modifier = containerModifier,
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exercise?.isCustom == true) {
                        FilledIconButton(onClick = { onEdit(exercise.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                        FilledIconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                exercise != null -> ExerciseDetailContent(exercise, uiState.prLabel)
                uiState.loaded -> NotFound()
                else -> {} // initial loading — nothing to show yet
            }
        }
    }

    if (showDeleteDialog && exercise != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete exercise?") },
            text = { Text("\"${exercise.name}\" will be removed. This can't be undone.") },
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
private fun ExerciseDetailContent(exercise: Exercise, prLabel: String?) {
    ListItem(
        headlineContent = { Text("Type") },
        trailingContent = { Text(exercise.type.label()) },
    )
    ListItem(
        headlineContent = { Text("Muscle group") },
        trailingContent = { Text(exercise.muscleGroup.label()) },
    )
    ListItem(
        headlineContent = { Text("Equipment") },
        trailingContent = { Text(exercise.equipment.label()) },
    )
    ListItem(
        headlineContent = { Text("Source") },
        trailingContent = { Text(if (exercise.isCustom) "Custom" else "Built-in") },
    )

    HorizontalDivider()
    SectionHeader("Personal record")
    Text(
        text = prLabel ?: "No records yet — log a workout to set one.",
        style = if (prLabel != null) MaterialTheme.typography.headlineSmall
        else MaterialTheme.typography.bodyMedium,
        color = if (prLabel != null) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    SectionHeader("History")
    Text(
        text = "Per-session history will appear here once you log workouts.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NotFound() {
    Text(
        "Exercise not found.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
