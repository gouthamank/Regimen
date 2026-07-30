package dev.gouthaman.regimen.domain.repository

interface SyncDeviceRepository {
    /** Checks the signed-in account's live primary-device record and silently claims it if unset
     * (the common single-device case - no confirmation needed, nothing to protect against with an
     * empty claim). Returns whether this device is (now) the primary; `false` means a different
     * device already holds it, which the caller can't resolve here - see the dedicated
     * secondary-device UI for that. Also `false` if nobody's signed in. */
    suspend fun ensurePrimaryClaimed(): Boolean

    /** A plain read of the same live primary-device record `ensurePrimaryClaimed` checks - no
     * claim attempt, just "is this device still the one allowed to push automatically right now."
     * The push job's first step every run, since primary status can change between runs (another
     * device claiming it) without this device doing anything itself. `false` if nobody's signed
     * in, or if a different device holds it. */
    suspend fun isPrimary(): Boolean

    /** True only when a *different* device already holds the live primary-device record - the
     * gate for showing the secondary-device disclaimer/Pull/Claim UI. Deliberately distinct from
     * `!isPrimary()`, which is also true when nobody has claimed primary at all yet (the common
     * brand-new-account case, where nothing extra should be shown). `false` if nobody's signed
     * in, if primary is unset, or if this device itself already holds it. */
    suspend fun hasCompetingPrimary(): Boolean
}
