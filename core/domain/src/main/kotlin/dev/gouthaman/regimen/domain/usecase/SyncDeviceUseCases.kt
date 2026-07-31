package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason
import dev.gouthaman.regimen.domain.repository.SyncDeviceRepository
import javax.inject.Inject

class EnsurePrimaryClaimedUseCase @Inject constructor(
    private val repo: SyncDeviceRepository,
) {
    suspend operator fun invoke(): Boolean = repo.ensurePrimaryClaimed()
}

class IsPrimaryUseCase @Inject constructor(
    private val repo: SyncDeviceRepository,
) {
    suspend operator fun invoke(): Boolean = repo.isPrimary()
}

class GetSecondaryDeviceReasonUseCase @Inject constructor(
    private val repo: SyncDeviceRepository,
) {
    suspend operator fun invoke(): SecondaryDeviceReason? = repo.secondaryDeviceReason()
}
