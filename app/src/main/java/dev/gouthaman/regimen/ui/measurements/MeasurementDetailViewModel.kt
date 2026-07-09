package dev.gouthaman.regimen.ui.measurements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.usecase.AddMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteMeasurementTypeUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.ui.navigation.MeasurementDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One logged entry, formatted for the detail list, retaining the raw record for deletion. */
data class MeasurementEntry(
    val metric: BodyMetric,
    val valueLabel: String,
    val dateMillis: Long,
)

data class MeasurementDetailUiState(
    val type: MeasurementType? = null,
    val weightUnit: UnitSystem = UnitSystem.METRIC,
    /** Display-unit values in chronological order, for the trend chart. */
    val trend: List<Float> = emptyList(),
    /** Entries newest-first for the list. */
    val entries: List<MeasurementEntry> = emptyList(),
    val loaded: Boolean = false,
) {
    val canDeleteType: Boolean get() = type?.isBuiltIn == false
}

@HiltViewModel
class MeasurementDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeTypes: ObserveMeasurementTypesUseCase,
    observeMeasurements: ObserveMeasurementsUseCase,
    observePreferences: ObservePreferencesUseCase,
    private val addMeasurementUseCase: AddMeasurementUseCase,
    private val deleteMeasurementUseCase: DeleteMeasurementUseCase,
    private val deleteTypeUseCase: DeleteMeasurementTypeUseCase,
) : ViewModel() {

    private val typeId = savedStateHandle.toRoute<MeasurementDetailRoute>().typeId

    val uiState: StateFlow<MeasurementDetailUiState> = combine(
        observeTypes().map { types -> types.firstOrNull { it.id == typeId } },
        observeMeasurements(typeId),
        observePreferences(),
    ) { type, metrics, prefs ->
        if (type == null) {
            MeasurementDetailUiState(loaded = true)
        } else {
            val system = prefs.weightUnit
            MeasurementDetailUiState(
                type = type,
                weightUnit = system,
                trend = metrics.map {
                    MeasurementFormat.toDisplay(type, it.value, system).toFloat()
                },
                entries = metrics.sortedByDescending { it.date }.map { metric ->
                    MeasurementEntry(
                        metric = metric,
                        valueLabel = MeasurementFormat.format(type, metric.value, system),
                        dateMillis = metric.date,
                    )
                },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeasurementDetailUiState())

    fun addEntry(date: Long, displayValue: Double) {
        val type = uiState.value.type ?: return
        val stored = MeasurementFormat.toStored(type, displayValue, uiState.value.weightUnit)
        viewModelScope.launch { addMeasurementUseCase(type.id, date, stored) }
    }

    fun deleteEntry(entry: MeasurementEntry) {
        viewModelScope.launch { deleteMeasurementUseCase(entry.metric) }
    }

    /** Delete this (custom) type; cascades its entries. No-op for the built-in bodyweight type. */
    fun deleteType() {
        val type = uiState.value.type ?: return
        if (type.isBuiltIn) return
        viewModelScope.launch { deleteTypeUseCase(type) }
    }
}
