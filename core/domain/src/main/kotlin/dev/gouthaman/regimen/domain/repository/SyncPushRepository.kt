package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.SyncStatus

interface SyncPushRepository {
    /** Runs one incremental push (or a no-op if this device isn't primary/nobody's signed in) and
     * returns its outcome. Safe to call from anywhere - the primary-status check is internal, not
     * the caller's responsibility. */
    suspend fun push(): SyncStatus

    /** The last run's persisted outcome, surviving app restarts - a fresh install/first-ever-run
     * reads back as [SyncStatus]'s "not yet synced" shape (all fields null/false). */
    suspend fun getLastStatus(): SyncStatus
}
