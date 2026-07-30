package dev.gouthaman.regimen.sync.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.repository.SyncScheduleRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduleRepository {

    override fun schedulePeriodicPush() {
        val request = PeriodicWorkRequestBuilder<SyncPushWorker>(Duration.ofHours(24))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .build()
        // KEEP, not REPLACE - this is called on every sign-in, not just the first one; an
        // already-scheduled device shouldn't have its next run time reset every time.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelPeriodicPush() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    override suspend fun nextScheduledSyncAt(): Long? {
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
            .first()
        // WorkInfo.nextScheduleTimeMillis is Long.MAX_VALUE when the work is in a terminal state
        // (e.g. CANCELLED, which is exactly what a self-cancelled secondary device's job looks
        // like) rather than an actual upcoming time - that sentinel means "not really scheduled,"
        // same as an empty list.
        return workInfos.firstOrNull()?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sync_push_periodic"
    }
}
