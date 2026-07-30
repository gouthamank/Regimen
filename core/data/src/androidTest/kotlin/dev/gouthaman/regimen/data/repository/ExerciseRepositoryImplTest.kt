package dev.gouthaman.regimen.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseRepositoryImplTest {

    private lateinit var db: RegimenDatabase
    private lateinit var repository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        repository = ExerciseRepositoryImpl(db.exerciseDao(), db.syncTombstoneDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun delete_tombstonesTheExercise() = runTest {
        val id = repository.addCustom("Bench Press", MuscleGroup.CHEST, Equipment.BARBELL)
        val exercise = Exercise(
            id = id,
            name = "Bench Press",
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isCustom = true,
        )

        repository.delete(exercise)

        val tombstone = db.syncTombstoneDao().getAll().single()
        assertEquals(SyncEntityType.EXERCISE, tombstone.entityType)
        assertEquals(id, tombstone.entityId)
        assertNull(repository.getById(id))
    }
}
