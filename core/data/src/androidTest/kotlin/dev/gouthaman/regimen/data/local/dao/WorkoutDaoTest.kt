package dev.gouthaman.regimen.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.CardioEntryEntity
import dev.gouthaman.regimen.data.local.entity.ExerciseEntity
import dev.gouthaman.regimen.data.local.entity.RoutineEntity
import dev.gouthaman.regimen.data.local.entity.SetEntryEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutEntity
import dev.gouthaman.regimen.data.local.entity.WorkoutExerciseEntity
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: RegimenDatabase
    private lateinit var workoutDao: WorkoutDao
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var routineDao: RoutineDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        workoutDao = db.workoutDao()
        exerciseDao = db.exerciseDao()
        routineDao = db.routineDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertExercise(
        name: String = "Bench Press",
        type: ExerciseType = ExerciseType.STRENGTH,
    ): Long = exerciseDao.insert(
        ExerciseEntity(
            name = name,
            type = type,
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL
        ),
    )

    private suspend fun insertWorkout(
        startTime: Long,
        endTime: Long? = null,
        routineId: Long? = null,
        workoutStatus: WorkoutStatus = if (endTime != null) WorkoutStatus.COMPLETE else WorkoutStatus.IN_PROGRESS,
    ): Long = workoutDao.insertWorkout(
        WorkoutEntity(
            startTime = startTime,
            endTime = endTime,
            routineId = routineId,
            workoutStatus = workoutStatus,
        ),
    )

    private suspend fun insertWorkoutExercise(
        workoutId: Long,
        exerciseId: Long,
        position: Int = 0
    ): Long =
        workoutDao.insertWorkoutExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                position = position
            ),
        )

    private suspend fun insertSet(
        workoutExerciseId: Long,
        setNumber: Int,
        weightKg: Double? = null,
        reps: Int? = null,
        isComplete: Boolean = true,
    ): Long = workoutDao.upsertSet(
        SetEntryEntity(
            workoutExerciseId = workoutExerciseId,
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            isComplete = isComplete,
        ),
    )

    @Test
    fun observeBestWeight_returnsHeaviestCompletedSet() = runTest {
        val exerciseId = insertExercise()
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 80.0, isComplete = true)
        insertSet(weId, 2, weightKg = 100.0, isComplete = true)
        insertSet(weId, 3, weightKg = 999.0, isComplete = false)

        workoutDao.observeBestWeight(exerciseId).test {
            assertEquals(100.0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestWeight_ignoresInProgressWorkouts() = runTest {
        val exerciseId = insertExercise()
        val workoutId = insertWorkout(startTime = 1_000, endTime = null)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 500.0, isComplete = true)

        workoutDao.observeBestWeight(exerciseId).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestWeight_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val exerciseId = insertExercise()
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = 2_000, workoutStatus = WorkoutStatus.EDITING)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 90.0)

        workoutDao.observeBestWeight(exerciseId).test {
            assertEquals(90.0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestWeight_includesWorkoutsMarkedCompleteEvenWhenEndTimeIsNull() = runTest {
        val exerciseId = insertExercise()
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = null, workoutStatus = WorkoutStatus.COMPLETE)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 500.0)

        workoutDao.observeBestWeight(exerciseId).test {
            assertEquals(500.0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePersonalRecords_groupsHeaviestPerExercise() = runTest {
        val benchId = insertExercise("Bench Press")
        val squatId = insertExercise("Squat")
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val benchWe = insertWorkoutExercise(workoutId, benchId, 0)
        val squatWe = insertWorkoutExercise(workoutId, squatId, 1)
        insertSet(benchWe, 1, weightKg = 60.0)
        insertSet(benchWe, 2, weightKg = 80.0)
        insertSet(squatWe, 1, weightKg = 120.0)

        workoutDao.observePersonalRecords().test {
            val records = awaitItem().associateBy { it.exerciseId }
            assertEquals(80.0, records.getValue(benchId).bestWeightKg, 0.0)
            assertEquals(120.0, records.getValue(squatId).bestWeightKg, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePersonalRecords_excludesSetsWithoutWeight() = runTest {
        val exerciseId = insertExercise("Push Up", ExerciseType.STRENGTH)
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = null, reps = 20)

        workoutDao.observePersonalRecords().test {
            assertTrue(awaitItem().none { it.exerciseId == exerciseId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observePersonalRecords_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val exerciseId = insertExercise()
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = 2_000, workoutStatus = WorkoutStatus.EDITING)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 70.0)

        workoutDao.observePersonalRecords().test {
            assertEquals(70.0, awaitItem().first { it.exerciseId == exerciseId }.bestWeightKg, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestReps_onlyCoversSetsLoggedWithoutWeight() = runTest {
        val bodyweightId = insertExercise("Push Up")
        val weightedId = insertExercise("Bench Press")
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val bodyweightWe = insertWorkoutExercise(workoutId, bodyweightId, 0)
        val weightedWe = insertWorkoutExercise(workoutId, weightedId, 1)
        insertSet(bodyweightWe, 1, weightKg = null, reps = 15)
        insertSet(bodyweightWe, 2, weightKg = null, reps = 20)
        insertSet(weightedWe, 1, weightKg = 100.0, reps = 5)

        workoutDao.observeBestReps().test {
            val reps = awaitItem()
            assertEquals(1, reps.size)
            assertEquals(20, reps.first { it.exerciseId == bodyweightId }.bestReps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestReps_ignoresInProgressWorkouts() = runTest {
        val exerciseId = insertExercise("Push Up")
        val workoutId = insertWorkout(startTime = 1_000, endTime = null)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = null, reps = 25)

        workoutDao.observeBestReps().test {
            assertTrue(awaitItem().none { it.exerciseId == exerciseId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeBestReps_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val exerciseId = insertExercise("Push Up")
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = 2_000, workoutStatus = WorkoutStatus.EDITING)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = null, reps = 18)

        workoutDao.observeBestReps().test {
            assertEquals(18, awaitItem().first { it.exerciseId == exerciseId }.bestReps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCompletedSetWithBothWeightAndReps_isAWeightPrButNotABodyweightRepsPr() = runTest {
        val exerciseId = insertExercise("Bench Press")
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 100.0, reps = 5)

        workoutDao.observePersonalRecords().test {
            assertEquals(100.0, awaitItem().first { it.exerciseId == exerciseId }.bestWeightKg, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
        workoutDao.observeBestReps().test {
            assertTrue(awaitItem().none { it.exerciseId == exerciseId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getMostRecentSetForExercise_picksHighestSetNumberFromTheMostRecentFinishedWorkout() =
        runTest {
            val exerciseId = insertExercise()
            val olderWorkout = insertWorkout(startTime = 1_000, endTime = 1_500)
            val olderWe = insertWorkoutExercise(olderWorkout, exerciseId)
            insertSet(olderWe, 1, weightKg = 40.0, reps = 12)

            val recentWorkout = insertWorkout(startTime = 5_000, endTime = 5_500)
            val recentWe = insertWorkoutExercise(recentWorkout, exerciseId)
            insertSet(recentWe, 1, weightKg = 60.0, reps = 10)
            insertSet(recentWe, 2, weightKg = 65.0, reps = 8)

            val result = workoutDao.getMostRecentSetForExercise(exerciseId)

            assertEquals(65.0, result?.weightKg)
            assertEquals(2, result?.setNumber)
        }

    @Test
    fun getMostRecentSetForExercise_ignoresUnfinishedWorkouts() = runTest {
        val exerciseId = insertExercise()
        val finished = insertWorkout(startTime = 1_000, endTime = 1_500)
        val finishedWe = insertWorkoutExercise(finished, exerciseId)
        insertSet(finishedWe, 1, weightKg = 40.0)

        val inProgress = insertWorkout(startTime = 9_000, endTime = null)
        val inProgressWe = insertWorkoutExercise(inProgress, exerciseId)
        insertSet(inProgressWe, 1, weightKg = 999.0)

        assertEquals(40.0, workoutDao.getMostRecentSetForExercise(exerciseId)?.weightKg)
    }

    @Test
    fun getMostRecentSetForExercise_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val exerciseId = insertExercise()
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = 2_000, workoutStatus = WorkoutStatus.EDITING)
        val weId = insertWorkoutExercise(workoutId, exerciseId)
        insertSet(weId, 1, weightKg = 77.0)

        assertEquals(77.0, workoutDao.getMostRecentSetForExercise(exerciseId)?.weightKg)
    }

    @Test
    fun getMostRecentSetForExercise_includesWorkoutsMarkedCompleteEvenWhenEndTimeIsNull() =
        runTest {
            val exerciseId = insertExercise()
            val workoutId = insertWorkout(
                startTime = 1_000,
                endTime = null,
                workoutStatus = WorkoutStatus.COMPLETE
            )
            val weId = insertWorkoutExercise(workoutId, exerciseId)
            insertSet(weId, 1, weightKg = 999.0)

            assertEquals(999.0, workoutDao.getMostRecentSetForExercise(exerciseId)?.weightKg)
        }

    @Test
    fun observeExerciseHistory_returnsFinishedSessionsMostRecentFirst() = runTest {
        val exerciseId = insertExercise()
        val first = insertWorkout(startTime = 1_000, endTime = 1_500)
        insertWorkoutExercise(first, exerciseId)
        val second = insertWorkout(startTime = 5_000, endTime = 5_500)
        insertWorkoutExercise(second, exerciseId)

        workoutDao.observeExerciseHistory(exerciseId).test {
            val history = awaitItem()
            assertEquals(2, history.size)
            assertEquals(5_000, history[0].startTime)
            assertEquals(1_000, history[1].startTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeExerciseHistory_excludesUnfinishedWorkouts() = runTest {
        val exerciseId = insertExercise()
        val finished = insertWorkout(startTime = 1_000, endTime = 1_500)
        insertWorkoutExercise(finished, exerciseId)
        val inProgress = insertWorkout(startTime = 9_000, endTime = null)
        insertWorkoutExercise(inProgress, exerciseId)

        workoutDao.observeExerciseHistory(exerciseId).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeExerciseHistory_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val exerciseId = insertExercise()
        val workoutId =
            insertWorkout(startTime = 1_000, endTime = 2_000, workoutStatus = WorkoutStatus.EDITING)
        insertWorkoutExercise(workoutId, exerciseId)

        workoutDao.observeExerciseHistory(exerciseId).test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getMostRecentCompletedForRoutine_ignoresRoutinesWithoutAFinishedSession() = runTest {
        val routineId = routineDao.insertRoutine(RoutineEntity(name = "Push Day", position = 0))
        insertWorkout(startTime = 1_000, endTime = null, routineId = routineId)

        assertNull(workoutDao.getMostRecentCompletedForRoutine(routineId))
    }

    @Test
    fun getMostRecentCompletedForRoutine_picksTheLatestFinishedSession() = runTest {
        val routineId = routineDao.insertRoutine(RoutineEntity(name = "Push Day", position = 0))
        insertWorkout(startTime = 1_000, endTime = 1_500, routineId = routineId)
        val latest = insertWorkout(startTime = 5_000, endTime = 5_500, routineId = routineId)

        assertEquals(latest, workoutDao.getMostRecentCompletedForRoutine(routineId)?.workout?.id)
    }

    @Test
    fun getMostRecentCompletedForRoutine_includesEditingStatusWorkoutsWithEndTimeSet() = runTest {
        val routineId = routineDao.insertRoutine(RoutineEntity(name = "Push Day", position = 0))
        val workoutId = insertWorkout(
            startTime = 1_000,
            endTime = 1_500,
            routineId = routineId,
            workoutStatus = WorkoutStatus.EDITING,
        )

        assertEquals(workoutId, workoutDao.getMostRecentCompletedForRoutine(routineId)?.workout?.id)
    }

    @Test
    fun observeCompletedWithDetails_onlyIncludesFinishedWorkoutsOrderedNewestFirst() = runTest {
        val exerciseId = insertExercise()
        val older = insertWorkout(startTime = 1_000, endTime = 1_500)
        insertWorkoutExercise(older, exerciseId)
        val newer = insertWorkout(startTime = 5_000, endTime = 5_500)
        insertWorkoutExercise(newer, exerciseId)
        insertWorkout(startTime = 9_000, endTime = null)

        workoutDao.observeCompletedWithDetails().test {
            val completed = awaitItem()
            assertEquals(2, completed.size)
            assertEquals(newer, completed[0].workout.id)
            assertEquals(older, completed[1].workout.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeWorkout_returnsNullForAMissingId() = runTest {
        workoutDao.observeWorkout(999).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeWorkout_emitsAgainAfterAnUpdate() = runTest {
        val workoutId = insertWorkout(startTime = 1_000)

        workoutDao.observeWorkout(workoutId).test {
            val initial = awaitItem()
            assertNull(initial?.workout?.note)

            workoutDao.updateWorkout(initial!!.workout.copy(note = "Felt strong"))

            assertEquals("Felt strong", awaitItem()?.workout?.note)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getWorkoutWithDetails_resolvesExerciseSetsAndCardio() = runTest {
        val strengthId = insertExercise("Bench Press", ExerciseType.STRENGTH)
        val cardioId = insertExercise("Running", ExerciseType.CARDIO)
        val workoutId = insertWorkout(startTime = 1_000, endTime = 2_000)
        val strengthWe = insertWorkoutExercise(workoutId, strengthId, 0)
        insertSet(strengthWe, 1, weightKg = 80.0, reps = 5)
        val cardioWe = insertWorkoutExercise(workoutId, cardioId, 1)
        workoutDao.upsertCardio(
            CardioEntryEntity(
                workoutExerciseId = cardioWe,
                durationSec = 600,
                distanceMeters = 2_000.0
            ),
        )

        val details = workoutDao.getWorkoutWithDetails(workoutId)!!

        assertEquals(2, details.exercises.size)
        val strengthDetails = details.exercises.first { it.workoutExercise.id == strengthWe }
        assertEquals(1, strengthDetails.sets.size)
        assertEquals(80.0, strengthDetails.sets[0].weightKg)
        val cardioDetails = details.exercises.first { it.workoutExercise.id == cardioWe }
        assertEquals(1, cardioDetails.cardio.size)
        assertEquals(2_000.0, cardioDetails.cardio[0].distanceMeters)
    }

    @Test
    fun getWorkoutWithDetails_returnsNullForAMissingId() = runTest {
        assertNull(workoutDao.getWorkoutWithDetails(999))
    }

    @Test
    fun getInProgressWorkout_returnsTheMostRecentUnfinishedWorkout() = runTest {
        insertWorkout(startTime = 1_000, endTime = 1_500)
        val inProgress = insertWorkout(startTime = 9_000, endTime = null)

        assertEquals(inProgress, workoutDao.getInProgressWorkout()?.workout?.id)
    }

    @Test
    fun getInProgressWorkout_returnsNullWhenEverythingIsFinished() = runTest {
        insertWorkout(startTime = 1_000, endTime = 1_500)

        assertNull(workoutDao.getInProgressWorkout())
    }
}
