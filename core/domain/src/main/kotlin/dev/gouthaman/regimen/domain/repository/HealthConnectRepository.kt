package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState

interface HealthConnectRepository {
    suspend fun getConnectionState(): HealthConnectConnectionState

    /** The Health Connect permission strings Regimen needs - passed by the UI layer to its own
     * permission-request launcher (only an Activity/Compose context can launch that). */
    fun requiredPermissions(): Set<String>

    /** Null if nothing was found for either record type in range. */
    suspend fun queryBiometrics(startTime: Long, endTime: Long): HealthConnectBiometricsSample?
}
