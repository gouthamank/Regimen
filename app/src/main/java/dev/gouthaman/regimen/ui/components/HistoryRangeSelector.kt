package dev.gouthaman.regimen.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.gouthaman.regimen.domain.model.HistoryRange

/** Range selector (4w / 3m / 1y / All) shared by the Progress frequency chart and the
 * Measurement trend chart. Callers are responsible for their own horizontal inset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRangeSelector(
    selected: HistoryRange,
    onSelect: (HistoryRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = HistoryRange.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(option.label)
            }
        }
    }
}
