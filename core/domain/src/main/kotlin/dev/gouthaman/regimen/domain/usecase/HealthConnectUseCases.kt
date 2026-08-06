package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.BiometricsBackfillResult
import dev.gouthaman.regimen.domain.model.HealthConnectConnectionState
import dev.gouthaman.regimen.domain.model.HealthConnectPrefs
import dev.gouthaman.regimen.domain.model.HealthConnectRetryFrequency
import dev.gouthaman.regimen.domain.model.HealthConnectStatus
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

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Fixed, not user-configurable - applies uniformly to both manual "Check now" and the periodic
 * job. Already-fetched workouts are always excluded from candidates regardless of window size,
 * so a wide fixed window costs nothing. */
private const val BACKFILL_WINDOW_DAYS = 30L

/**
 * Queries Health Connect for [workoutId]'s `[startTime, endTime]` and persists whatever's found
 * as a [WorkoutBiometrics] row. Returns whether anything was actually found - lets a retry/backfill
 * job tell "pulled" apart from "still nothing there yet, try again later". No-op (returns false)
 * for a workout that's missing, or hasn't finished yet.
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

        workoutBiometricsRepo.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = workoutId,
                avgBpm = sample.avgBpm,
                maxBpm = sample.maxBpm,
                activeCaloriesKcal = sample.activeCaloriesKcal,
                sourcePackageName = sample.sourcePackageName,
                fetchedAt = clock.nowMillis(),
            ),
        )
        return true
    }
}

/**
 * Finds `COMPLETE` workouts started within the fixed [BACKFILL_WINDOW_DAYS] window that don't
 * have a [WorkoutBiometrics] row yet, and calls [PullBiometricsForWorkoutUseCase] for each.
 * Composed from [WorkoutRepository]/[WorkoutBiometricsRepository] directly rather than a
 * dedicated cross-table query, so the candidate-selection logic here is exercised by ordinary
 * fakes.
 */
class RunBiometricsBackfillUseCase @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
    private val pullBiometricsForWorkoutUseCase: PullBiometricsForWorkoutUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(): BiometricsBackfillResult {
        val now = clock.nowMillis()
        val sinceStartTime = now - BACKFILL_WINDOW_DAYS * DAY_MILLIS
        val completedIds = workoutRepo.observeCompletedBetween(sinceStartTime, now).first()
            .map { it.id }
        val missingIds = completedIds.filter { workoutBiometricsRepo.get(it) == null }
        val pulledCount = missingIds.count { pullBiometricsForWorkoutUseCase(it) }
        return BiometricsBackfillResult(
            candidateCount = missingIds.size,
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

/** Wipes every pulled `WorkoutBiometrics` row - a hard delete, not a tombstone, since this data
 * is a local cache of Health Connect, not a durable record. Only reachable while the feature is
 * opted out, so it has no effect on the periodic job's own schedule. */
class DeleteHealthConnectDataUseCase @Inject constructor(
    private val workoutBiometricsRepo: WorkoutBiometricsRepository,
) {
    suspend operator fun invoke() = workoutBiometricsRepo.deleteAll()
}
