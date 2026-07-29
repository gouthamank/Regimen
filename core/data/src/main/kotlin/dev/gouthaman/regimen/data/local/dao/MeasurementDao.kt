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
}
