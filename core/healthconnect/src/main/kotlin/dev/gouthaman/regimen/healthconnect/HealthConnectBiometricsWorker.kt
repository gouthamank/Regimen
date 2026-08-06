package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.gouthaman.regimen.domain.repository.HealthConnectPrefsRepository
import dev.gouthaman.regimen.domain.usecase.RunBiometricsBackfillUseCase
import kotlinx.coroutines.flow.first

@HiltWorker
class HealthConnectBiometricsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runBiometricsBackfillUseCase: RunBiometricsBackfillUseCase,
    private val healthConnectPrefsRepository: HealthConnectPrefsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = healthConnectPrefsRepository.prefs.first()
        // The toggle may have flipped off after this run was already queued - self-cancel rather
        // than running once more on a stale schedule.
        if (!prefs.autoPullEnabled) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(HealthConnectSchedulerImpl.UNIQUE_WORK_NAME)
            return Result.success()
        }

        return runCatching { runBiometricsBackfillUseCase(prefs.backfillWindow) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }
}
