package dev.gouthaman.regimen.domain.model

/** Why the secondary-device disclaimer/Pull/Claim UI is showing on this device right now - two
 * distinct situations that both resolve via the same two actions (Pull cloud data / "Use this
 * device instead"), but call for different explanatory copy since they mean different things. */
enum class SecondaryDeviceReason {
    /** A different device holds the live primary-device record. */
    COMPETING_PRIMARY,

    /** This device's own ID still says primary, but its local freshness watermark doesn't match
     * what the cloud says was last pushed - e.g. an Auto Backup restore landed with a stale
     * snapshot. Not safe to resume automatic push from until resolved. */
    STALE_LOCAL_STATE,
}
