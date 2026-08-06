package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.repository.HealthConnectScheduleRepository
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectScheduleRepository {

    override fun schedulePeriodicBackfill(frequency: HealthConnectRetryFrequency) {
        val request =
            PeriodicWorkRequestBuilder<HealthConnectBiometricsWorker>(Duration.ofHours(frequency.hours))
                .build()
        // REPLACE, not KEEP - a frequency change must take effect on its next run rather than
        // waiting out whatever interval was already in force (unlike the sync push job, which is
        // never rescheduled with a different interval after its first schedule call).
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancelPeriodicBackfill() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health_connect_backfill_periodic"
    }
}
