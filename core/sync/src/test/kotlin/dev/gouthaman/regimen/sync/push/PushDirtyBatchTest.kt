package dev.gouthaman.regimen.sync.push

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Row(val id: String)

class PushDirtyBatchTest {

    @Test
    fun `commits and clears every dirty row in chunks, decrementing the budget`() = runTest {
        val rows = (1..5).map { Row(it.toString()) }
        val committed = mutableListOf<List<String>>()
        val cleared = mutableListOf<List<String>>()
        val reported = mutableListOf<Int>()

        val result = pushDirtyBatch(
            budget = 10,
            getDirty = { limit -> rows.take(limit) },
            commit = { chunk -> committed += chunk.map(Row::id) },
            idOf = Row::id,
            clearDirty = { ids -> cleared += ids },
            chunkSize = 2,
            onCommitted = { count -> reported += count },
        )

        val expectedChunks = listOf(listOf("1", "2"), listOf("3", "4"), listOf("5"))
        assertEquals(expectedChunks, committed)
        assertEquals(expectedChunks, cleared)
        assertEquals(listOf(2, 2, 1), reported)
        assertEquals(5, result.remainingBudget)
        assertFalse(result.hasMore)
    }

    @Test
    fun `a commit failure partway through leaves earlier chunks cleared and propagates the exception`() =
        runTest {
            val rows = (1..5).map { Row(it.toString()) }
            val cleared = mutableListOf<String>()

            try {
                pushDirtyBatch(
                    budget = 10,
                    getDirty = { limit -> rows.take(limit) },
                    commit = { chunk ->
                        if ("3" in chunk.map(Row::id)) throw RuntimeException("network drop")
                    },
                    idOf = Row::id,
                    clearDirty = { ids -> cleared += ids },
                    chunkSize = 2,
                )
                error("expected an exception")
            } catch (e: RuntimeException) {
                assertEquals("network drop", e.message)
            }

            // The failed chunk ("3", "4") and everything after it stay dirty.
            assertEquals(listOf("1", "2"), cleared)
        }

    @Test
    fun `a full page at the requested limit signals more work may remain`() = runTest {
        val rows = (1..5).map { Row(it.toString()) }

        val result = pushDirtyBatch(
            budget = 5,
            getDirty = { limit -> rows.take(limit) },
            commit = { },
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
            commit = { },
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
            getDirty = { getDirtyCalled = true; emptyList<Row>() },
            commit = { },
            idOf = Row::id,
            clearDirty = { },
        )

        assertFalse(getDirtyCalled)
        assertEquals(0, result.remainingBudget)
        assertFalse(result.hasMore)
    }
}
