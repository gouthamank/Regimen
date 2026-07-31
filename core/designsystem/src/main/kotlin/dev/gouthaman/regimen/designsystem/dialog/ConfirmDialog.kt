package dev.gouthaman.regimen.designsystem.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

/**
 * A title/text confirmation with a confirm action and an optional dismiss action - shared by
 * every delete/discard/finish confirmation across the app. Omit [dismissLabel] for a single-button
 * acknowledgment-only dialog. [destructive]/[positive] color the confirm button (error/tertiary)
 * for discard-data vs. unambiguously-good actions; leave both false for neutral. [confirmEnableDelayMillis]
 * disables confirm for a beat, for confirmations that deserve a pause before committing.
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
    positive: Boolean = false,
    confirmEnableDelayMillis: Long = 0,
) {
    val haptics = LocalHapticFeedback.current
    var confirmEnabled by remember(confirmEnableDelayMillis) {
        mutableStateOf(
            confirmEnableDelayMillis <= 0
        )
    }
    LaunchedEffect(confirmEnableDelayMillis) {
        if (confirmEnableDelayMillis > 0) {
            delay(confirmEnableDelayMillis)
            confirmEnabled = true
            // Marks the moment the button becomes tappable, so a still-waiting thumb feels it
            // flip, not just sees it.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (destructive) haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    onConfirm()
                },
                enabled = confirmEnabled,
                colors = when {
                    destructive -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    positive -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    else -> ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = if (dismissLabel != null) {
            { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
        } else null,
    )
}
