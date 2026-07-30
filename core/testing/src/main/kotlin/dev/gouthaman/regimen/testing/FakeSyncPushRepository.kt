package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.domain.repository.SyncPushRepository

class FakeSyncPushRepository(
    private val result: SyncStatus = SyncStatus(
        lastSyncedAt = 0L,
        isFullyUpToDate = true,
        lastError = null
    ),
    private val lastStatus: SyncStatus = SyncStatus(
        lastSyncedAt = null,
        isFullyUpToDate = false,
        lastError = null
    ),
) : SyncPushRepository {

    var pushCalled = false
        private set

    override suspend fun push(): SyncStatus {
        pushCalled = true
        return result
    }

    override suspend fun getLastStatus(): SyncStatus = lastStatus
}
