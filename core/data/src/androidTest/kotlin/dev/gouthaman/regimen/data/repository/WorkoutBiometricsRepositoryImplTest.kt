package dev.gouthaman.regimen.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.domain.model.WorkoutBiometrics
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WorkoutBiometricsRepositoryImplTest {

    private lateinit var db: RegimenDatabase
    private lateinit var repository: WorkoutBiometricsRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        repository = WorkoutBiometricsRepositoryImpl(db.workoutBiometricsDao())
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
        db.workoutDao().insertWorkout(
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
    fun upsert_withNoIdSupplied_generatesOneAndReturnsIt() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)

        val generatedId = repository.upsert(
            WorkoutBiometrics(
                id = "",
                workoutId = workoutId,
                avgBpm = 120,
                maxBpm = 150,
                activeCaloriesKcal = 300.5,
                sourcePackageName = "com.fitbit.FitbitMobile",
                fetchedAt = 5_000,
            ),
        )

        assertNotNull(generatedId)
        assertEquals(generatedId, repository.get(workoutId)?.id)
    }

    @Test
    fun upsert_withAnIdSupplied_keepsIt() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)

        val id = repository.upsert(
            WorkoutBiometrics(id = "fixed-id", workoutId = workoutId, fetchedAt = 1_000),
        )

        assertEquals("fixed-id", id)
    }

    @Test
    fun get_forAWorkoutWithNoBiometrics_returnsNull() = runTest {
        val workoutId = insertWorkout(startTime = 1_000, workoutStatus = WorkoutStatus.COMPLETE)

        assertNull(repository.get(workoutId))
    }
}
