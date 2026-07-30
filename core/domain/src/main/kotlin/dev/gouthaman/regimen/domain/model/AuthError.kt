package dev.gouthaman.regimen.domain.model

/** Coarse, UI-displayable classification of an auth/sync failure - deliberately doesn't carry the
 * underlying platform exception's message, since that's not fit for user-facing display (see
 * [AuthException]). The Composable layer maps each reason to real, translatable copy. */
enum class AuthErrorReason {
    /** No Google account is available on this device for Credential Manager to offer. */
    NO_CREDENTIALS,

    /** The user dismissed the sign-in sheet/picker themselves. */
    CANCELLED,

    /** A network-shaped failure (offline, timeout) talking to Firebase Auth/Firestore. */
    NETWORK,

    /** A security-sensitive operation (e.g. deleting the account) requires a session newer than
     * this one - Firebase enforces "recent login" for these regardless of whether the session
     * itself is still otherwise valid. Fixed by signing out and back in, not by retrying. */
    REAUTH_REQUIRED,

    /** Anything else - deliberately vague rather than surfacing a raw exception message. */
    UNKNOWN,
}

/** Wraps an [AuthRepository] failure with a [reason] classification, so callers can show
 * user-facing copy without inspecting platform-specific exception types (which [core:domain]
 * can't depend on) or displaying a raw exception message. */
class AuthException(
    val reason: AuthErrorReason,
    cause: Throwable? = null,
) : Exception(cause)
