package dev.gouthaman.regimen.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.gouthaman.regimen.domain.model.SyncStatus

/** Priority order: a failed run always shows as failed regardless of prior progress; fully
 * caught-up shows as synced (using [SessionFormat.timeWithDateIfNotToday], not plain
 * [SessionFormat.time], since a persisted status can be days old); partial progress shows as
 * still working; otherwise nothing has ever synced. */
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
