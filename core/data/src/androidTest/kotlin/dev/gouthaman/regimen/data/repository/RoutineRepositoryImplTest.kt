package dev.gouthaman.regimen.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseSpec
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.Routine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineRepositoryImplTest {

    private lateinit var db: RegimenDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        routineRepository = RoutineRepositoryImpl(db.routineDao(), db.syncTombstoneDao(), db)
        exerciseRepository = ExerciseRepositoryImpl(db.exerciseDao(), db.syncTombstoneDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExercise(): String =
        exerciseRepository.addCustom("Bench Press", MuscleGroup.CHEST, Equipment.BARBELL)

    @Test
    fun delete_tombstonesTheRoutineAndItsCascadedExercises() = runTest {
        val exerciseId = insertExercise()
        val routineId = routineRepository.saveRoutine(
            routineId = null,
            name = "Push Day",
            specs = listOf(ExerciseSpec(exerciseId = exerciseId)),
        )
        val routineExerciseId =
            routineRepository.getRoutine(routineId)!!.exercises.single().routineExercise.id

        routineRepository.delete(Routine(id = routineId, name = "Push Day", position = 0))

        val tombstones = db.syncTombstoneDao().getAll().associateBy { it.entityId }
        assertEquals(2, tombstones.size)
        assertEquals(SyncEntityType.ROUTINE, tombstones[routineId]?.entityType)
        assertEquals(SyncEntityType.ROUTINE_EXERCISE, tombstones[routineExerciseId]?.entityType)
        assertEquals(routineId, tombstones[routineExerciseId]?.parentId)
        assertNull(routineRepository.getRoutine(routineId))
    }

    @Test
    fun saveRoutine_tombstonesOnlyExercisesRemovedFromTheList() = runTest {
        val benchId = insertExercise()
        val squatId = exerciseRepository.addCustom("Squat", MuscleGroup.LEGS, Equipment.BARBELL)
        val routineId = routineRepository.saveRoutine(
            routineId = null,
            name = "Push Day",
            specs = listOf(ExerciseSpec(exerciseId = benchId)),
        )
        val benchRoutineExerciseId =
            routineRepository.getRoutine(routineId)!!.exercises.single().routineExercise.id

        routineRepository.saveRoutine(
            routineId = routineId,
            name = "Push Day",
            specs = listOf(ExerciseSpec(exerciseId = squatId)),
        )

        val tombstones = db.syncTombstoneDao().getAll()
        assertEquals(1, tombstones.size)
        assertEquals(SyncEntityType.ROUTINE_EXERCISE, tombstones[0].entityType)
        assertEquals(benchRoutineExerciseId, tombstones[0].entityId)
        assertEquals(routineId, tombstones[0].parentId)
    }

    @Test
    fun saveRoutine_keepingOneExerciseAndRemovingAnotherOnlyTombstonesTheRemovedOne() = runTest {
        val benchId = insertExercise()
        val squatId = exerciseRepository.addCustom("Squat", MuscleGroup.LEGS, Equipment.BARBELL)
        val routineId = routineRepository.saveRoutine(
            routineId = null,
            name = "Push Day",
            specs = listOf(ExerciseSpec(exerciseId = benchId), ExerciseSpec(exerciseId = squatId)),
        )
        val exercisesById =
            routineRepository.getRoutine(routineId)!!.exercises.associateBy { it.exercise.id }
        val benchRoutineExerciseId = exercisesById[benchId]!!.routineExercise.id
        val squatRoutineExerciseId = exercisesById[squatId]!!.routineExercise.id

        routineRepository.saveRoutine(
            routineId = routineId,
            name = "Push Day",
            specs = listOf(ExerciseSpec(exerciseId = benchId)),
        )

        val tombstones = db.syncTombstoneDao().getAll()
        assertEquals(1, tombstones.size)
        assertEquals(SyncEntityType.ROUTINE_EXERCISE, tombstones[0].entityType)
        assertEquals(squatRoutineExerciseId, tombstones[0].entityId)
        val survivingExercise = routineRepository.getRoutine(routineId)!!.exercises.single()
        assertEquals(benchRoutineExerciseId, survivingExercise.routineExercise.id)
    }
}
