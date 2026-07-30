package dev.gouthaman.regimen.domain.repository

interface SyncScheduleRepository {
    /** Enqueues the primary device's periodic push job (every 24h, network-constrained) if it
     * isn't already scheduled - idempotent, safe to call on every sign-in regardless of whether
     * this device turns out to be primary. A device that isn't primary self-cancels its own
     * schedule the first time its job actually runs and finds that out, rather than needing this
     * call site to know primary status up front. */
    fun schedulePeriodicPush()

    /** Cancels the periodic push job outright - called on sign-out, since there's no signed-in
     * account left to sync for. */
    fun cancelPeriodicPush()

    /** The actual next run time WorkManager itself has scheduled for the periodic push job -
     * read directly off its own `WorkInfo`, not computed/guessed here. `null` if there's no
     * scheduled work at all (never scheduled, or cancelled - e.g. a secondary device whose job
     * already self-cancelled after discovering it isn't primary). */
    suspend fun nextScheduledSyncAt(): Long?
}
