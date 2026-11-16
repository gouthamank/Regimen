package dev.gouthaman.regimen.domain.model

/** Coarse, UI-displayable classification for why Pull cloud data, Claim primary, or Delete cloud
 * data failed - deliberately doesn't carry the underlying platform
 * exception's message, same reasoning as [AuthErrorReason]. */
enum class SyncReplaceErrorReason {
    /** Pull only - refuses to wipe local sync-scoped state out from under a live,
     * foreground-service-backed workout session. */
    WORKOUT_IN_PROGRESS,

    /** Claim/Delete - `syncConfig.lockedAt` is fresh, meaning a push *or a claim* is genuinely in
     * flight right now on this account (the lock is shared, not push-exclusive). */
    PUSH_IN_PROGRESS,

    /** Claim only - another device claimed primary while this one was still uploading. */
    TAKEN_OVER,

    /** Delete only - the cloud data is gone but deleting the Firebase Auth account needs a newer
     * sign-in than the one taken just before it started. Running Delete again (which
     * re-authenticates first) finishes it. */
    REAUTH_REQUIRED,

    /** The Google grant behind the local session no longer exists - see
     * [AuthErrorReason.SESSION_REVOKED]. */
    SESSION_REVOKED,

    /** A network-shaped failure (offline, timeout) talking to Firestore. */
    NETWORK,

    /** Anything else - deliberately vague rather than surfacing a raw exception message. */
    UNKNOWN,
}

/** Wraps a [SyncReplaceRepository][dev.gouthaman.regimen.domain.repository.SyncReplaceRepository]
 * failure with a [reason] classification, mirroring [AuthException]'s shape. */
class SyncReplaceException(
    val reason: SyncReplaceErrorReason,
    cause: Throwable? = null,
) : Exception(cause)
