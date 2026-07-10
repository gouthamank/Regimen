package dev.gouthaman.regimen.ui.measurements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * S8a — Add Measurement Entry. Pick a type (locked when [fixedTypeId] is set, e.g. from the detail
 * screen), a date (defaults to today), and a value in the user's display units. [onSave] receives
 * the raw display value; the ViewModel converts to canonical storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMeasurementSheet(
    types: List<MeasurementType>,
    weightUnit: UnitSystem,
    onDismiss: () -> Unit,
    onSave: (typeId: Long, date: Long, displayValue: Double) -> Unit,
    fixedTypeId: Long? = null,
) {
    if (types.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember {
        mutableStateOf(types.firstOrNull { it.id == fixedTypeId } ?: types.first())
    }
    var value by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    val unitLabel = MeasurementFormat.unitLabel(selectedType, weightUnit)
    val parsedValue = value.trim().toDoubleOrNull()
    val canSave = parsedValue != null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Title is pinned above the form, Save is pinned below it (via ColumnScope.weight on the
        // form fields, fill = false so a short form doesn't force the sheet to full height) —
        // rather than one plain Column with everything including Save, so that in compact
        // landscape the form scrolls internally instead of pushing Save off the bottom of the
        // sheet with nothing left to scroll it back into view.
        Text(
            "Add measurement",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Type picker (locked when opened for a specific type).
            if (fixedTypeId == null) {
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Measurement") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        types.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            } else {
                Text(selectedType.name, style = MaterialTheme.typography.titleMedium)
            }

            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Value ($unitLabel)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text("Date")
                TextButton(onClick = { showDatePicker = true }) {
                    Text(dateFormatter.format(dateMillis))
                }
            }
        }

        Button(
            onClick = { onSave(selectedType.id, dateMillis, parsedValue!!) },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 16.dp),
        ) {
            Text("Save")
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis,
            selectableDates = NoFutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

/**
 * Restricts the date picker to today or earlier. The picker works in UTC-midnight millis, so we
 * compare on calendar day (in UTC, matching how the picked value is stored) against today's local
 * date — no future calendar day can be selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
private object NoFutureDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val picked = java.time.Instant.ofEpochMilli(utcTimeMillis)
            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return !picked.isAfter(java.time.LocalDate.now())
    }

    override fun isSelectableYear(year: Int): Boolean = year <= java.time.Year.now().value
}
