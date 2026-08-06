package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.WorkoutBiometrics

interface WorkoutBiometricsRepository {
    suspend fun get(workoutId: String): WorkoutBiometrics?
    suspend fun upsert(biometrics: WorkoutBiometrics): String

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