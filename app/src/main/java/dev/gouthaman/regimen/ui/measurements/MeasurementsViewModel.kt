package dev.gouthaman.regimen.ui.measurements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.AddMeasurementTypeUseCase
import dev.gouthaman.regimen.domain.usecase.AddMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in the Body Measurements list: a type plus a summary of its logged history. */
data class MeasurementRow(
    val type: MeasurementType,
    val latestLabel: String?,
    val unitLabel: String,
    /** Display-unit values in chronological order, for the inline sparkline. */
    val trend: List<Float>,
    val entryCount: Int,
)

data class MeasurementsUiState(
    val rows: List<MeasurementRow> = emptyList(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MeasurementsViewModel @Inject constructor(
    observeTypes: ObserveMeasurementTypesUseCase,
    private val observeMeasurements: ObserveMeasurementsUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val addTypeUseCase: AddMeasurementTypeUseCase,
    private val addMeasurementUseCase: AddMeasurementUseCase,
) : ViewModel() {

    // Each type paired with its (date-ascending) metrics; recombined whenever the type set changes.
    private val typesWithMetrics: Flow<List<Pair<MeasurementType, List<BodyMetric>>>> =
        observeTypes().flatMapLatest { types ->
            if (types.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(types.map { type -> observeMeasurements(type.id).map { type to it } }) {
                    it.toList()
                }
            }
        }

    val uiState: StateFlow<MeasurementsUiState> =
        combine(typesWithMetrics, observePreferences()) { pairs, prefs ->
            val rows = pairs.map { (type, metrics) ->
                type.toRow(metrics, prefs.unitSystem)
            }
            MeasurementsUiState(rows = rows, unitSystem = prefs.unitSystem, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementsUiState())

    private fun MeasurementType.toRow(metrics: List<BodyMetric>, system: UnitSystem): MeasurementRow {
        val latest = metrics.maxByOrNull { it.date }
        return MeasurementRow(
            type = this,
            latestLabel = latest?.let { MeasurementFormat.format(this, it.value, system) },
            unitLabel = MeasurementFormat.unitLabel(this, system),
            trend = metrics.map { MeasurementFormat.toDisplay(this, it.value, system).toFloat() },
            entryCount = metrics.size,
        )
    }

    /** Add a custom measurement type. [unit] is free text (e.g. "cm", "%"). */
    fun addType(name: String, unit: String) {
        if (name.isBlank()) return
        viewModelScope.launch { addTypeUseCase(name, unit) }
    }

    /** Log an entry for [typeId]; [displayValue] is in the user's units and converted for storage. */
    fun addEntry(typeId: Long, date: Long, displayValue: Double) {
        val type = uiState.value.rows.firstOrNull { it.type.id == typeId }?.type ?: return
        val stored = MeasurementFormat.toStored(type, displayValue, uiState.value.unitSystem)
        viewModelScope.launch { addMeasurementUseCase(typeId, date, stored) }
    }
}
