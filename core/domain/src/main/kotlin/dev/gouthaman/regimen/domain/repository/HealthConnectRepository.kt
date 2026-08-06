package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HeartRateSample

interface HealthConnectRepository {
    suspend fun getConnectionState(): HealthConnectConnectionState

    /** Every Health Connect permission Regimen needs, core and optional (background read) alike. */
    fun requiredPermissions(): Set<String>

    /** Just heart rate + calories - requested on its own, never bundled with the optional
     * background permission. Bundling them risks a single `USER_FIXED` background permission
     * (denied enough times previously that Android refuses to prompt again) silently aborting the
     * *entire* request, including the core permissions that were never denied. */
    fun coreReadPermissions(): Set<String>

    /** Every Health Connect permission Regimen currently holds, core and optional alike - lets a
     * caller diff this against [requiredPermissions] to spot an optional permission (background
     * reads) that's become available since the last grant, without re-deriving the connection
     * state check itself. */
    suspend fun getGrantedPermissions(): Set<String>

    /** The installed app's user-facing label for [packageName] (e.g. "Google Health"), or null if
     * it can't be resolved - for attributing which app last wrote the data Regimen pulled. */
    fun resolveAppLabel(packageName: String): String?

    /** Null if nothing was found for either record type in range. */
    suspend fun queryBiometrics(startTime: Long, endTime: Long): HealthConnectBiometricsSample?

    /** Raw heart-rate samples in `[startTime, endTime]`, chronological order, empty if none found -
     * a live on-demand read, never persisted (unlike [queryBiometrics]). */
    suspend fun getHeartRateSeries(startTime: Long, endTime: Long): List<HeartRateSample>
}
