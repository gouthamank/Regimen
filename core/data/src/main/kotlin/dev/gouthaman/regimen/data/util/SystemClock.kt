package dev.gouthaman.regimen.data.util

import dev.gouthaman.regimen.domain.util.Clock
import javax.inject.Inject

class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
