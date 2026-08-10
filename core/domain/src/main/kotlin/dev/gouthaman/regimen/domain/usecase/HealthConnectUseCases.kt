package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.BiometricsBackfillResult
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.model.HealthConnectStatus
import dev.gouthaman.regimen.domain.model.HeartRateSample
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.repository.HealthConnectPrefsRepository
import dev.gouthaman.regimen.domain.repository.HealthConnectRepository
import dev.gouthaman.regimen.domain.repository.HealthConnectScheduleRepository
import dev.gouthaman.regimen.domain.repository.WorkoutBiometricsRepository
import dev.gouthaman.regimen.domain.repository.WorkoutRepository
import dev.gouthaman.regimen.domain.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Fixed, not user-configurable - applies uniformly to both manual "Check now" and the periodic
 * job. Already-fetched workouts are always excluded from candidates regardless of window size,
 * so a wide fixed window costs nothing. */
private const val BACKFILL_WINDOW_DAYS = 30L

/**
 * Queries Health Connect for [workoutId]'s `[startTime, endTime]` and persists whatever's found
 * as a [WorkoutBiometrics] row, overwriting any existing one but preserving its
 * [WorkoutBiometrics.heartRateSeries]. Returns whether anything was found.
 */
class PullBiometricsForWorkoutUseCase @Inject constructor(
    private val healthConnectRepo: HealthConnectRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(workoutId: String): Boolean {
        val workout = workoutRepo.getWorkout(workoutId)?.workout ?: return false
        val endTime = workout.endTime ?: return false
        val sample = healthConnectRepo.queryBiometrics(workout.startTime, endTime) ?: return false
        val existing = workoutBiometricsRepo.get(workoutId)

        workoutBiometricsRepo.upsert(
            WorkoutBiometrics(
                id = existing?.id.orEmpty(),
                workoutId = workoutId,
                avgBpm = sample.avgBpm,
                maxBpm = sample.maxBpm,
                activeCaloriesKcal = sample.activeCaloriesKcal,
                sourcePackageName = sample.sourcePackageName,
                fetchedAt = clock.nowMillis(),
                heartRateSeries = existing?.heartRateSeries,
            ),
        )
        return true
    }
}

/**
 * Re-pulls every `COMPLETE` workout in the fixed [BACKFILL_WINDOW_DAYS] window, including ones
 * already pulled - Health Connect sync can fill in more data later, and this is a cheap on-device
 * read, not a network call.
 */
class RunBiometricsBackfillUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val pullBiometricsForWorkoutUseCase: PullBiometricsForWorkoutUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): BiometricsBackfillResult {
        val now = clock.nowMillis()
        val sinceStartTime = now - BACKFILL_WINDOW_DAYS * DAY_MILLIS
        val candidateIds = workoutRepo.observeCompletedBetween(sinceStartTime, now).first()
            .map { it.id }
        val pulledCount = candidateIds.count { pullBiometricsForWorkoutUseCase(it) }
        return BiometricsBackfillResult(
            candidateCount = candidateIds.size,
            pulledCount = pulledCount,
        )
    }
}

/** Everything the Settings status widget needs, in one call. */
class GetHealthConnectStatusUseCase @Inject constructor(
    private val healthConnectRepo: HealthConnectRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
) {
    suspend operator fun invoke(): HealthConnectStatus {
        val connectionState = healthConnectRepo.getConnectionState()
        val requiredPermissions = healthConnectRepo.requiredPermissions()
        val hasOptionalPermissionAvailable =
            connectionState == HealthConnectConnectionState.ACTIVE &&
                    !healthConnectRepo.getGrantedPermissions().containsAll(requiredPermissions)
        val mostRecent = workoutBiometricsRepo.getMostRecentlyFetched()
        // Falls back to the raw package name (not omitted) if the source app has since been
        // uninstalled and its label can no longer be resolved.
        val detectedSourceAppLabel = mostRecent?.sourcePackageName
            ?.let { healthConnectRepo.resolveAppLabel(it) ?: it }
        return HealthConnectStatus(
            connectionState = connectionState,
            hasOptionalPermissionAvailable = hasOptionalPermissionAvailable,
            detectedSourceAppLabel = detectedSourceAppLabel,
            lastPulledAt = mostRecent?.fetchedAt,
            requiredPermissions = requiredPermissions,
            corePermissions = healthConnectRepo.coreReadPermissions(),
        )
    }
}

/** Plain Flow passthrough, same shape as [ObserveWorkoutUseCase] et al. */
class ObserveHealthConnectPrefsUseCase @Inject constructor(
    private val prefsRepo: HealthConnectPrefsRepository,
) {
    operator fun invoke(): Flow<HealthConnectPrefs> = prefsRepo.prefs
}

/** Plain Flow passthrough, same shape as [ObserveHealthConnectPrefsUseCase]. */
class ObserveWorkoutBiometricsUseCase @Inject constructor(
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
) {
    operator fun invoke(workoutId: String): Flow<WorkoutBiometrics?> =
        workoutBiometricsRepo.observe(workoutId)
}

/** Recomputes whether the periodic backfill job should be running and (re)schedules or cancels
 * it accordingly - the single source of truth, so the schedule can never drift from live
 * permission/connection state (e.g. permission revoked after the feature was left enabled). */
