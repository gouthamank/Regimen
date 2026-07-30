package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.repository.SyncReplaceRepository
import javax.inject.Inject

class PullCloudDataUseCase @Inject constructor(
    private val repo: SyncReplaceRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repo.pullCloudData()
}

class ClaimPrimaryUseCase @Inject constructor(
    private val repo: SyncReplaceRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repo.claimPrimary()
}

class LocalWorkoutCountUseCase @Inject constructor(
    private val repo: SyncReplaceRepository,
) {
    suspend operator fun invoke(): Int = repo.localWorkoutCount()
}

class CloudWorkoutCountUseCase @Inject constructor(
    private val repo: SyncReplaceRepository,
) {
    suspend operator fun invoke(): Int = repo.cloudWorkoutCount()
}
