package dev.gouthaman.regimen.feature.exercise

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.common.label
import dev.gouthaman.regimen.designsystem.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.designsystem.adaptive.RegimenPosture
import dev.gouthaman.regimen.designsystem.component.EmptyState
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
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
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val windowInfo = LocalRegimenWindowInfo.current
    val customLabel = stringResource(R.string.exercise_library_custom_label)

    // Pairs a chip label with the toggle that clears it, so the filter row and "clear all" share one list instead of four branches.
    val activeFilters = buildList<Pair<String, () -> Unit>> {
        filters.type?.let { type -> add(type.label() to { onToggleType(type) }) }
        filters.muscleGroup?.let { mg -> add(mg.label() to { onToggleMuscleGroup(mg) }) }
        filters.equipment?.let { eq -> add(eq.label() to { onToggleEquipment(eq) }) }
        if (filters.customOnly) add(customLabel to onToggleCustomOnly)
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exercise_library_back_description)
                        )
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
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.exercise_library_filters_label)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCustom) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.exercise_library_add_custom_description)
                )
            }
        },
    ) { innerPadding ->
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
            Column(modifier = contentModifier) {
                if (activeFilters.isNotEmpty()) {
                    ActiveFiltersRow(activeFilters)
                }

                if (uiState.exercises.isEmpty()) {
                    val hasActiveFilters =
                        activeFilters.isNotEmpty() || filters.query.isNotEmpty()
                    EmptyState(
                        message = stringResource(R.string.exercise_library_empty_state),
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Filled.SearchOff,
                        actionLabel = if (hasActiveFilters) {
                            stringResource(R.string.exercise_library_clear_filters_button)
                        } else null,
                        onAction = if (hasActiveFilters) {
                            {
                                onQueryChange("")
                                activeFilters.forEach { (_, clear) -> clear() }
                            }
                        } else null,
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
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.exercise_library_clear_search_description)
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.exercise_library_search_placeholder)) },
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
                        contentDescription = stringResource(
                            R.string.exercise_library_remove_filter_description,
                            label
                        ),
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
                Text(
                    stringResource(R.string.exercise_library_filters_label),
                    style = MaterialTheme.typography.titleLarge
                )
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
                    Text(stringResource(R.string.exercise_library_clear_all_button))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.exercise_library_type_filter_title),
                    style = MaterialTheme.typography.labelLarge
                )
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
                        label = { Text(stringResource(R.string.exercise_library_custom_label)) },
                    )
                }
            }
            FilterSection(
                title = stringResource(R.string.exercise_library_muscle_group_filter_title),
                options = MuscleGroup.entries,
                selected = filters.muscleGroup,
                label = { it.label() },
                onToggle = onToggleMuscleGroup,
            )
            FilterSection(
                title = stringResource(R.string.exercise_library_equipment_filter_title),
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
    label: @Composable (T) -> String,
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
                        stringResource(R.string.exercise_library_custom_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}
