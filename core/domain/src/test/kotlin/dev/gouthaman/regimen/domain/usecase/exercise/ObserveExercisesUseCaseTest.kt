package dev.gouthaman.regimen.domain.usecase.exercise

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Equipment
import dev.gouthaman.regimen.domain.model.Exercise
import dev.gouthaman.regimen.domain.model.ExerciseType
import dev.gouthaman.regimen.domain.model.MuscleGroup
import dev.gouthaman.regimen.domain.usecase.ObserveExercisesUseCase
import dev.gouthaman.regimen.testing.FakeExerciseRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveExercisesUseCaseTest {

    private val benchPress =
        Exercise(1, "Bench Press", ExerciseType.STRENGTH, MuscleGroup.CHEST, Equipment.BARBELL)
    private val squat =
        Exercise(2, "Squat", ExerciseType.STRENGTH, MuscleGroup.LEGS, Equipment.BARBELL)
    private val running =
        Exercise(3, "Running", ExerciseType.CARDIO, MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)
    private val customCurl = Exercise(
        4,
        "My Curl",
        ExerciseType.STRENGTH,
        MuscleGroup.ARMS,
        Equipment.DUMBBELL,
        isCustom = true
    )

    private fun repoWithAll(): FakeExerciseRepository {
        val repo = FakeExerciseRepository()
        repo.seed(benchPress, squat, running, customCurl)
        return repo
    }

    @Test
    fun `no filters returns every exercise`() = runTest {
        ObserveExercisesUseCase(repoWithAll())().test {
            assertEquals(4, awaitItem().size)
        }
    }

    @Test
    fun `a search query matches name or tags`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(query = "cardio").test {
            assertEquals(listOf(running), awaitItem())
        }
    }

    @Test
    fun `a type filter narrows to that type`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(type = ExerciseType.CARDIO).test {
            assertEquals(listOf(running), awaitItem())
        }
    }

    @Test
    fun `a muscle group filter narrows to that group`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(muscleGroup = MuscleGroup.LEGS).test {
            assertEquals(listOf(squat), awaitItem())
        }
    }

    @Test
    fun `an equipment filter narrows to that equipment`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(equipment = Equipment.DUMBBELL).test {
            assertEquals(listOf(customCurl), awaitItem())
        }
    }

    @Test
    fun `customOnly excludes built-in exercises`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(customOnly = true).test {
            assertEquals(listOf(customCurl), awaitItem())
        }
    }

    @Test
    fun `filters combine with AND semantics`() = runTest {
        ObserveExercisesUseCase(repoWithAll())(
            type = ExerciseType.STRENGTH,
            muscleGroup = MuscleGroup.CHEST
        ).test {
            assertEquals(listOf(benchPress), awaitItem())
        }
    }
}
