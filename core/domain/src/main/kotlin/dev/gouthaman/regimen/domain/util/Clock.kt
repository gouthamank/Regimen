package dev.gouthaman.regimen.domain.util

interface Clock {
    fun nowMillis(): Long
}
