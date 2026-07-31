package dev.gouthaman.regimen.data.local.entity

import androidx.room.Entity

/** Every Room entity that's part of remote sync scope, one per Firestore collection it maps to. */
enum class SyncEntityType {
    EXERCISE, MEASUREMENT_TYPE, BODY_METRIC, ROUTINE, ROUTINE_EXERCISE,
    WORKOUT, WORKOUT_EXERCISE, SET_ENTRY, CARDIO_ENTRY,
}

/** A pending-deletion record for the sync push job - Room's cascade deletes leave no trace a row
 * ever existed, so this is what lets the push job know to delete the matching Firestore document
 * too. [parentId]/[grandparentId] capture the ancestor ids needed to build the deleted document's
 * Firestore path for entity types nested under a parent collection there; null for flat top-level
 * types. Cleared by the push job once the matching Firestore document is confirmed deleted. */
@Entity(tableName = "sync_tombstones", primaryKeys = ["entityType", "entityId"])
data class SyncTombstoneEntity(
    val entityType: SyncEntityType,
    val entityId: String,
    val parentId: String? = null,
    val grandparentId: String? = null,
    val deletedAt: Long = System.currentTimeMillis(),
)
