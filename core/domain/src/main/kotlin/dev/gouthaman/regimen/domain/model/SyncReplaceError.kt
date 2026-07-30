package dev.gouthaman.regimen.domain.model

/** Coarse, UI-displayable classification for why a secondary device's full-replace action
 * (Pull cloud data / Claim primary) refused to run - deliberately doesn't carry the underlying
 * platform exception's message, same reasoning as [AuthErrorReason]. */
enum class SyncReplaceErrorReason {
    /** Pull only - refuses to wipe local sync-scoped state out from under a live,
     * foreground-service-backed workout session. */
    WORKOUT_IN_PROGRESS,

    /** Claim only - `syncConfig.lockedAt` is fresh, meaning a push *or another claim* is
     * genuinely in flight right now on this account (the lock is shared between both, not
     * push-exclusive). */
    PUSH_IN_PROGRESS,

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
