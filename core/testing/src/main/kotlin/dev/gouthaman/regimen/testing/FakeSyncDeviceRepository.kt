package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository

class FakeSyncDeviceRepository(
    private val ensurePrimaryClaimedResult: Boolean = true,
    var isPrimaryResult: Boolean = true,
    var secondaryDeviceReasonResult: SecondaryDeviceReason? = null,
) : SyncDeviceRepository {

    var ensurePrimaryClaimedCalled = false
        private set

    override suspend fun ensurePrimaryClaimed(): Boolean {
        ensurePrimaryClaimedCalled = true
        return ensurePrimaryClaimedResult
    }

    override suspend fun isPrimary(): Boolean = isPrimaryResult

    override suspend fun secondaryDeviceReason(): SecondaryDeviceReason? =
        secondaryDeviceReasonResult
}
