package dev.gouthaman.regimen.domain.service

/**
 * Fires the rest-complete alert: vibration + optional chime + a system notification. The concrete
 * implementation needs an Android `Context` and notification APIs, so it lives in `:app` - this
 * interface lets a feature module's ViewModel depend on the capability without depending on `:app`.
 */
interface RestAlerts {
    /** [workoutId] is used to deep-link the notification's tap target back into that session's
     * Active Workout screen. */
    fun fire(workoutId: String, chimeEnabled: Boolean = true)
}
