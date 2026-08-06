package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import kotlinx.coroutines.flow.Flow

interface WorkoutBiometricsRepository {
    suspend fun get(workoutId: String): WorkoutBiometrics?

    /** Reactive counterpart of [get]. */
    fun observe(workoutId: String): Flow<WorkoutBiometrics?>

    suspend fun upsert(biometrics: WorkoutBiometrics): String

    /** Bulk read, avoids one suspend call per workout when building a trend. */
    suspend fun getForWorkouts(workoutIds: List<String>): List<WorkoutBiometrics>

    /** The single most recently pulled row across every workout - the Settings status widget's
     * "last successful pull"/"currently syncing from" fields read off this, not any one workout's
     * own row. */
    suspend fun getMostRecentlyFetched(): WorkoutBiometrics?

    /** Every row, unconditionally - no per-workout deletion exists, only this bulk action from
     * Health Connect Settings. Deliberately a hard delete, not a tombstone - a workout still
     * inside the backfill window can freely repopulate on the next check if the feature is turned
     * back on; this data is a local cache of what's in Health Connect, not a durable record. */
    suspend fun deleteAll()
}