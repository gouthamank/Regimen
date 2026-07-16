package dev.gouthaman.regimen.domain.usecase.routine

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.usecase.HasRoutinesUseCase
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HasRoutinesUseCaseTest {

    @Test
    fun `no routines yields false`() = runTest {
        val repo = FakeRoutineRepository()
        HasRoutinesUseCase(repo)().test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `at least one routine yields true`() = runTest {
        val repo = FakeRoutineRepository()
        repo.seed(RoutineWithExercises(Routine(1, "Push Day", 0), emptyList()))
        HasRoutinesUseCase(repo)().test {
            assertTrue(awaitItem())
        }
    }
}
