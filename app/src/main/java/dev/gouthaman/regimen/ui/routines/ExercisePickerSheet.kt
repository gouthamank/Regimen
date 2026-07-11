package dev.gouthaman.regimen.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gouthaman.regimen.R
import dev.gouthaman.regimen.data.local.entity.Exercise
import dev.gouthaman.regimen.domain.model.matchesSearch
import dev.gouthaman.regimen.ui.exercise.label

/**
 * Reusable multi-select exercise picker (S16). Callers pass the addable [exercises] (already
 * context-filtered — e.g. strength-only for routines); confirming returns the chosen ids.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
    onCreateCustom: () -> Unit,
    modifier: Modifier = Modifier,
    initiallySelected: Set<Long> = emptySet(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>().apply { addAll(initiallySelected) } }

    // Keep selected items visible even if a later search query would otherwise filter them out.
    val visible = exercises.filter { it.matchesSearch(query) || it.id in selected }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        // List gets weight(1f, fill = false) so header/footer stay pinned outside it without
        // forcing full sheet height — a fixed heightIn cap instead could let header + list-cap +
        // footer exceed the sheet's available height, pushing Save off-screen with nothing left
        // to scroll it back into view.
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.exercise_picker_title),
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.exercise_picker_search_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.exercise_picker_create_custom)) },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onCreateCustom),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp),
        ) {
            items(visible, key = { it.id }) { exercise ->
                val checked = exercise.id in selected
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        Text("${exercise.muscleGroup.label()} · ${exercise.equipment.label()}")
                    },
                    leadingContent = {
                        Checkbox(checked = checked, onCheckedChange = null)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        if (checked) selected.remove(exercise.id) else selected.add(exercise.id)
                    },
                )
            }
        }

        Button(
            onClick = { onConfirm(selected.toList()) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                if (selected.isEmpty()) stringResource(R.string.exercise_picker_save_button)
                else stringResource(R.string.exercise_picker_save_with_count_button, selected.size),
            )
        }
    }
}
