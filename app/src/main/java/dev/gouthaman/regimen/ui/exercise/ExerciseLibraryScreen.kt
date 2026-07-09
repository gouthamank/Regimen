package dev.gouthaman.regimen.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseLibraryScreen(
        uiState = uiState,
        onBack = onBack,
        onExerciseClick = onExerciseClick,
        onAddCustom = onAddCustom,
        onQueryChange = viewModel::setQuery,
        onToggleType = viewModel::toggleType,
        onToggleMuscleGroup = viewModel::toggleMuscleGroup,
        onToggleEquipment = viewModel::toggleEquipment,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    uiState: ExerciseLibraryUiState,
    onBack: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    onAddCustom: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleType: (ExerciseType) -> Unit,
    onToggleMuscleGroup: (MuscleGroup) -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = uiState.filters
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Exercise library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledIconButton(onClick = onAddCustom) {
                        Icon(Icons.Filled.Add, contentDescription = "Add custom exercise")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = filters.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search exercises") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FilterChipRow(
                options = ExerciseType.entries,
                selected = filters.type,
                label = { it.label() },
                onToggle = onToggleType,
            )
            FilterChipRow(
                options = MuscleGroup.entries,
                selected = filters.muscleGroup,
                label = { it.label() },
                onToggle = onToggleMuscleGroup,
            )
            FilterChipRow(
                options = Equipment.entries,
                selected = filters.equipment,
                label = { it.label() },
                onToggle = onToggleEquipment,
            )

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            if (uiState.exercises.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(exercise, onClick = { onExerciseClick(exercise.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(options.size) { index ->
            val item = options[index]
            FilterChip(
                selected = item == selected,
                onClick = { onToggle(item) },
                label = { Text(label(item)) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text("${exercise.muscleGroup.label()} · ${exercise.equipment.label()}")
        },
        trailingContent = if (exercise.isCustom) {
            {
                Text(
                    "Custom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Text(
            "No exercises match your filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
