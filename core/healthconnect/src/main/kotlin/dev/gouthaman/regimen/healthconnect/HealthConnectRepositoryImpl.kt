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

// Gates HealthConnectConnectionState.ACTIVE - both required for Regimen to be useful at all.
private val CORE_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
)

@Singleton
class HealthConnectRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : HealthConnectRepository {

    // Deferred rather than a Hilt-provided singleton: HealthConnectClient.getOrCreate() throws
    // when the SDK isn't available, and that must never crash app startup on a device without
    // Health Connect - only ever surface as HealthConnectConnectionState.UNAVAILABLE.
    private fun clientOrNull(): HealthConnectClient? =
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }

    // Background reads (needed for the periodic backfill job to work while the app isn't in the
    // foreground) aren't supported by every Health Connect version, and the request must not ask
    // for a permission the current provider can't grant.
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

    override suspend fun getConnectionState(): HealthConnectConnectionState {
        val client = clientOrNull() ?: return HealthConnectConnectionState.UNAVAILABLE
        val granted = client.permissionController.getGrantedPermissions()
        return if (granted.containsAll(CORE_PERMISSIONS)) {
            HealthConnectConnectionState.ACTIVE
        } else {
            HealthConnectConnectionState.NEEDS_PERMISSION
        }
    }

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

        // Explicitly typed as List<Record> - the inferred common supertype of HeartRateRecord and
        // ActiveCaloriesBurnedRecord is IntervalRecord, which is internal to the health-connect
        // library and can't be accessed from here otherwise.
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
