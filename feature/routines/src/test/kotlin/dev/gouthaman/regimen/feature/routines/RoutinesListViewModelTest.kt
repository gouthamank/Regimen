package dev.gouthaman.regimen.feature.routines

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.Routine
import dev.gouthaman.regimen.domain.model.RoutineWithExercises
import dev.gouthaman.regimen.domain.repository.RoutineRepository
import dev.gouthaman.regimen.domain.usecase.DeleteRoutineUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveRoutinesUseCase
import dev.gouthaman.regimen.domain.usecase.ReorderRoutinesUseCase
import dev.gouthaman.regimen.testing.FakeRoutineRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class GatedReorderRoutineRepository(
    private val delegate: FakeRoutineRepository,
    private val reorderGate: CompletableDeferred<Unit>,
) : RoutineRepository by delegate {
    override suspend fun reorder(orderedIds: List<String>) {
        reorderGate.await()
        delegate.reorder(orderedIds)
    }
}

class RoutinesListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val routineA = RoutineWithExercises(Routine("1", "A", 0), emptyList())
    private val routineB = RoutineWithExercises(Routine("2", "B", 1), emptyList())
    private val routineC = RoutineWithExercises(Routine("3", "C", 2), emptyList())

    private fun viewModelFor(repo: RoutineRepository) = RoutinesListViewModel(
        observeRoutines = ObserveRoutinesUseCase(repo),
        deleteRoutine = DeleteRoutineUseCase(repo),
        reorderRoutines = ReorderRoutinesUseCase(repo),
    )

    @Test
    fun `routines are sorted by position when no reorder is pending`() = runTest {
        val repo = FakeRoutineRepository().apply { seed(routineC, routineA, routineB) }
        val viewModel = viewModelFor(repo)

        viewModel.routines.test {
            var state = awaitItem()
            while (state.size < 3) state = awaitItem()
            assertEquals(
                listOf(routineA.routine.id, routineB.routine.id, routineC.routine.id),
                state.map { it.routine.id })
        }
    }

    @Test
    fun `a pending reorder is reflected immediately and appends routines missing from the new order`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val repo = GatedReorderRoutineRepository(FakeRoutineRepository().apply {
                seed(
                    routineA,
                    routineB,
                    routineC
                )
            }, gate)
            val viewModel = viewModelFor(repo)

            viewModel.routines.test {
                var state = awaitItem()
                while (state.size < 3) state = awaitItem()
                assertEquals(
                    listOf(routineA.routine.id, routineB.routine.id, routineC.routine.id),
                    state.map { it.routine.id })

                viewModel.reorder(listOf(routineC.routine.id, routineA.routine.id))

                val optimistic = awaitItem().map { it.routine.id }
                assertEquals(
                    listOf(routineC.routine.id, routineA.routine.id, routineB.routine.id),
                    optimistic
                )

                gate.complete(Unit)

                val settled = awaitItem().map { it.routine.id }
                assertEquals(
                    listOf(routineC.routine.id, routineA.routine.id, routineB.routine.id),
                    settled
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `deleting a routine removes it from the list`() = runTest {
        val repo = FakeRoutineRepository().apply { seed(routineA, routineB) }
        val viewModel = viewModelFor(repo)

        viewModel.routines.test {
            var state = awaitItem()
            while (state.size < 2) state = awaitItem()

            viewModel.delete(routineA)

            state = awaitItem()
            while (state.size != 1) state = awaitItem()
            assertEquals(listOf(routineB.routine.id), state.map { it.routine.id })
        }
    }
}
