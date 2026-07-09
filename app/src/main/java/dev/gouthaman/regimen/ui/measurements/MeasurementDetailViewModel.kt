package dev.gouthaman.regimen.ui.measurements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gouthaman.regimen.data.local.entity.BodyMetric
import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.domain.model.HistoryRange
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.model.cutoffMillis
import dev.gouthaman.regimen.domain.usecase.AddMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteMeasurementTypeUseCase
import dev.gouthaman.regimen.domain.usecase.DeleteMeasurementUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementTypesUseCase
import dev.gouthaman.regimen.domain.usecase.ObserveMeasurementsUseCase
import dev.gouthaman.regimen.domain.usecase.ObservePreferencesUseCase
import dev.gouthaman.regimen.ui.navigation.MeasurementDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Display-unit values in chronological order, for the trend chart (filtered by [range]). */
    val trend: List<Float> = emptyList(),
    val range: HistoryRange = HistoryRange.THREE_MONTHS,
    /** Entries newest-first for the list (full history, not filtered by [range]). */
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

    private val _range = MutableStateFlow(HistoryRange.THREE_MONTHS)
    val range: StateFlow<HistoryRange> = _range.asStateFlow()

    val uiState: StateFlow<MeasurementDetailUiState> = combine(
        observeTypes().map { types -> types.firstOrNull { it.id == typeId } },
        observeMeasurements(typeId),
        observePreferences(),
        _range,
    ) { type, metrics, prefs, range ->
        if (type == null) {
            MeasurementDetailUiState(loaded = true)
        } else {
            val system = prefs.weightUnit
            val cutoff = range.cutoffMillis()
            MeasurementDetailUiState(
                type = type,
                weightUnit = system,
                trend = metrics
                    .filter { cutoff == null || it.date >= cutoff }
                    .sortedBy { it.date }
                    .map { MeasurementFormat.toDisplay(type, it.value, system).toFloat() },
                range = range,
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

    fun setRange(value: HistoryRange) {
        _range.value = value
    }

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