class ReconcileHealthConnectScheduleUseCase @Inject constructor(
    private val healthConnectRepo: HealthConnectRepository,
    private val prefsRepo: HealthConnectPrefsRepository,
    private val scheduleRepo: HealthConnectScheduleRepository,
) {
    suspend operator fun invoke() {
        val prefs = prefsRepo.prefs.first()
        val eligible = prefs.healthConnectEnabled &&
                prefs.backgroundSyncEnabled &&
                healthConnectRepo.getConnectionState() == HealthConnectConnectionState.ACTIVE &&
                healthConnectRepo.getGrantedPermissions()
                    .containsAll(healthConnectRepo.requiredPermissions())
        if (eligible) {
            scheduleRepo.schedulePeriodicBackfill(prefs.retryFrequency)
        } else {
            scheduleRepo.cancelPeriodicBackfill()
        }
    }
}

/** The Health Connect settings that can be changed from Settings - each reconciles the backfill
 * job's schedule afterward rather than scheduling/cancelling directly, since eligibility also
 * depends on live connection/permission state, not just these prefs. */
class SetHealthConnectPrefsUseCase @Inject constructor(
    private val prefsRepo: HealthConnectPrefsRepository,
    private val reconcileSchedule: ReconcileHealthConnectScheduleUseCase,
) {
    suspend fun setHealthConnectEnabled(value: Boolean) {
        prefsRepo.setHealthConnectEnabled(value)
        reconcileSchedule()
    }

    /** Turning this off only stops scheduling - it can't also revoke just the background
     * permission, since Health Connect's `PermissionController` only exposes
     * `revokeAllPermissions()` (every permission Regimen holds, not a subset), which would be far
     * too destructive for this one toggle. The permission itself stays granted; a user who wants
     * it gone has to revoke it manually via Health Connect's own app. */
    suspend fun setBackgroundSyncEnabled(value: Boolean) {
        prefsRepo.setBackgroundSyncEnabled(value)
        reconcileSchedule()
    }

    suspend fun setRetryFrequency(value: HealthConnectRetryFrequency) {
        prefsRepo.setRetryFrequency(value)
        reconcileSchedule()
    }
}

/** Fixed point resolution for the heart-rate chart, regardless of workout length. */
private const val HEART_RATE_CHART_BUCKET_WIDTH_MILLIS = 10_000L

/** Chart series for one workout - queries Health Connect live first, so a re-check can pick up
 * newly-synced data, falling back to the cached [WorkoutBiometrics.heartRateSeries] only if
 * nothing's found live. Caches onto an existing row only, never creates a bare one. */
class GetHeartRateSeriesForWorkoutUseCase @Inject constructor(
    private val healthConnectRepo: HealthConnectRepository,
    private val workoutRepo: WorkoutRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
) {
    suspend operator fun invoke(workoutId: String): List<Float> {
        val workout = workoutRepo.getWorkout(workoutId)?.workout ?: return emptyList()
        val endTime = workout.endTime ?: return emptyList()
        val existing = workoutBiometricsRepo.get(workoutId)

        val samples = healthConnectRepo.getHeartRateSeries(workout.startTime, endTime)
        val bucketCount =
            ((endTime - workout.startTime) / HEART_RATE_CHART_BUCKET_WIDTH_MILLIS).toInt()
                .coerceAtLeast(1)
        val livePoints = bucketAverages(samples, workout.startTime, endTime, bucketCount)
        if (livePoints.isNotEmpty()) {
            if (existing != null) {
                workoutBiometricsRepo.upsert(
                    existing.copy(heartRateSeries = livePoints.map { it.roundToInt() }),
                )
            }
            return livePoints
        }

        return existing?.heartRateSeries?.takeIf { it.isNotEmpty() }?.map { it.toFloat() }
            ?: emptyList()
    }
}

/** Averages samples into [bucketCount] equal-width time buckets, linearly interpolating any empty
 * ones (edges carry forward/back from the nearest filled bucket) - always [bucketCount] points, so
 * a point's index reliably maps to a fixed elapsed time for the chart's x-axis. */
internal fun bucketAverages(
    samples: List<HeartRateSample>,
    startTime: Long,
    endTime: Long,
    bucketCount: Int,
): List<Float> {
    if (samples.isEmpty() || endTime <= startTime) return emptyList()
    val bucketWidth = (endTime - startTime).toDouble() / bucketCount
    val sums = DoubleArray(bucketCount)
    val counts = IntArray(bucketCount)
    for (sample in samples) {
        val index = ((sample.time - startTime) / bucketWidth).toInt().coerceIn(0, bucketCount - 1)
        sums[index] += sample.bpm
        counts[index]++
    }
    val raw =
        DoubleArray(bucketCount) { if (counts[it] == 0) Double.NaN else sums[it] / counts[it] }
    return fillGaps(raw).map { it.toFloat() }
}

/** Forward/back-fills leading/trailing NaN runs from the nearest real value, linearly interpolates
 * interior runs between their two neighbors. */
private fun fillGaps(values: DoubleArray): DoubleArray {
    val result = values.copyOf()
    var i = 0
    while (i < result.size) {
        if (!result[i].isNaN()) {
            i++
            continue
        }
        var j = i
        while (j < result.size && result[j].isNaN()) j++
        val left = if (i == 0) null else result[i - 1]
        val right = if (j == result.size) null else result[j]
        for (k in i until j) {
            result[k] = when {
                left == null -> right!!
                right == null -> left
                else -> left + (right - left) * (k - i + 1).toDouble() / (j - i + 1)
            }
        }
        i = j
    }
    return result
}

/** Wipes every pulled `WorkoutBiometrics` row - a hard delete, not a tombstone, since this data
 * is a local cache of Health Connect, not a durable record. Only reachable while the feature is
 * opted out, so it has no effect on the periodic job's own schedule. */
class DeleteHealthConnectDataUseCase @Inject constructor(
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
) {
    suspend operator fun invoke() = workoutBiometricsRepo.deleteAll()
}
