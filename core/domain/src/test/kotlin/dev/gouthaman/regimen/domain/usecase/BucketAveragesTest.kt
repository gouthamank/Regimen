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
    fun `empty buckets are omitted, not zero-filled`() {
        val samples = listOf(HeartRateSample(time = 0, bpm = 100))

        val result = bucketAverages(samples, startTime = 0, endTime = 100, bucketCount = 4)

        assertEquals(listOf(100f), result)
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
