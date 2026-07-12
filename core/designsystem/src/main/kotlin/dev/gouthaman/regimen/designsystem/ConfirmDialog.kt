package dev.gouthaman.regimen.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A title/text confirmation with a confirm action and an optional dismiss action — shared by
 * every delete/discard/finish confirmation across the app. Omit [dismissLabel] for an
 * acknowledgment-only dialog with a single button (e.g. an info dialog explaining why an action
 * was blocked). [destructive] colors the confirm button with the error color, for actions that
 * discard data or in-progress work; leave it false for a plain positive confirmation (e.g.
 * finishing a workout).
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = if (dismissLabel != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else null,
    )
}
