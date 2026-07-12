package dev.gouthaman.regimen.data.local

import dev.gouthaman.regimen.data.local.dao.ExerciseDao
import dev.gouthaman.regimen.data.local.dao.MeasurementDao
import dev.gouthaman.regimen.data.local.seed.BuiltInData
import javax.inject.Inject
import javax.inject.Singleton

/** Populates the built-in exercise library and measurement types on first launch. */
@Singleton
class Seeder @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val measurementDao: MeasurementDao,
) {
    suspend fun seedIfNeeded() {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAll(BuiltInData.exercises)
        }
        if (measurementDao.typeCount() == 0) {
            BuiltInData.measurementTypes.forEach { measurementDao.insertType(it) }
        }
    }
}
