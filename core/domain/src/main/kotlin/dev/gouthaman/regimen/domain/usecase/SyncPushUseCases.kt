package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.SyncStatus
import dev.gouthaman.regimen.domain.repository.SyncPushRepository
import dev.gouthaman.regimen.domain.repository.SyncScheduleRepository
import javax.inject.Inject

/** The Account screen's manual "Sync now" - an out-of-band run of the same incremental push the
 * periodic job does, not a destructive action, so it needs no confirmation dialog. */
class SyncNowUseCase @Inject constructor(
    private val repo: SyncPushRepository,
) {
    suspend operator fun invoke(): SyncStatus = repo.push()
}

/** The Account screen reads this once on load to restore [SyncStatus] across app restarts,
 * before any fresh "Sync now"/periodic run has happened this session. */
class GetLastSyncStatusUseCase @Inject constructor(
    private val repo: SyncPushRepository,
) {
    suspend operator fun invoke(): SyncStatus = repo.getLastStatus()
}

class SchedulePeriodicSyncUseCase @Inject constructor(
    private val repo: SyncScheduleRepository,
) {
    operator fun invoke() = repo.schedulePeriodicPush()
}

class CancelPeriodicSyncUseCase @Inject constructor(
    private val repo: SyncScheduleRepository,
) {
    operator fun invoke() = repo.cancelPeriodicPush()
}

/** The Account screen's "Next sync scheduled at ..." row - reads WorkManager's own computed
 * next-run time for the periodic push job, not a guess based on the interval. */
class GetNextScheduledSyncAtUseCase @Inject constructor(
    private val repo: SyncScheduleRepository,
) {
    suspend operator fun invoke(): Long? = repo.nextScheduledSyncAt()
}
