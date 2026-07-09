package dev.gouthaman.regimen.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurement_types ORDER BY isBuiltIn DESC, name COLLATE NOCASE ASC")
    fun observeTypes(): Flow<List<MeasurementType>>

    @Query("SELECT COUNT(*) FROM measurement_types")
    suspend fun typeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertType(type: MeasurementType): Long

    @Update
    suspend fun updateType(type: MeasurementType)

    @Delete
    suspend fun deleteType(type: MeasurementType)

    @Query("SELECT * FROM body_metrics WHERE measurementTypeId = :typeId ORDER BY date ASC")
    fun observeMetricsForType(typeId: Long): Flow<List<BodyMetric>>

    @Query("SELECT * FROM body_metrics WHERE measurementTypeId = :typeId ORDER BY date DESC LIMIT 1")
    fun observeLatestForType(typeId: Long): Flow<BodyMetric?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: BodyMetric): Long

    @Delete
    suspend fun deleteMetric(metric: BodyMetric)
}
