package dev.gouthaman.regimen.domain.model

/** The primary device's sync push job's outcome, as of its last run. Four states, not a binary
 * success/fail, since a run can complete successfully while still leaving a capped backlog for
 * next time - that's normal progress, not a failure. Render in priority order: [lastError] != null
 * -> "Sync failed"; [isFullyUpToDate] -> "Synced"; [lastSyncedAt] != null && ![isFullyUpToDate] ->
 * "Backing up..."; [lastSyncedAt] == null -> "Not yet synced." */
data class SyncStatus(
    val lastSyncedAt: Long?,
    val isFullyUpToDate: Boolean,
    val lastError: AuthErrorReason?,
)
