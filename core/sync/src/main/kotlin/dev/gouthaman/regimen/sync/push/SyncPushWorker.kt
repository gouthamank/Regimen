package dev.gouthaman.regimen.sync.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository

/** The periodic background half of the primary device's incremental sync push - the manual
 * "Sync now" entry point calls [SyncPushRunner] directly instead, since it has no need for
 * WorkManager's scheduling/retry machinery. */
@HiltWorker
class SyncPushWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncPushRunner: SyncPushRunner,
    private val syncDeviceRepository: SyncDeviceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // A device that's lost primary status has nothing to do here ever again until it's
        // re-claimed - rather than firing and no-opping forever, it cancels its own schedule.
        if (!syncDeviceRepository.isPrimary()) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(SyncSchedulerImpl.UNIQUE_WORK_NAME)
            return Result.success()
        }

        val status = syncPushRunner.push()
        // WorkManager's own BackoffPolicy.EXPONENTIAL handles the retry timing - this just decides
        // whether a retry is warranted at all, not when.
        return if (status.lastError != null) Result.retry() else Result.success()
    }
}
