package dev.gouthaman.regimen.testing

import dev.gouthaman.regimen.domain.service.RestAlerts

class FakeRestAlerts : RestAlerts {

    data class FiredAlert(val workoutId: Long, val chimeEnabled: Boolean)

    private val _fired = mutableListOf<FiredAlert>()
    val fired: List<FiredAlert> get() = _fired

    override fun fire(workoutId: Long, chimeEnabled: Boolean) {
        _fired += FiredAlert(workoutId, chimeEnabled)
    }
}
