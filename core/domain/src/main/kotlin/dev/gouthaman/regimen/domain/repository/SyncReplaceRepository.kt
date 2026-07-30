package dev.gouthaman.regimen.domain.repository

/** The two secondary-device full-replace actions - both one-directional, unconditional, never a
 * merge. Only ever surfaced once there's an actual competing primary device to reconcile against;
 * a single-device user should never call either. */
interface SyncReplaceRepository {
    /** Wipes this device's local sync-scoped state and replaces it with whatever's currently in
     * Firestore. Fails with [dev.gouthaman.regimen.domain.model.SyncReplaceException]
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.WORKOUT_IN_PROGRESS]) while a
     * workout is in progress. Also resets the local freshness watermark to match, so a device
     * that failed that check can resume normal automatic sync afterward if it's already primary. */
    suspend fun pullCloudData(): Result<Unit>

    /** Wipes the account's Firestore data and replaces it with whatever's currently local on this
     * device, then becomes the new primary. Fails with
     * [dev.gouthaman.regimen.domain.model.SyncReplaceException]
     * ([dev.gouthaman.regimen.domain.model.SyncReplaceErrorReason.PUSH_IN_PROGRESS]) if another
     * device's push is genuinely in flight right now. */
    suspend fun claimPrimary(): Result<Unit>

    /** This device's local workout count right now - populates both confirmations' "N workouts"
     * copy. */
    suspend fun localWorkoutCount(): Int

    /** The signed-in account's cloud workout count, via a server-side `count()` aggregation - not
     * a full fetch, so populating a confirmation dialog doesn't burn read quota proportional to
     * history size. */
    suspend fun cloudWorkoutCount(): Int
}
