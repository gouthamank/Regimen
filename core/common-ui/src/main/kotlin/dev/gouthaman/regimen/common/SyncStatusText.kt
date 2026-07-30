package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.SyncStatus

/** Renders in the same priority order the sync design settled on: a failed run always shows as
 * failed regardless of any prior progress; a fully caught-up run shows as synced (with the
 * timestamp - [SessionFormat.timeWithDateIfNotToday], not plain [SessionFormat.time], since a
 * persisted status can be days old by the time this renders, and time-of-day alone would read as
 * "today" regardless of how stale it actually is); a capped-partial run (some progress, backlog
 * remains) shows as still working; otherwise nothing has ever synced. */
@Composable
fun SyncStatus.text(): String {
    val syncedAt = lastSyncedAt
    return when {
        lastError != null -> stringResource(R.string.sync_status_failed)
        isFullyUpToDate && syncedAt != null ->
            stringResource(
                R.string.sync_status_synced_at,
                SessionFormat.timeWithDateIfNotToday(syncedAt)
            )

        isFullyUpToDate -> stringResource(R.string.sync_status_synced)
        syncedAt != null -> stringResource(R.string.sync_status_backing_up)
        else -> stringResource(R.string.sync_status_not_yet_synced)
    }
}
