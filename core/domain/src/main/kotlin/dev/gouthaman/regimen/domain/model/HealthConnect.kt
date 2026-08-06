package dev.gouthaman.regimen.domain.model

/** ACTIVE = installed, up to date, and permission granted. NEEDS_PERMISSION = installed and up
 * to date but Regimen hasn't been granted the required read permissions (or they were revoked).
 * UNAVAILABLE = not installed, or installed but needs updating - both resolve the same way from
 * the user's side (go to the Play Store), so they're not split into separate states. */
enum class HealthConnectConnectionState {
    ACTIVE,
    NEEDS_PERMISSION,
    UNAVAILABLE,
}

/** One query's worth of heart-rate/calorie data pulled from Health Connect for a single workout's
 * time range. A null field means that particular record type had nothing in range. */
data class HealthConnectBiometricsSample(
    val avgBpm: Int?,
    val maxBpm: Int?,
    val activeCaloriesKcal: Double?,
    val sourcePackageName: String?,
)

/** [healthConnectEnabled] is a plain feature opt-in, off by default - independent of whether
 * Android permission has been granted. [backgroundSyncEnabled] is a second, separate opt-in -
 * granting the background-read permission doesn't imply this is on; the periodic job only
 * schedules while both this and the permission are true (reconciled separately). */
data class HealthConnectPrefs(
    val healthConnectEnabled: Boolean = false,
    val backgroundSyncEnabled: Boolean = false,
    val retryFrequency: HealthConnectRetryFrequency = HealthConnectRetryFrequency.SIX_HOURS,
)

/** Everything the Settings status widget needs. [corePermissions] and [requiredPermissions] are
 * requested separately by the UI, never combined into one launch - see
 * [dev.gouthaman.regimen.domain.repository.HealthConnectRepository.coreReadPermissions]. */
data class HealthConnectStatus(
    val connectionState: HealthConnectConnectionState,
    val hasOptionalPermissionAvailable: Boolean,
    val detectedSourceAppLabel: String?,
    val lastPulledAt: Long?,
    val requiredPermissions: Set<String>,
    val corePermissions: Set<String>,
)

/** One raw heart-rate reading from Health Connect's sample series - never persisted. */
data class HeartRateSample(val time: Long, val bpm: Long)

/** A single backfill sweep's outcome - [candidateCount] is how many workouts were missing
 * biometrics and in range, [pulledCount] is how many of those actually found data. Lets "Pull
 * now" tell a user "nothing needed checking" apart from "checked some, found nothing yet" apart
 * from "found data for N" - a completed pull is never silent. */
data class BiometricsBackfillResult(
    val candidateCount: Int,
    val pulledCount: Int,
)
