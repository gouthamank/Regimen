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
import dev.gouthaman.regimen.domain.model.ThemeMode
import dev.gouthaman.regimen.common.R as CommonR

/** Light/dark/system segmented picker, shared by Onboarding and Settings (identical
 * implementations before this was extracted). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeModeSelector(selected: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(
                    stringResource(
                        when (option) {
                            ThemeMode.LIGHT -> CommonR.string.theme_mode_light
                            ThemeMode.DARK -> CommonR.string.theme_mode_dark
                            ThemeMode.SYSTEM -> CommonR.string.theme_mode_system
                        },
                    ),
                )
            }
        }
    }
}
