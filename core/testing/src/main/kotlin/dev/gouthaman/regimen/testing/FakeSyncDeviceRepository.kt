package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository

class FakeSyncDeviceRepository(
    private val ensurePrimaryClaimedResult: Boolean = true,
) : SyncDeviceRepository {

    var ensurePrimaryClaimedCalled = false
        private set

    override suspend fun ensurePrimaryClaimed(): Boolean {
        ensurePrimaryClaimedCalled = true
        return ensurePrimaryClaimedResult
    }
}
