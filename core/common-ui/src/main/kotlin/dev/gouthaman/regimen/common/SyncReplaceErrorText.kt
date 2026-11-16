package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason

/** Shared error copy for the Account screen's background sync operations (Pull cloud data, Claim
 * primary, Delete cloud data), mirroring [AuthErrorReason][dev.gouthaman.regimen.domain.model.AuthErrorReason]'s [text]
 * pattern. */
@Composable
fun SyncReplaceErrorReason.text(): String = when (this) {
    SyncReplaceErrorReason.WORKOUT_IN_PROGRESS -> stringResource(R.string.sync_replace_error_workout_in_progress)
    SyncReplaceErrorReason.PUSH_IN_PROGRESS -> stringResource(R.string.sync_replace_error_push_in_progress)
    SyncReplaceErrorReason.TAKEN_OVER -> stringResource(R.string.sync_replace_error_taken_over)
    SyncReplaceErrorReason.REAUTH_REQUIRED -> stringResource(R.string.sync_replace_error_reauth_required)
    SyncReplaceErrorReason.SESSION_REVOKED -> stringResource(R.string.sync_replace_error_session_revoked)
    SyncReplaceErrorReason.NETWORK -> stringResource(R.string.sync_replace_error_network)
    SyncReplaceErrorReason.UNKNOWN -> stringResource(R.string.sync_replace_error_unknown)
}
