package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.SyncStatus

interface SyncPushRepository {
    /** Runs one incremental push (or a no-op if this device isn't primary/nobody's signed in) and
     * returns its outcome. Safe to call from anywhere - the primary-status check is internal, not
     * the caller's responsibility. */
    suspend fun push(): SyncStatus

    /** Same push loop as [push], for a caller that has *already* claimed
     * `syncConfig.lockedAt` itself (currently only "Claim primary"'s own force-push) - skips
     * [push]'s own lock check and doesn't take or release the lock itself, since here that lock is
     * this same call chain's, not a foreign one, and skips the freshness-watermark check, since the
     * whole point is to force a full re-upload regardless of watermark state. Unlike [push], keeps
     * running passes until nothing is left dirty rather than stopping at one pass's cap.
     * [onCommitted] hears each confirmed batch's row count. Not for general use - calling this
     * without already holding the lock removes the safety [push] otherwise gives against two
     * concurrent writers. */
    suspend fun forcePush(onCommitted: suspend (Int) -> Unit = {}): SyncStatus

    /** The last run's persisted outcome, surviving app restarts - a fresh install/first-ever-run
     * reads back as [SyncStatus]'s "not yet synced" shape (all fields null/false). */
    suspend fun getLastStatus(): SyncStatus
}
