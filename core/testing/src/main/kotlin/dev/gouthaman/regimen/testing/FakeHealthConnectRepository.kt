package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository

class FakeHealthConnectRepository(
    var connectionState: HealthConnectConnectionState = HealthConnectConnectionState.ACTIVE,
    var sampleForRange: HealthConnectBiometricsSample? = null,
    var requiredPermissionsResult: Set<String> = setOf("android.permission.health.READ_HEART_RATE"),
    var grantedPermissionsResult: Set<String> = requiredPermissionsResult,
    var corePermissionsResult: Set<String> = requiredPermissionsResult,
    var appLabels: Map<String, String> = emptyMap(),
) : HealthConnectRepository {

    val queriedRanges = mutableListOf<Pair<Long, Long>>()
    val lastQueriedRange: Pair<Long, Long>? get() = queriedRanges.lastOrNull()

    override suspend fun getConnectionState(): HealthConnectConnectionState = connectionState

    override fun requiredPermissions(): Set<String> = requiredPermissionsResult

    override fun coreReadPermissions(): Set<String> = corePermissionsResult

    override suspend fun getGrantedPermissions(): Set<String> = grantedPermissionsResult

    override fun resolveAppLabel(packageName: String): String? = appLabels[packageName]

    override suspend fun queryBiometrics(
        startTime: Long,
        endTime: Long
    ): HealthConnectBiometricsSample? {
        queriedRanges += startTime to endTime
        return sampleForRange
    }
}
