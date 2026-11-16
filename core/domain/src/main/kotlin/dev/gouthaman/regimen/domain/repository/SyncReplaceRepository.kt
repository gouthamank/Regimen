package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.SyncOperationProgress

/** The two secondary-device full-replace actions - both one-directional, unconditional, never a
 * merge. Only ever surfaced once there's an actual competing primary device to reconcile against;
 * a single-device user should never call either. Both report through `onProgress` and fail fast
 * with [dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.NETWORK] when offline. */
interface SyncReplaceRepository {
    /** Wipes this device's local sync-scoped state and replaces it with whatever's currently in
     * Firestore. All-or-nothing: every cloud read finishes before a single local row changes, so
     * an interrupted pull leaves this device untouched.
     * Fails with [dev.gouthaman.regimen.domain.model.SyncReplaceException]
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.WORKOUT_IN_PROGRESS]) while a
     * workout is in progress. Also resets the local freshness watermark to match, so a device
     * that failed that check can resume normal automatic sync afterward if it's already primary. */
    suspend fun pullCloudData(
        onProgress: suspend (SyncOperationProgress) -> Unit = {},
    ): Result<Unit>

    /** Claims primary, then wipes the account's Firestore data and replaces it with whatever's
     * currently local on this device. If interrupted, this device is left primary with its
     * stale-local-state UI, and running it again redoes the whole thing. Fails with
     * [dev.gouthaman.regimen.domain.model.SyncReplaceException]
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.PUSH_IN_PROGRESS]) if a push or
     * another claim is genuinely in flight right now, or
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.TAKEN_OVER]) if another device
     * claims primary while this one is still uploading. */
    suspend fun claimPrimary(
        onProgress: suspend (SyncOperationProgress) -> Unit = {},
    ): Result<Unit>

    /** This device's local workout count right now - populates the Claim confirmation's
     * "N workouts" copy. */
    suspend fun localWorkoutCount(): Int

    /** The signed-in account's cloud workout count, via a server-side `count()` aggregation - not
     * a full fetch, so populating the Pull confirmation doesn't burn read quota proportional to
     * history size. Throws [dev.gouthaman.regimen.domain.model.SyncReplaceException] on failure. */
    suspend fun cloudWorkoutCount(): Int
}
