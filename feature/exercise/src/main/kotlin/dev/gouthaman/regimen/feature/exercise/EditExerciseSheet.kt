package dev.gouthaman.regimen.feature.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gouthaman.regimen.common.customExerciseEquipment
import dev.gouthaman.regimen.common.customExerciseMuscleGroups
import dev.gouthaman.regimen.common.label
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.MuscleGroup

@Composable
fun EditExerciseSheet(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditExerciseViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    EditExerciseSheet(
        uiState = uiState,
        onBack = onBack,
        onNameChange = viewModel::setName,
        onMuscleGroupChange = viewModel::setMuscleGroup,
        onEquipmentChange = viewModel::setEquipment,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExerciseSheet(
    uiState: EditExerciseUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onMuscleGroupChange: (MuscleGroup) -> Unit,
    onEquipmentChange: (Equipment) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onBack, modifier = modifier) {
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
                    stringResource(if (uiState.isEditing) R.string.edit_exercise_edit_title else R.string.edit_exercise_new_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                FilledIconButton(onClick = onSave, enabled = uiState.canSave) {
                    Icon(
                        if (uiState.isEditing) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = stringResource(R.string.edit_exercise_save_description)
                    )
                }
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                singleLine = true,
                label = { Text(stringResource(R.string.edit_exercise_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )

            EnumDropdown(
                label = stringResource(R.string.edit_exercise_muscle_group_label),
                options = customExerciseMuscleGroups,
                selected = uiState.muscleGroup,
                optionLabel = { it.label() },
                onSelect = onMuscleGroupChange,
            )

            EnumDropdown(
                label = stringResource(R.string.edit_exercise_equipment_label),
                options = customExerciseEquipment,
                selected = uiState.equipment,
                optionLabel = { it.label() },
                onSelect = onEquipmentChange,
            )

            Text(
                stringResource(R.string.edit_exercise_strength_only_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
