package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.repository.HealthConnectScheduleRepository

class FakeHealthConnectScheduleRepository : HealthConnectScheduleRepository {

    var scheduledFrequency: HealthConnectRetryFrequency? = null
        private set
    var cancelCalled = false
        private set

    override fun schedulePeriodicBackfill(frequency: HealthConnectRetryFrequency) {
        scheduledFrequency = frequency
    }

    override fun cancelPeriodicBackfill() {
        cancelCalled = true
        scheduledFrequency = null
    }
}
