package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.repository.SyncScheduleRepository

class FakeSyncScheduleRepository(
    var nextScheduledSyncAtResult: Long? = null,
) : SyncScheduleRepository {

    var scheduleCalled = false
        private set
    var cancelCalled = false
        private set

    override fun schedulePeriodicPush() {
        scheduleCalled = true
    }

    override fun cancelPeriodicPush() {
        cancelCalled = true
    }

    override suspend fun nextScheduledSyncAt(): Long? = nextScheduledSyncAtResult
}
