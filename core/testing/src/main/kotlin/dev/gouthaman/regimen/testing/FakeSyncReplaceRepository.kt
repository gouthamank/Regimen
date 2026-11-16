package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.SyncOperationProgress
import dev.gouthaman.regimen.domain.repository.SyncReplaceRepository

class FakeSyncReplaceRepository(
    private val pullResult: Result<Unit> = Result.success(Unit),
    private val claimResult: Result<Unit> = Result.success(Unit),
    var localWorkoutCountResult: Int = 0,
    var cloudWorkoutCountResult: Result<Int> = Result.success(0),
) : SyncReplaceRepository {

    var pullCalled = false
        private set
    var claimCalled = false
        private set

    override suspend fun pullCloudData(
        onProgress: suspend (SyncOperationProgress) -> Unit,
    ): Result<Unit> {
        pullCalled = true
        return pullResult
    }

    override suspend fun claimPrimary(
        onProgress: suspend (SyncOperationProgress) -> Unit,
    ): Result<Unit> {
        claimCalled = true
        return claimResult
    }

    override suspend fun localWorkoutCount(): Int = localWorkoutCountResult

    override suspend fun cloudWorkoutCount(): Int = cloudWorkoutCountResult.getOrThrow()
}
