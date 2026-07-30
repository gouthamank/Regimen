package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason

/** Shared error copy for the secondary-device "Pull cloud data"/"Claim primary" actions,
 * mirroring [AuthErrorReason][dev.gouthaman.regimen.domain.model.AuthErrorReason]'s [text]
 * pattern. */
@Composable
fun SyncReplaceErrorReason.text(): String = when (this) {
    SyncReplaceErrorReason.WORKOUT_IN_PROGRESS -> stringResource(R.string.sync_replace_error_workout_in_progress)
    SyncReplaceErrorReason.PUSH_IN_PROGRESS -> stringResource(R.string.sync_replace_error_push_in_progress)
    SyncReplaceErrorReason.NETWORK -> stringResource(R.string.sync_replace_error_network)
    SyncReplaceErrorReason.UNKNOWN -> stringResource(R.string.sync_replace_error_unknown)
}
