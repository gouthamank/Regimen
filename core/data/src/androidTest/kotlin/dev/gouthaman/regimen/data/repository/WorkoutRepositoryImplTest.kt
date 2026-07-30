package dev.gouthaman.regimen.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.domain.model.CardioEntry
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.SetEntry
import dev.gouthaman.regimen.domain.model.Workout
import dev.gouthaman.regimen.domain.model.WorkoutExercise
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryImplTest {

    private lateinit var db: RegimenDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        workoutRepository = WorkoutRepositoryImpl(db.workoutDao(), db.syncTombstoneDao(), db)
        exerciseRepository = ExerciseRepositoryImpl(db.exerciseDao(), db.syncTombstoneDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExercise(): String =
        exerciseRepository.addCustom("Bench Press", MuscleGroup.CHEST, Equipment.BARBELL)

    @Test
    fun deleteWorkout_tombstonesTheWorkoutAndEveryCascadedRow() = runTest {
        val exerciseId = insertExercise()
        val workoutId = workoutRepository.createWorkout(startTime = 1_000, routineId = null)
        val strengthWe = workoutRepository.addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = 0)
        )
        val setId = workoutRepository.upsertSet(
            SetEntry(workoutExerciseId = strengthWe, setNumber = 1, weightKg = 80.0)
        )
        val cardioWe = workoutRepository.addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = 1)
        )
        val cardioId = workoutRepository.upsertCardio(
            CardioEntry(workoutExerciseId = cardioWe, durationSec = 600)
        )

        workoutRepository.deleteWorkout(Workout(id = workoutId, startTime = 1_000))

        val tombstones = db.syncTombstoneDao().getAll().associateBy { it.entityId }
        assertEquals(5, tombstones.size)
        assertEquals(SyncEntityType.WORKOUT, tombstones[workoutId]?.entityType)
        assertEquals(SyncEntityType.WORKOUT_EXERCISE, tombstones[strengthWe]?.entityType)
        assertEquals(workoutId, tombstones[strengthWe]?.parentId)
        assertEquals(SyncEntityType.WORKOUT_EXERCISE, tombstones[cardioWe]?.entityType)
        assertEquals(SyncEntityType.SET_ENTRY, tombstones[setId]?.entityType)
        assertEquals(strengthWe, tombstones[setId]?.parentId)
        assertEquals(workoutId, tombstones[setId]?.grandparentId)
        assertEquals(SyncEntityType.CARDIO_ENTRY, tombstones[cardioId]?.entityType)
        assertEquals(cardioWe, tombstones[cardioId]?.parentId)
        assertEquals(workoutId, tombstones[cardioId]?.grandparentId)
        assertNull(workoutRepository.getWorkout(workoutId))
    }

    @Test
    fun deleteSet_looksUpTheWorkoutIdForTheGrandparent() = runTest {
        val exerciseId = insertExercise()
        val workoutId = workoutRepository.createWorkout(startTime = 1_000, routineId = null)
        val weId = workoutRepository.addExercise(
            WorkoutExercise(workoutId = workoutId, exerciseId = exerciseId, position = 0)
        )
        val setId = workoutRepository.upsertSet(
            SetEntry(workoutExerciseId = weId, setNumber = 1, weightKg = 80.0)
        )

        workoutRepository.deleteSet(
            SetEntry(id = setId, workoutExerciseId = weId, setNumber = 1, weightKg = 80.0)
        )

        val tombstone = db.syncTombstoneDao().getAll().single()
        assertEquals(SyncEntityType.SET_ENTRY, tombstone.entityType)
        assertEquals(weId, tombstone.parentId)
        assertEquals(workoutId, tombstone.grandparentId)
    }
}
