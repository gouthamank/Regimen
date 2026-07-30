package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.BodyMetricEntity
import dev.gouthaman.regimen.data.local.entity.MeasurementTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement_types ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeTypes(): Flow<List<MeasurementTypeEntity>>

    @Query("SELECT COUNT(*) FROM measurement_types")
    suspend fun typeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertType(type: MeasurementTypeEntity)

    @Update
    suspend fun updateType(type: MeasurementTypeEntity)

    @Delete
    suspend fun deleteType(type: MeasurementTypeEntity)

    @Query("SELECT * FROM body_metrics WHERE measurementTypeId = :typeId ORDER BY date ASC")
    fun observeMetricsForType(typeId: String): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics WHERE measurementTypeId = :typeId ORDER BY date DESC LIMIT 1")
    fun observeLatestForType(typeId: String): Flow<BodyMetricEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: BodyMetricEntity)

    @Delete
    suspend fun deleteMetric(metric: BodyMetricEntity)

    /** Cascade-victim enumeration for the sync tombstone write, which lives in
     * `MeasurementRepositoryImpl` (see
     * [dev.gouthaman.regimen.data.repository.MeasurementRepositoryImpl]) - this DAO only exposes
     * the raw child-id lookup, since only a DAO can run a typed Room query. */
    @Query("SELECT id FROM body_metrics WHERE measurementTypeId = :typeId")
    suspend fun bodyMetricIdsFor(typeId: String): List<String>

    /** Sync push job's read/clear side. Built-in types (`isBuiltIn = 1`, e.g. "Bodyweight") are
     * never in sync scope. */
    @Query(
        "SELECT * FROM measurement_types WHERE isBuiltIn = 0 AND isDirty = 1 " +
                "ORDER BY lastModifiedAt ASC LIMIT :limit"
    )
    suspend fun getDirtyTypes(limit: Int): List<MeasurementTypeEntity>

    @Query("UPDATE measurement_types SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtyTypes(ids: List<String>)

    @Query("SELECT * FROM body_metrics WHERE isDirty = 1 ORDER BY lastModifiedAt ASC LIMIT :limit")
    suspend fun getDirtyMetrics(limit: Int): List<BodyMetricEntity>

    @Query("UPDATE body_metrics SET isDirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirtyMetrics(ids: List<String>)

    /** "Pull cloud data"'s wipe/insert side. Only non-built-in types are ever uploaded, same
     * scope as [getDirtyTypes] - built-ins have nothing in the cloud to be replaced by. */
    @Query("DELETE FROM measurement_types WHERE isBuiltIn = 0")
    suspend fun deleteAllCustomTypes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTypes(types: List<MeasurementTypeEntity>)

    /** Every `BodyMetric` is in sync scope regardless of its parent type's `isBuiltIn` (see
     * [getDirtyMetrics] - no such filter there either), so this wipes all of them, not just ones
     * under custom types. */
    @Query("DELETE FROM body_metrics")
    suspend fun deleteAllMetrics()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMetrics(metrics: List<BodyMetricEntity>)

    /** "Claim primary"'s force-full-upload side - see [dev.gouthaman.regimen.data.local.dao.ExerciseDao.markAllCustomDirty]. */
    @Query("UPDATE measurement_types SET isDirty = 1 WHERE isBuiltIn = 0")
    suspend fun markAllCustomTypesDirty()

    @Query("UPDATE body_metrics SET isDirty = 1")
    suspend fun markAllMetricsDirty()
}
