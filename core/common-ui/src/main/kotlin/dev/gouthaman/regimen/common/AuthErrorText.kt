package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.AuthErrorReason

/** Shared across `:feature:account` and `:feature:onboarding` - both surfaces show the same
 * sign-in errors, so the copy lives once here rather than duplicated per module. */
@Composable
fun AuthErrorReason.text(): String = when (this) {
    AuthErrorReason.NO_CREDENTIALS -> stringResource(R.string.auth_error_no_credentials)
    AuthErrorReason.CANCELLED -> stringResource(R.string.auth_error_cancelled)
    AuthErrorReason.NETWORK -> stringResource(R.string.auth_error_network)
    AuthErrorReason.REAUTH_REQUIRED -> stringResource(R.string.auth_error_reauth_required)
    AuthErrorReason.SESSION_REVOKED -> stringResource(R.string.auth_error_session_revoked)
    AuthErrorReason.UNKNOWN -> stringResource(R.string.auth_error_unknown)
}
