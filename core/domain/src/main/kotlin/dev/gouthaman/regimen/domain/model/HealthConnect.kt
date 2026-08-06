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

/** [autoPullEnabled] is off by default (explicit opt-in) and gates whether the backfill job is
 * scheduled at all - [retryFrequency]/[backfillWindow] are irrelevant while it's off. */
data class HealthConnectPrefs(
    val autoPullEnabled: Boolean = false,
    val retryFrequency: HealthConnectRetryFrequency = HealthConnectRetryFrequency.SIX_HOURS,
    val backfillWindow: HealthConnectBackfillWindow = HealthConnectBackfillWindow.SEVEN,
)
