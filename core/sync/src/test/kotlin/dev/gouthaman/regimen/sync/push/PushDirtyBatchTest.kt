package dev.gouthaman.regimen.sync.push

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Row(val id: String)

class PushDirtyBatchTest {

    @Test
    fun `writes and clears every dirty row, decrementing the budget`() = runTest {
        val rows = listOf(Row("a"), Row("b"), Row("c"))
        val written = mutableListOf<String>()
        val cleared = mutableListOf<List<String>>()

        val result = pushDirtyBatch(
            budget = 10,
            getDirty = { limit -> rows.take(limit) },
            write = { row -> written += row.id },
            idOf = Row::id,
            clearDirty = { ids -> cleared += ids },
        )

        assertEquals(listOf("a", "b", "c"), written)
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), cleared)
        assertEquals(7, result.remainingBudget)
        assertFalse(result.hasMore)
    }

    @Test
    fun `a write failure partway through leaves earlier rows cleared and propagates the exception`() =
        runTest {
            val rows = listOf(Row("a"), Row("b"), Row("c"))
            val cleared = mutableListOf<String>()

            try {
                pushDirtyBatch(
                    budget = 10,
                    getDirty = { limit -> rows.take(limit) },
                    write = { row -> if (row.id == "b") throw RuntimeException("network drop") },
                    idOf = Row::id,
                    clearDirty = { ids -> cleared += ids },
                )
                error("expected an exception")
            } catch (e: RuntimeException) {
                assertEquals("network drop", e.message)
            }

            assertEquals(listOf("a"), cleared)
        }

    @Test
    fun `a full page at the requested limit signals more work may remain`() = runTest {
        val rows = (1..5).map { Row(it.toString()) }

        val result = pushDirtyBatch(
            budget = 5,
            getDirty = { limit -> rows.take(limit) },
            write = { },
            idOf = Row::id,
            clearDirty = { },
        )

        assertTrue(result.hasMore)
    }

    @Test
    fun `a short page below the requested limit means the backlog is exhausted`() = runTest {
        val rows = listOf(Row("a"))

        val result = pushDirtyBatch(
            budget = 5,
            getDirty = { limit -> rows.take(limit) },
            write = { },
            idOf = Row::id,
            clearDirty = { },
        )

        assertFalse(result.hasMore)
    }

    @Test
    fun `a zero budget skips the read entirely`() = runTest {
        var getDirtyCalled = false

        val result = pushDirtyBatch(
            budget = 0,
            getDirty = { getDirtyCalled = true; emptyList() },
            write = { },
            idOf = Row::id,
            clearDirty = { },
        )

        assertFalse(getDirtyCalled)
        assertEquals(0, result.remainingBudget)
        assertFalse(result.hasMore)
    }
}
