package dev.gouthaman.regimen.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.RoutineExerciseEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutineDaoTest {

    private lateinit var db: RegimenDatabase
    private lateinit var routineDao: RoutineDao
    private lateinit var exerciseDao: ExerciseDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        routineDao = db.routineDao()
        exerciseDao = db.exerciseDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExercise(name: String = "Bench Press"): Long = exerciseDao.insert(
        ExerciseEntity(
            name = name,
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL
        ),
    )

    private suspend fun insertRoutine(name: String, position: Int): Long =
        routineDao.insertRoutine(RoutineEntity(name = name, position = position))

    @Test
    fun observeRoutinesWithExercises_resolvesExercisesInPositionOrder() = runTest {
        val benchId = insertExercise("Bench Press")
        val squatId = insertExercise("Squat")
        val routineId = insertRoutine("Push Day", 0)
        routineDao.insertRoutineExercises(
            listOf(
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = squatId,
                    position = 1,
                    targetSets = 3,
                    targetReps = 5,
                    targetRestSec = 120
                ),
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = benchId,
                    position = 0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                ),
            ),
        )

        routineDao.observeRoutinesWithExercises().test {
            val routines = awaitItem()
            assertEquals(1, routines.size)
            val exercises = routines[0].exercises.sortedBy { it.routineExercise.position }
            assertEquals(benchId, exercises[0].exercise.id)
            assertEquals(squatId, exercises[1].exercise.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeRoutinesWithExercises_ordersRoutinesByPosition() = runTest {
        insertRoutine("Second", 1)
        insertRoutine("First", 0)

        routineDao.observeRoutinesWithExercises().test {
            assertEquals(listOf("First", "Second"), awaitItem().map { it.routine.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeRoutine_returnsNullForAMissingId() = runTest {
        routineDao.observeRoutine(999).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeRoutine_emitsAgainAfterAnUpdate() = runTest {
        val routineId = insertRoutine("Push Day", 0)

        routineDao.observeRoutine(routineId).test {
            assertEquals("Push Day", awaitItem()?.routine?.name)

            routineDao.updateRoutine(
                RoutineEntity(
                    id = routineId,
                    name = "Push Day (v2)",
                    position = 0
                )
            )

            assertEquals("Push Day (v2)", awaitItem()?.routine?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getRoutineWithExercises_resolvesTheSameShapeAsTheFlowVariant() = runTest {
        val exerciseId = insertExercise()
        val routineId = insertRoutine("Push Day", 0)
        routineDao.insertRoutineExercises(
            listOf(
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    position = 0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                )
            ),
        )

        val routine = routineDao.getRoutineWithExercises(routineId)!!

        assertEquals("Push Day", routine.routine.name)
        assertEquals(1, routine.exercises.size)
    }

    @Test
    fun getRoutineWithExercises_returnsNullForAMissingId() = runTest {
        assertNull(routineDao.getRoutineWithExercises(999))
    }

    @Test
    fun applyOrder_rewritesPositionsToMatchTheGivenIdOrder() = runTest {
        val a = insertRoutine("A", 0)
        val b = insertRoutine("B", 1)
        val c = insertRoutine("C", 2)

        routineDao.applyOrder(listOf(c, a, b))

        routineDao.observeRoutinesWithExercises().test {
            assertEquals(listOf("C", "A", "B"), awaitItem().map { it.routine.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun replaceRoutineExercises_removesOldEntriesAndInsertsTheNewOnes() = runTest {
        val benchId = insertExercise("Bench Press")
        val squatId = insertExercise("Squat")
        val routineId = insertRoutine("Push Day", 0)
        routineDao.insertRoutineExercises(
            listOf(
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = benchId,
                    position = 0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                )
            ),
        )

        routineDao.replaceRoutineExercises(
            routineId,
            listOf(
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = squatId,
                    position = 0,
                    targetSets = 4,
                    targetReps = 5,
                    targetRestSec = 150
                )
            ),
        )

        val exercises = routineDao.getRoutineWithExercises(routineId)!!.exercises
        assertEquals(1, exercises.size)
        assertEquals(squatId, exercises[0].exercise.id)
        assertEquals(4, exercises[0].routineExercise.targetSets)
    }

    @Test
    fun isExerciseUsedInAnyRoutine_reflectsRoutineMembership() = runTest {
        val exerciseId = insertExercise()
        val routineId = insertRoutine("Push Day", 0)

        assertFalse(routineDao.isExerciseUsedInAnyRoutine(exerciseId))

        routineDao.insertRoutineExercises(
            listOf(
                RoutineExerciseEntity(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    position = 0,
                    targetSets = 3,
                    targetReps = 8,
                    targetRestSec = 90
                )
            ),
        )

        assertTrue(routineDao.isExerciseUsedInAnyRoutine(exerciseId))
    }

    @Test
    fun maxPosition_reflectsTheHighestExistingPositionOrMinusOneWhenEmpty() = runTest {
        assertEquals(-1, routineDao.maxPosition())

        insertRoutine("A", 0)
        insertRoutine("B", 4)

        assertEquals(4, routineDao.maxPosition())
    }
}
