package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gouthaman.regimen.domain.model.HealthConnectBiometricsSample
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Gates ACTIVE - both required for Regimen to be useful at all.
private val CORE_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
)

@Singleton
class HealthConnectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectRepository {

    // Deferred, not a Hilt singleton - getOrCreate() throws when the SDK isn't installed, and
    // that must surface as UNAVAILABLE, not crash app startup.
    private fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    // Not every Health Connect version supports background reads - never request a permission
    // the current provider can't grant.
    override fun requiredPermissions(): Set<String> {
        val client = clientOrNull() ?: return CORE_PERMISSIONS
        val backgroundReadAvailable = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return if (backgroundReadAvailable) {
            CORE_PERMISSIONS + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
        } else {
            CORE_PERMISSIONS
        }
    }

    override fun coreReadPermissions(): Set<String> = CORE_PERMISSIONS

    override suspend fun getConnectionState(): HealthConnectConnectionState {
        clientOrNull() ?: return HealthConnectConnectionState.UNAVAILABLE
        return if (getGrantedPermissions().containsAll(CORE_PERMISSIONS)) {
            HealthConnectConnectionState.ACTIVE
        } else {
            HealthConnectConnectionState.NEEDS_PERMISSION
        }
    }

    override suspend fun getGrantedPermissions(): Set<String> =
        clientOrNull()?.permissionController?.getGrantedPermissions() ?: emptySet()

    override fun resolveAppLabel(packageName: String): String? = runCatching {
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrNull()

    override suspend fun queryBiometrics(
        startTime: Long,
        endTime: Long,
    ): HealthConnectBiometricsSample? {
        val client = clientOrNull() ?: return null
        val range = TimeRangeFilter.between(
            Instant.ofEpochMilli(startTime),
            Instant.ofEpochMilli(endTime),
        )

        val heartRateRecords = client.readRecords(
            ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range),
        ).records
        val calorieRecords = client.readRecords(
            ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = range,
            ),
        ).records

        val allBpm = heartRateRecords.flatMap { it.samples }.map { it.beatsPerMinute }
        val avgBpm = allBpm.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val maxBpm = allBpm.maxOrNull()?.toInt()
        val activeCaloriesKcal = calorieRecords.takeIf { it.isNotEmpty() }
            ?.sumOf { it.energy.inKilocalories }

        if (avgBpm == null && activeCaloriesKcal == null) return null

        // List<Record> forced - the inferred supertype (IntervalRecord) is internal to the library.
        val allRecords: List<Record> = heartRateRecords + calorieRecords
        val sourcePackageName = allRecords.maxByOrNull { it.metadata.lastModifiedTime }
            ?.metadata?.dataOrigin?.packageName

        return HealthConnectBiometricsSample(
            avgBpm = avgBpm,
            maxBpm = maxBpm,
            activeCaloriesKcal = activeCaloriesKcal,
            sourcePackageName = sourcePackageName,
        )
    }
}
