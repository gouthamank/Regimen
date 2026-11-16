package dev.gouthaman.regimen.domain.model

/** Which part of a Pull cloud data / Use this device instead / Delete cloud data run is currently
 * in progress - drives the Account screen's progress dialog. */
enum class SyncOperationStep {
    STARTING,

    /** Listing every cloud document ahead of deleting them - the total isn't known until this
     * finishes. */
    FINDING_CLOUD_DATA,
    DELETING_CLOUD_DATA,
    DOWNLOADING,
    SAVING,
    UPLOADING,
    DELETING_ACCOUNT,
}

/** [total] is null while it isn't known (still finding/downloading) - [done] then just counts up
 * on its own. [done] can briefly exceed [total] when rows are edited mid-upload, so consumers
 * should clamp. */
data class SyncOperationProgress(
    val step: SyncOperationStep,
    val done: Int = 0,
    val total: Int? = null,
)
