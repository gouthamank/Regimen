package dev.gouthaman.regimen.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.WorkoutBiometricsEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutBiometricsDaoTest {

    private lateinit var db: RegimenDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var biometricsDao: WorkoutBiometricsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        workoutDao = db.workoutDao()
        biometricsDao = db.workoutBiometricsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertWorkout(
        startTime: Long,
        workoutStatus: WorkoutStatus,
    ): String {
        val id = UUID.randomUUID().toString()
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = id,
                startTime = startTime,
                endTime = if (workoutStatus == WorkoutStatus.COMPLETE) startTime + 1_000 else null,
                workoutStatus = workoutStatus,
            ),
        )
        return id
    }

    @Test
    fun upsert_thenGet_returnsTheSameRow() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
        biometricsDao.upsert(
            WorkoutBiometricsEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                avgBpm = 120,
                maxBpm = 150,
                activeCaloriesKcal = 300.5,
                sourcePackageName = "com.fitbit.FitbitMobile",
                fetchedAt = 5_000,
            ),
        )

        val result = biometricsDao.get(workoutId)

        assertEquals(120, result?.avgBpm)
        assertEquals(150, result?.maxBpm)
        assertEquals(300.5, result?.activeCaloriesKcal)
        assertEquals("com.fitbit.FitbitMobile", result?.sourcePackageName)
    }

    @Test
    fun get_forAWorkoutWithNoBiometrics_returnsNull() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)

        assertNull(biometricsDao.get(workoutId))
    }

    @Test
    fun upsert_twiceForTheSameWorkout_replacesRatherThanDuplicates() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
        biometricsDao.upsert(
            WorkoutBiometricsEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                avgBpm = 100,
                fetchedAt = 1_000,
            ),
        )
        biometricsDao.upsert(
            WorkoutBiometricsEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                avgBpm = 140,
                fetchedAt = 2_000,
            ),
        )

        assertEquals(140, biometricsDao.get(workoutId)?.avgBpm)
    }

    @Test
    fun deletingTheParentWorkout_cascadesToItsBiometrics() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
        biometricsDao.upsert(
            WorkoutBiometricsEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                fetchedAt = 1_000,
            ),
        )

        workoutDao.deleteWorkout(WorkoutEntity(id = workoutId, startTime = 1_000))

        assertNull(biometricsDao.get(workoutId))
    }

    @Test
    fun getCompletedWorkoutIdsMissingBiometrics_excludesInProgressAndAlreadyPulledWorkouts() =
        runTest {
            val missingId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
            val hasBiometricsId =
                insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
            biometricsDao.upsert(
                WorkoutBiometricsEntity(
                    id = UUID.randomUUID().toString(),
                    workoutId = hasBiometricsId,
                    fetchedAt = 1_000,
                ),
            )
            insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.IN_PROGRESS)

            val result = biometricsDao.getCompletedWorkoutIdsMissingBiometrics(sinceStartTime = 0)

            assertEquals(listOf(missingId), result)
        }

    @Test
    fun getCompletedWorkoutIdsMissingBiometrics_excludesWorkoutsBeforeTheCutoff() = runTest {
        insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)
        val recentId = insertWorkout(startTime = 5_000, workoutStatus = WorkoutStatus.COMPLETE)

        val result = biometricsDao.getCompletedWorkoutIdsMissingBiometrics(sinceStartTime = 2_000)

        assertEquals(listOf(recentId), result)
    }
}
