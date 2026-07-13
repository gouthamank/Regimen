package dev.gouthaman.regimen.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * A section label within a screen (e.g. "Personal records", "Preferences"). Style and padding are
 * caller-supplied — screens use genuinely different typography/spacing for this today, not just
 * a copy-paste accident.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Text(text, style = style, modifier = modifier)
}
