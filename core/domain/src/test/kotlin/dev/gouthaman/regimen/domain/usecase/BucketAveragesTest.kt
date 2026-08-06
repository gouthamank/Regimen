package dev.gouthaman.regimen.domain.usecase

import dev.gouthaman.regimen.domain.model.HeartRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BucketAveragesTest {

    @Test
    fun `averages samples per bucket in chronological order`() {
        val samples = listOf(
            HeartRateSample(time = 0, bpm = 100),
            HeartRateSample(time = 10, bpm = 140),
            HeartRateSample(time = 50, bpm = 120),
        )

        val result = bucketAverages(samples, startTime = 0, endTime = 100, bucketCount = 2)

        assertEquals(listOf(120f, 120f), result)
    }

    @Test
    fun `always returns bucketCount points - a single sample forward-fills every bucket`() {
        val samples = listOf(HeartRateSample(time = 0, bpm = 100))

        val result = bucketAverages(samples, startTime = 0, endTime = 100, bucketCount = 4)

        assertEquals(listOf(100f, 100f, 100f, 100f), result)
    }

    @Test
    fun `interior gaps are linearly interpolated between their neighbors`() {
        // bucket width 25: sample at 0 -> bucket 0 (100), sample at 100 -> bucket 4 (200),
        // buckets 1-3 are empty and should interpolate evenly between 100 and 200.
        val samples = listOf(
            HeartRateSample(time = 0, bpm = 100),
            HeartRateSample(time = 100, bpm = 200),
        )

        val result = bucketAverages(samples, startTime = 0, endTime = 125, bucketCount = 5)

        assertEquals(listOf(100f, 125f, 150f, 175f, 200f), result)
    }

    @Test
    fun `a trailing gap after the last sample backfills from the last known value`() {
        val samples =
            listOf(HeartRateSample(time = 0, bpm = 100), HeartRateSample(time = 10, bpm = 120))

        val result = bucketAverages(samples, startTime = 0, endTime = 100, bucketCount = 4)

        assertEquals(listOf(110f, 110f, 110f, 110f), result)
    }

    @Test
    fun `no samples or a degenerate time range returns an empty list`() {
        assertTrue(bucketAverages(emptyList(), 0, 100, 10).isEmpty())
        assertTrue(
            bucketAverages(
                listOf(HeartRateSample(0, 100)),
                startTime = 100,
                endTime = 100,
                bucketCount = 10
            )
                .isEmpty(),
        )
    }
}
