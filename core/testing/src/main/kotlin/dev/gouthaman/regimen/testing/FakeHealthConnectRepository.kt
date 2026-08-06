package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository

class FakeHealthConnectRepository(
    var connectionState: HealthConnectConnectionState = HealthConnectConnectionState.ACTIVE,
    var sampleForRange: HealthConnectBiometricsSample? = null,
) : HealthConnectRepository {

    val queriedRanges = mutableListOf<Pair<Long, Long>>()
    val lastQueriedRange: Pair<Long, Long>? get() = queriedRanges.lastOrNull()

    override suspend fun getConnectionState(): HealthConnectConnectionState = connectionState

    override fun requiredPermissions(): Set<String> =
        setOf("android.permission.health.READ_HEART_RATE")

    override suspend fun queryBiometrics(
        startTime: Long,
        endTime: Long
    ): HealthConnectBiometricsSample? {
        queriedRanges += startTime to endTime
        return sampleForRange
    }
}
