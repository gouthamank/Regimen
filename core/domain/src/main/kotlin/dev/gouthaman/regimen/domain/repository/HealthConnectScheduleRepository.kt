package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency

interface HealthConnectScheduleRepository {
    /** (Re)schedules the periodic backfill job at [frequency] - replaces any existing schedule,
     * unlike the sync push job's KEEP policy, since a frequency change needs to take effect on
     * its next run rather than waiting out whatever interval was previously in force. */
    fun schedulePeriodicBackfill(frequency: HealthConnectRetryFrequency)

    /** Cancels the periodic backfill job outright - called when the auto-pull toggle turns off. */
    fun cancelPeriodicBackfill()
}
