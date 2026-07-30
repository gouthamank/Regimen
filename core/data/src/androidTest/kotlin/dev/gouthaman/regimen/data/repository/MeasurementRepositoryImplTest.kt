package dev.gouthaman.regimen.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.gouthaman.regimen.data.local.RegimenDatabase
import dev.gouthaman.regimen.data.local.entity.SyncEntityType
import dev.gouthaman.regimen.domain.model.MeasurementType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementRepositoryImplTest {

    private lateinit var db: RegimenDatabase
    private lateinit var repository: MeasurementRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RegimenDatabase::class.java).build()
        repository = MeasurementRepositoryImpl(db.measurementDao(), db.syncTombstoneDao(), db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteType_tombstonesTheTypeAndItsCascadedMetrics() = runTest {
        val typeId = repository.addType("Waist", "cm")
        val metricId = repository.addMetric(typeId, date = 1_000, value = 80.0)

        repository.deleteType(MeasurementType(id = typeId, name = "Waist", unit = "cm"))

        val tombstones = db.syncTombstoneDao().getAll().associateBy { it.entityId }
        assertEquals(2, tombstones.size)
        assertEquals(SyncEntityType.MEASUREMENT_TYPE, tombstones[typeId]?.entityType)
        assertEquals(SyncEntityType.BODY_METRIC, tombstones[metricId]?.entityType)
        assertNull(tombstones[metricId]?.parentId)
    }
}
