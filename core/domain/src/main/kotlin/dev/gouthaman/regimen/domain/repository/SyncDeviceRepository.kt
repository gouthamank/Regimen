package dev.gouthaman.regimen.domain.repository

interface SyncDeviceRepository {
    /** Checks the signed-in account's live primary-device record and silently claims it if unset
     * (the common single-device case - no confirmation needed, nothing to protect against with an
     * empty claim). Returns whether this device is (now) the primary; `false` means a different
     * device already holds it, which the caller can't resolve here - see the dedicated
     * secondary-device UI for that. Also `false` if nobody's signed in. */
    suspend fun ensurePrimaryClaimed(): Boolean
}
