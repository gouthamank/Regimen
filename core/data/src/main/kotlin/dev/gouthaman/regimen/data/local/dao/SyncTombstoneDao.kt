package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.data.local.entity.SyncTombstoneEntity

/** The sync tombstone table's single point of read/write. Writes are driven by the repository
 * layer (e.g. `WorkoutRepositoryImpl`), not the entity DAOs - a repository already orchestrates
 * cross-DAO cascade-victim enumeration (via `RoomDatabase.withTransaction`) before deciding what
 * to tombstone, so the write belongs there rather than duplicated per entity DAO. Reads/clears are
 * the future sync push job's view. */
@Dao
interface SyncTombstoneDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tombstone: SyncTombstoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tombstones: List<SyncTombstoneEntity>)

    @Query("SELECT * FROM sync_tombstones")
    suspend fun getAll(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE entityType = :type AND entityId = :entityId")
    suspend fun clear(type: SyncEntityType, entityId: String)
}
