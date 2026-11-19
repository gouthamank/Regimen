package dev.gouthaman.regimen.common

import dev.gouthaman.regimen.domain.model.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetGroupsTest {

    private fun set(number: Int, weightKg: Double?, reps: Int?) =
        SetEntry(workoutExerciseId = "we", setNumber = number, weightKg = weightKg, reps = reps)

    @Test
    fun `consecutive sets at the same weight form one group`() {
        val groups = groupSetsByWeight(
            listOf(set(1, 60.0, 10), set(2, 60.0, 10), set(3, 60.0, 8))
        )
        assertEquals(listOf(SetGroup(60.0, listOf(10, 10, 8))), groups)
    }

    @Test
    fun `a weight change starts a new group, and a return to an earlier weight does not merge`() {
        val groups = groupSetsByWeight(
            listOf(set(1, 60.0, 10), set(2, 65.0, 8), set(3, 60.0, 12))
        )
        assertEquals(
            listOf(
                SetGroup(60.0, listOf(10)),
                SetGroup(65.0, listOf(8)),
                SetGroup(60.0, listOf(12)),
            ),
            groups,
        )
    }

    @Test
    fun `sets are grouped in set-number order regardless of input order`() {
        val groups = groupSetsByWeight(
            listOf(set(3, 65.0, 6), set(1, 60.0, 10), set(2, 60.0, 10))
        )
        assertEquals(
            listOf(SetGroup(60.0, listOf(10, 10)), SetGroup(65.0, listOf(6))),
            groups,
        )
    }

    @Test
    fun `bodyweight sets group under a null weight`() {
        val groups = groupSetsByWeight(listOf(set(1, null, 12), set(2, null, 10)))
        assertEquals(listOf(SetGroup(null, listOf(12, 10))), groups)
    }

    @Test
    fun `isUniform needs more than one set, all with the same known reps`() {
        assertTrue(SetGroup(60.0, listOf(10, 10, 10)).isUniform)
        assertFalse(SetGroup(60.0, listOf(10)).isUniform)
        assertFalse(SetGroup(60.0, listOf(10, 8)).isUniform)
        assertFalse(SetGroup(60.0, listOf(null, null)).isUniform)
    }
}
