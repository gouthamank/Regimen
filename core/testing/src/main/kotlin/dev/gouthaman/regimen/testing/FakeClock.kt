package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.util.Clock

class FakeClock(var currentMillis: Long = 0L) : Clock {

    override fun nowMillis(): Long = currentMillis

    fun advanceBy(millis: Long) {
        currentMillis += millis
    }
}
