package dev.gouthaman.regimen.healthconnect

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.repository.HealthConnectRepositoryImpl
import dev.gouthaman.regimen.data.repository.WorkoutBiometricsRepositoryImpl
import dev.gouthaman.regimen.data.repository.WorkoutRepositoryImpl
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import dev.gouthaman.regimen.domain.usecase.PullBiometricsForWorkoutUseCase
import dev.gouthaman.regimen.domain.util.Clock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manual Phase 1b checkpoint against the real `HealthConnectClient` - not part of the automated
 * suite. Requires seeding Health Connect with a HeartRateRecord or ActiveCaloriesBurnedRecord via
 * the Health Connect Toolbox app, and granting Regimen its read permissions, both immediately
 * before running this.
 *
 * Lives in `:app`'s androidTest, not `:core:data`'s, deliberately - a library module's
 * instrumented tests run under a synthetic test-only package that was never granted Health
 * Connect permissions in the first place, regardless of what's granted to the real
 * `dev.gouthaman.regimen` app. `:app`'s androidTest, self-instrumenting the real app, is the only
 * place this check actually runs under the same package the permission grant applies to.
 */
@Ignore("Manual - requires Toolbox-seeded Health Connect data and a real permission grant first")
@RunWith(AndroidJUnit4::class)
class HealthConnectManualVerificationTest {

    @Test
    fun pullBiometricsForWorkoutUseCase_findsToolboxSeededData() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()

        val workoutRepo = WorkoutRepositoryImpl(db.workoutDao(), db.syncTombstoneDao(), db)
        val biometricsRepo = WorkoutBiometricsRepositoryImpl(db.workoutBiometricsDao())
        val healthConnectRepo = HealthConnectRepositoryImpl(context)

        // A wide window so it's easy to seed matching Toolbox data without an exact timestamp.
        val now = System.currentTimeMillis()
        val workoutId = UUID.randomUUID().toString()
        db.workoutDao().insertWorkout(
            WorkoutEntity(
                id = workoutId,
                startTime = now - TimeUnit.HOURS.toMillis(6),
                endTime = now,
                workoutStatus = WorkoutStatus.COMPLETE,
            ),
        )

        val clock = object : Clock {
            override fun nowMillis(): Long = now
        }
        val useCase =
            PullBiometricsForWorkoutUseCase(healthConnectRepo, workoutRepo, biometricsRepo, clock)

        val found = useCase(workoutId)

        assertTrue(
            "No Health Connect data found in the last 6 hours - seed a HeartRateRecord or " +
                    "ActiveCaloriesBurnedRecord via Health Connect Toolbox first, and grant Regimen " +
                    "Health Connect read permissions via its own Settings UI.",
            found,
        )
        val saved = biometricsRepo.get(workoutId)
        println(
            "Pulled: avgBpm=${saved?.avgBpm} maxBpm=${saved?.maxBpm} " +
                    "activeCaloriesKcal=${saved?.activeCaloriesKcal} source=${saved?.sourcePackageName}",
        )
    }
}
