package dev.gouthaman.regimen.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.common.R as CommonR

/**
 * Metric/imperial segmented picker, shared by Onboarding and Settings (identical implementations
 * before this was extracted). [weightLabels] switches between the weight-unit and distance-unit
 * label sets for the same [UnitSystem] enum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSystemSelector(
    selected: UnitSystem,
    onChange: (UnitSystem) -> Unit,
    weightLabels: Boolean,
) {
    val options = UnitSystem.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    stringResource(
                        if (weightLabels) {
                            when (option) {
                                UnitSystem.METRIC -> CommonR.string.unit_system_metric_weight
                                UnitSystem.IMPERIAL -> CommonR.string.unit_system_imperial_weight
                            }
                        } else {
                            when (option) {
                                UnitSystem.METRIC -> CommonR.string.unit_system_metric_distance
                                UnitSystem.IMPERIAL -> CommonR.string.unit_system_imperial_distance
                            }
                        },
                    ),
                )
            }
        }
    }
}
