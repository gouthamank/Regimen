package dev.gouthaman.regimen.feature.measurements

import app.cash.turbine.test
import dev.gouthaman.regimen.domain.model.BodyMetric
import dev.gouthaman.regimen.domain.model.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.UserPreferences
import dev.gouthaman.regimen.domain.usecase.AddMeasurementTypeUseCase
import dev.gouthaman.regimen.domain.usecase.AddMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.testing.FakeMeasurementRepository
import dev.gouthaman.regimen.testing.FakePreferencesRepository
import dev.gouthaman.regimen.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MeasurementsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        measurementRepo: FakeMeasurementRepository,
        preferencesRepo: FakePreferencesRepository,
    ) = MeasurementsViewModel(
        observeTypes = ObserveMeasurementTypesUseCase(measurementRepo),
        observeMeasurements = ObserveMeasurementsUseCase(measurementRepo),
        observePreferences = ObservePreferencesUseCase(preferencesRepo),
        addTypeUseCase = AddMeasurementTypeUseCase(measurementRepo),
        addMeasurementUseCase = AddMeasurementUseCase(measurementRepo),
    )

    @Test
    fun `no measurement types yields an empty row list`() = runTest {
        val viewModel = viewModel(FakeMeasurementRepository(), FakePreferencesRepository())

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(emptyList<MeasurementRow>(), state.rows)
            assertEquals(true, state.loaded)
        }
    }

    @Test
    fun `a built-in type's trend converts to the current weight unit but latestValue stays canonical`() =
        runTest {
            val measurementRepo = FakeMeasurementRepository()
            measurementRepo.seedTypes(
                MeasurementType(
                    id = "1",
                    name = "Bodyweight",
                    unit = "kg",
                    isBuiltIn = true
                )
            )
            measurementRepo.seedMetrics(
                BodyMetric(id = "1", measurementTypeId = "1", date = 1_000, value = 80.0),
            )
            val preferencesRepo = FakePreferencesRepository()
            preferencesRepo.seed(UserPreferences(weightUnit = UnitSystem.IMPERIAL))
            val viewModel = viewModel(measurementRepo, preferencesRepo)

            viewModel.uiState.test {
                val row = awaitItem().rows.single()
                assertEquals(80.0, row.latestValue)
                assertEquals(1, row.trend.size)
                assertEquals(176.37f, row.trend[0], 0.01f)
            }
        }

    @Test
    fun `a custom type is never unit-converted`() = runTest {
        val measurementRepo = FakeMeasurementRepository()
        measurementRepo.seedTypes(
            MeasurementType(
                id = "1",
                name = "Waist",
                unit = "cm",
                isBuiltIn = false
            )
        )
        measurementRepo.seedMetrics(
            BodyMetric(id = "1", measurementTypeId = "1", date = 1_000, value = 80.0),
        )
        val preferencesRepo = FakePreferencesRepository()
        preferencesRepo.seed(UserPreferences(weightUnit = UnitSystem.IMPERIAL))
        val viewModel = viewModel(measurementRepo, preferencesRepo)

        viewModel.uiState.test {
            val row = awaitItem().rows.single()
            assertEquals(80.0f, row.trend[0], 0.001f)
        }
    }

    @Test
    fun `latestValue reflects the highest-date metric, not the last one added`() = runTest {
        val measurementRepo = FakeMeasurementRepository()
        measurementRepo.seedTypes(
            MeasurementType(
                id = "1",
                name = "Bodyweight",
                unit = "kg",
                isBuiltIn = true
            )
        )
        measurementRepo.seedMetrics(
            BodyMetric(id = "1", measurementTypeId = "1", date = 5_000, value = 78.0),
            BodyMetric(id = "2", measurementTypeId = "1", date = 1_000, value = 82.0),
        )
        val viewModel = viewModel(measurementRepo, FakePreferencesRepository())

        viewModel.uiState.test {
            assertEquals(78.0, awaitItem().rows.single().latestValue)
        }
    }

    @Test
    fun `entryCount reflects the number of logged metrics`() = runTest {
        val measurementRepo = FakeMeasurementRepository()
        measurementRepo.seedTypes(
            MeasurementType(
                id = "1",
                name = "Bodyweight",
                unit = "kg",
                isBuiltIn = true
            )
        )
        measurementRepo.seedMetrics(
            BodyMetric(id = "1", measurementTypeId = "1", date = 1_000, value = 80.0),
            BodyMetric(id = "2", measurementTypeId = "1", date = 2_000, value = 79.0),
        )
        val viewModel = viewModel(measurementRepo, FakePreferencesRepository())

        viewModel.uiState.test {
            assertEquals(2, awaitItem().rows.single().entryCount)
        }
    }

    @Test
    fun `a blank type name is not added`() = runTest {
        val measurementRepo = FakeMeasurementRepository()
        val viewModel = viewModel(measurementRepo, FakePreferencesRepository())

        viewModel.addType("   ", "kg")

        viewModel.uiState.test {
            assertEquals(emptyList<MeasurementRow>(), awaitItem().rows)
        }
    }

    @Test
    fun `adding an entry converts the display value to canonical storage for a built-in type`() =
        runTest {
            val measurementRepo = FakeMeasurementRepository()
            measurementRepo.seedTypes(
                MeasurementType(
                    id = "1",
                    name = "Bodyweight",
                    unit = "kg",
                    isBuiltIn = true
                )
            )
            val preferencesRepo = FakePreferencesRepository()
            preferencesRepo.seed(UserPreferences(weightUnit = UnitSystem.IMPERIAL))
            val viewModel = viewModel(measurementRepo, preferencesRepo)

            viewModel.uiState.test { awaitItem() }
            viewModel.addEntry(typeId = "1", date = 1_000, displayValue = 176.37)

            viewModel.uiState.test {
                val latest = awaitItem().rows.single().latestValue!!
                assertEquals(80.0, latest, 0.01)
            }
        }

    @Test
    fun `adding an entry for an unknown type is a no-op`() = runTest {
        val measurementRepo = FakeMeasurementRepository()
        measurementRepo.seedTypes(
            MeasurementType(
                id = "1",
                name = "Bodyweight",
                unit = "kg",
                isBuiltIn = true
            )
        )
        val viewModel = viewModel(measurementRepo, FakePreferencesRepository())
        viewModel.uiState.test { awaitItem() }

        viewModel.addEntry(typeId = "missing", date = 1_000, displayValue = 50.0)

        measurementRepo.observeMetrics("1").test {
            assertEquals(emptyList<Any>(), awaitItem())
        }
    }
}
