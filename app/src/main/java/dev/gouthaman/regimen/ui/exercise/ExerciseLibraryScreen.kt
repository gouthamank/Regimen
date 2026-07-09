package dev.gouthaman.regimen.ui.exercise

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExerciseLibraryScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    onAddCustom: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseLibraryScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onExerciseClick = onExerciseClick,
        onAddCustom = onAddCustom,
        onQueryChange = viewModel::setQuery,
        onToggleType = viewModel::toggleType,
        onToggleMuscleGroup = viewModel::toggleMuscleGroup,
        onToggleEquipment = viewModel::toggleEquipment,
        onToggleCustomOnly = viewModel::toggleCustomOnly,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ExerciseLibraryScreen(
    uiState: ExerciseLibraryUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onExerciseClick: (Long) -> Unit,
    onAddCustom: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleType: (ExerciseType) -> Unit,
    onToggleMuscleGroup: (MuscleGroup) -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
    onToggleCustomOnly: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = uiState.filters
    var showFilterSheet by remember { mutableStateOf(false) }

    // Each entry pairs a chip label with the toggle that clears it — lets the active-filter
    // row and the "clear all" action share one list instead of four parallel branches.
    val activeFilters = buildList<Pair<String, () -> Unit>> {
        filters.type?.let { type -> add(type.label() to { onToggleType(type) }) }
        filters.muscleGroup?.let { mg -> add(mg.label() to { onToggleMuscleGroup(mg) }) }
        filters.equipment?.let { eq -> add(eq.label() to { onToggleEquipment(eq) }) }
        if (filters.customOnly) add("Custom" to onToggleCustomOnly)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    SearchField(
                        query = filters.query,
                        onQueryChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFilters.isNotEmpty()) {
                                Badge { Text(activeFilters.size.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Filters")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustom) {
                Icon(Icons.Filled.Add, contentDescription = "Add custom exercise")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (activeFilters.isNotEmpty()) {
                ActiveFiltersRow(activeFilters)
            }

            if (uiState.exercises.isEmpty()) {
                EmptyState(
                    hasActiveFilters = activeFilters.isNotEmpty() || filters.query.isNotEmpty(),
                    onClearAll = {
                        onQueryChange("")
                        activeFilters.forEach { (_, clear) -> clear() }
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = { onExerciseClick(exercise.id) },
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            filters = filters,
            onToggleType = onToggleType,
            onToggleMuscleGroup = onToggleMuscleGroup,
            onToggleEquipment = onToggleEquipment,
            onToggleCustomOnly = onToggleCustomOnly,
            onDismiss = { showFilterSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
            }
        },
        placeholder = { Text("Search exercises") },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier,
    )
}

/** One removable chip per active filter. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveFiltersRow(activeFilters: List<Pair<String, () -> Unit>>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(activeFilters) { (label, clear) ->
            FilterChip(
                selected = true,
                onClick = clear,
                label = { Text(label) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove $label filter",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: ExerciseFilters,
    onToggleType: (ExerciseType) -> Unit,
    onToggleMuscleGroup: (MuscleGroup) -> Unit,
    onToggleEquipment: (Equipment) -> Unit,
    onToggleCustomOnly: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Filters", style = MaterialTheme.typography.titleLarge)
                val hasActive = filters.type != null || filters.muscleGroup != null ||
                        filters.equipment != null || filters.customOnly
                TextButton(
                    onClick = {
                        filters.type?.let(onToggleType)
                        filters.muscleGroup?.let(onToggleMuscleGroup)
                        filters.equipment?.let(onToggleEquipment)
                        if (filters.customOnly) onToggleCustomOnly()
                    },
                    enabled = hasActive,
                ) {
                    Text("Clear all")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseType.entries.forEach { option ->
                        FilterChip(
                            selected = option == filters.type,
                            onClick = { onToggleType(option) },
                            label = { Text(option.label()) },
                        )
                    }
                    FilterChip(
                        selected = filters.customOnly,
                        onClick = onToggleCustomOnly,
                        label = { Text("Custom") },
                    )
                }
            }
            FilterSection(
                title = "Muscle group",
                options = MuscleGroup.entries,
                selected = filters.muscleGroup,
                label = { it.label() },
                onToggle = onToggleMuscleGroup,
            )
            FilterSection(
                title = "Equipment",
                options = Equipment.entries,
                selected = filters.equipment,
                label = { it.label() },
                onToggle = onToggleEquipment,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterSection(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onToggle(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
) {
    val cardModifier = with(sharedTransitionScope) {
        Modifier
            .fillMaxWidth()
            .sharedBounds(
                rememberSharedContentState(key = exerciseRowTransitionKey(exercise.id)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = cardModifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseIcon(exercise.type, exercise.equipment)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(exercise.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${exercise.muscleGroup.label()} · ${exercise.equipment.label()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exercise.isCustom) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** No Material icon distinguishes barbell/dumbbell/kettlebell individually, so free weights share one glyph. */
private fun equipmentIcon(equipment: Equipment) = when (equipment) {
    Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL -> Icons.Filled.FitnessCenter
    Equipment.MACHINE -> Icons.Filled.PrecisionManufacturing
    Equipment.CABLE -> Icons.Filled.Cable
    Equipment.BODYWEIGHT -> Icons.Filled.SelfImprovement
    Equipment.CARDIO_MACHINE -> Icons.Filled.DirectionsRun
    Equipment.OTHER -> Icons.Filled.Category
}

@Composable
private fun ExerciseIcon(type: ExerciseType, equipment: Equipment) {
    val (container, onContainer) = when (type) {
        ExerciseType.STRENGTH -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer

        ExerciseType.CARDIO -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = container, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            equipmentIcon(equipment),
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyState(hasActiveFilters: Boolean, onClearAll: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                "No exercises match your filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (hasActiveFilters) {
                TextButton(onClick = onClearAll) {
                    Text("Clear filters")
                }
            }
        }
    }
}
