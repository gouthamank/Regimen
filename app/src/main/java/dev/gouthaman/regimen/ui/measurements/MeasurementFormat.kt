package dev.gouthaman.regimen.ui.measurements

import dev.gouthaman.regimen.data.local.entity.MeasurementType
import dev.gouthaman.regimen.domain.model.UnitSystem
import dev.gouthaman.regimen.domain.util.UnitConverter

/**
 * Bodyweight (the built-in type) is stored canonically in kg and displayed in the user's chosen
 * weight unit. Custom types carry their own free-text unit (e.g. "cm", "%") and are stored and
 * shown as-entered — no conversion.
 */
object MeasurementFormat {

    /** The unit label to show for [type] given the current [weightUnit] (kg/lb for bodyweight). */
    fun unitLabel(type: MeasurementType, weightUnit: UnitSystem): String =
        if (type.isBuiltIn) UnitConverter.weightLabel(weightUnit) else type.unit

    /** Stored value → the numeric value shown to the user. */
    fun toDisplay(type: MeasurementType, storedValue: Double, weightUnit: UnitSystem): Double =
        if (type.isBuiltIn) UnitConverter.kgToDisplay(storedValue, weightUnit) else storedValue

    /** A value the user typed (in display units) → the value to store. */
    fun toStored(type: MeasurementType, displayValue: Double, weightUnit: UnitSystem): Double =
        if (type.isBuiltIn) UnitConverter.displayToKg(displayValue, weightUnit) else displayValue

    /** Stored value → "72 kg" / "34.5 cm". */
    fun format(type: MeasurementType, storedValue: Double, weightUnit: UnitSystem): String =
        "${UnitConverter.formatValue(toDisplay(type, storedValue, weightUnit))} ${
            unitLabel(
                type,
                weightUnit
            )
        }"
}
