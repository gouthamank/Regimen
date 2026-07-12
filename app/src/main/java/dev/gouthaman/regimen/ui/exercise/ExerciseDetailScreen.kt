package dev.gouthaman.regimen.ui.exercise

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.designsystem.ConfirmDialog
import dev.gouthaman.regimen.designsystem.SectionHeader
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.ui.adaptive.LocalRegimenWindowInfo
import dev.gouthaman.regimen.ui.adaptive.RegimenPosture
import dev.gouthaman.regimen.ui.history.SessionFormat
import dev.gouthaman.regimen.ui.util.text

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
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val deleteBlockedInfo by viewModel.deleteBlockedInfo.collectAsStateWithLifecycle()

    // Deletion is refused if the exercise is still in use (DeleteExerciseUseCase), so navigate
    // back only once it's actually gone, not just on tapping "Delete".
    LaunchedEffect(deleted) { if (deleted) onBack() }

    ExerciseDetailScreen(
        uiState = uiState,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onBack = onBack,
        onEdit = onEdit,
        onDelete = viewModel::deleteCurrent,
        deleteBlockedInfo = deleteBlockedInfo,
        onDismissDeleteBlocked = viewModel::dismissDeleteBlockedMessage,
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
    deleteBlockedInfo: ExerciseDeleteBlockedInfo? = null,
    onDismissDeleteBlocked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val exercise = uiState.exercise
    val windowInfo = LocalRegimenWindowInfo.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Expands from the tapped Library row (see ExerciseLibraryScreen's ExerciseRow) via the
    // shared-bounds container transform keyed on this exercise's id.
    val containerModifier = with(sharedTransitionScope) {
        modifier
            .fillMaxSize()
            .sharedBounds(
                rememberSharedContentState(key = exerciseRowTransitionKey(uiState.exerciseId)),
                animatedVisibilityScope = animatedVisibilityScope,
            )
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    }
    Scaffold(
        modifier = containerModifier,
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        exercise?.name ?: stringResource(R.string.exercise_detail_title_fallback)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.exercise_detail_back_description)
                        )
                    }
                },
                actions = {
                    if (exercise?.isCustom == true) {
                        FilledIconButton(onClick = { onEdit(exercise.id) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.exercise_detail_edit_description)
                            )
                        }
                        FilledIconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.exercise_detail_delete_description)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
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
            Column(
                modifier = contentModifier.verticalScroll(rememberScrollState()),
            ) {
                when {
                    exercise != null -> ExerciseDetailContent(
                        exercise,
                        uiState.pr,
                        uiState.history,
                        uiState.weightUnit,
                        uiState.distanceUnit,
                    )

                    uiState.loaded -> NotFound()
                    else -> {}
                }
            }
        }
    }

    if (showDeleteDialog && exercise != null) {
        ConfirmDialog(
            title = stringResource(R.string.exercise_detail_delete_dialog_title),
            text = stringResource(R.string.exercise_detail_delete_dialog_text, exercise.name),
            confirmLabel = stringResource(R.string.exercise_detail_delete_confirm_button),
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
            dismissLabel = stringResource(R.string.exercise_detail_cancel_button),
            onDismiss = { showDeleteDialog = false },
            destructive = true,
        )
    }

    if (deleteBlockedInfo != null) {
        val usedIn = listOfNotNull(
            stringResource(R.string.exercise_detail_used_in_routines).takeIf { deleteBlockedInfo.inRoutines },
            stringResource(R.string.exercise_detail_used_in_workouts).takeIf { deleteBlockedInfo.inWorkouts },
        ).joinToString(" and ")
        ConfirmDialog(
            title = stringResource(R.string.exercise_detail_delete_blocked_title),
            text = stringResource(
                R.string.exercise_detail_delete_blocked_text,
                deleteBlockedInfo.exerciseName,
                usedIn,
            ),
            confirmLabel = stringResource(R.string.exercise_detail_delete_blocked_ok_button),
            onConfirm = onDismissDeleteBlocked,
            onDismiss = onDismissDeleteBlocked,
        )
    }
}

@Composable
private fun exercisePrLabel(value: ExercisePrValue): String = when (value) {
    is ExercisePrValue.Weight -> stringResource(
        R.string.exercise_detail_pr_weight_label,
        value.displayValue,
        value.unitLabel.text()
    )

    is ExercisePrValue.Reps -> pluralStringResource(
        R.plurals.exercise_detail_pr_reps_count,
        value.count,
        value.count
    )
}

@Composable
private fun ExerciseDetailContent(
    exercise: Exercise,
    pr: ExercisePrValue?,
    history: List<ExerciseHistoryItem>,
    weightUnit: UnitSystem,
    distanceUnit: UnitSystem,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.exercise_detail_type_label)) },
        trailingContent = { Text(exercise.type.label()) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.exercise_detail_muscle_group_label)) },
        trailingContent = { Text(exercise.muscleGroup.label()) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.exercise_detail_equipment_label)) },
        trailingContent = { Text(exercise.equipment.label()) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.exercise_detail_source_label)) },
        trailingContent = {
            Text(
                stringResource(
                    if (exercise.isCustom) R.string.exercise_detail_source_custom else R.string.exercise_detail_source_builtin,
                ),
            )
        },
    )

    HorizontalDivider()
    SectionHeader(
        stringResource(R.string.exercise_detail_pr_header),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        ),
    )
    Text(
        text = pr?.let { exercisePrLabel(it) }
            ?: stringResource(R.string.exercise_detail_no_records_yet),
        style = if (pr != null) MaterialTheme.typography.headlineSmall
        else MaterialTheme.typography.bodyMedium,
        color = if (pr != null) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    SectionHeader(
        stringResource(R.string.exercise_detail_history_header),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        ),
    )
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.exercise_detail_no_sessions_logged),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    } else {
        history.forEach { item ->
            val entryLabels = item.sets.map { SessionFormat.setLabel(it, weightUnit) } +
                    item.cardio.map { SessionFormat.cardioLabel(it, distanceUnit) }
            ListItem(
                headlineContent = { Text(item.dateLabel) },
                supportingContent = { Text(entryLabels.joinToString(" · ")) },
            )
        }
    }
}

@Composable
private fun NotFound() {
    Text(
        stringResource(R.string.exercise_detail_not_found),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
