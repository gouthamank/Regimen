package dev.gouthaman.regimen.domain.util

import dev.gouthaman.regimen.domain.model.UnitSystem
import kotlin.math.roundToInt

/**
 * Converts between canonical storage units (weight in kg, distance in meters) and the
 * user's chosen display units. Storage is always canonical; conversion happens only here.
 */
/** Canonical unit identifiers for display; resolved to localized text by the UI layer
 * (see `UnitLabel.text()` in ui/util/UnitLabelText.kt) rather than carrying English words here. */
enum class UnitLabel { KG, LB, KM, MI }

object UnitConverter {
    private const val LB_PER_KG = 2.2046226218
    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_KM = 1000.0

    fun weightLabel(system: UnitSystem): UnitLabel =
        if (system == UnitSystem.METRIC) UnitLabel.KG else UnitLabel.LB

    fun distanceLabel(system: UnitSystem): UnitLabel =
        if (system == UnitSystem.METRIC) UnitLabel.KM else UnitLabel.MI

    /** kg (stored) -> displayed weight value in the chosen system. */
    fun kgToDisplay(kg: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) kg else kg * LB_PER_KG

    /** displayed weight value -> kg for storage. */
    fun displayToKg(value: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) value else value / LB_PER_KG

    /** meters (stored) -> displayed distance in the chosen system. */
    fun metersToDisplay(meters: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) meters / METERS_PER_KM else meters / METERS_PER_MILE

    /** displayed distance -> meters for storage. */
    fun displayToMeters(value: Double, system: UnitSystem): Double =
        if (system == UnitSystem.METRIC) value * METERS_PER_KM else value * METERS_PER_MILE

    /** Trims trailing ".0" for whole numbers; keeps up to 2 decimals otherwise. */
    fun formatValue(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
