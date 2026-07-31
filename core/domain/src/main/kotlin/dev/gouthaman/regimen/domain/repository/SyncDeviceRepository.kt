package dev.gouthaman.regimen.domain.repository

import dev.gouthaman.regimen.domain.model.SecondaryDeviceReason

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

    /** Which reason the secondary-device disclaimer/Pull/Claim UI should show right now - `null`
     * means this device is safely primary (or nobody's claimed primary yet). Deliberately distinct
     * from `!isPrimary()`, which can't tell "a different device is primary" apart from "this
     * device is primary but its local state can't be trusted." */
    suspend fun secondaryDeviceReason(): SecondaryDeviceReason?
}
