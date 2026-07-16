package dev.gouthaman.regimen.domain.service

/**
 * Fires the rest-complete alert (S14): vibration + optional chime + a system notification.
 * The concrete implementation needs an Android `Context` and notification APIs, so it lives in
 * `:app` (`service/RestAlertsImpl.kt`) - this interface is what lets a feature module's ViewModel
 * depend on the capability without depending on `:app` itself, same as the repository interfaces
 * in `domain.repository`.
 */
interface RestAlerts {
    /** [workoutId] is used to deep-link the notification's tap target back into that session's
     * Active Workout screen. */
    fun fire(workoutId: Long, chimeEnabled: Boolean = true)
}
