package dev.gouthaman.regimen.domain.util

import dev.gouthaman.regimen.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

    @Test
    fun `weightLabel returns kg for metric`() {
        assertEquals(UnitLabel.KG, UnitConverter.weightLabel(UnitSystem.METRIC))
    }

    @Test
    fun `weightLabel returns lb for imperial`() {
        assertEquals(UnitLabel.LB, UnitConverter.weightLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `distanceLabel returns km for metric`() {
        assertEquals(UnitLabel.KM, UnitConverter.distanceLabel(UnitSystem.METRIC))
    }

    @Test
    fun `distanceLabel returns mi for imperial`() {
        assertEquals(UnitLabel.MI, UnitConverter.distanceLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `kgToDisplay is identity under metric`() {
        assertEquals(80.0, UnitConverter.kgToDisplay(80.0, UnitSystem.METRIC), 0.0001)
    }

    @Test
    fun `kgToDisplay converts to pounds under imperial`() {
        assertEquals(176.37, UnitConverter.kgToDisplay(80.0, UnitSystem.IMPERIAL), 0.01)
    }

    @Test
    fun `displayToKg is identity under metric`() {
        assertEquals(80.0, UnitConverter.displayToKg(80.0, UnitSystem.METRIC), 0.0001)
    }

    @Test
    fun `displayToKg converts pounds to kg under imperial`() {
        assertEquals(80.0, UnitConverter.displayToKg(176.37, UnitSystem.IMPERIAL), 0.01)
    }

    @Test
    fun `kgToDisplay and displayToKg round trip under imperial`() {
        val original = 62.5
        val displayed = UnitConverter.kgToDisplay(original, UnitSystem.IMPERIAL)
        val roundTripped = UnitConverter.displayToKg(displayed, UnitSystem.IMPERIAL)
        assertEquals(original, roundTripped, 0.0001)
    }

    @Test
    fun `metersToDisplay converts to kilometers under metric`() {
        assertEquals(5.0, UnitConverter.metersToDisplay(5000.0, UnitSystem.METRIC), 0.0001)
    }

    @Test
    fun `metersToDisplay converts to miles under imperial`() {
        assertEquals(3.10686, UnitConverter.metersToDisplay(5000.0, UnitSystem.IMPERIAL), 0.0001)
    }

    @Test
    fun `displayToMeters converts kilometers to meters under metric`() {
        assertEquals(5000.0, UnitConverter.displayToMeters(5.0, UnitSystem.METRIC), 0.0001)
    }

    @Test
    fun `displayToMeters converts miles to meters under imperial`() {
        assertEquals(1609.344, UnitConverter.displayToMeters(1.0, UnitSystem.IMPERIAL), 0.0001)
    }

    @Test
    fun `metersToDisplay and displayToMeters round trip under imperial`() {
        val original = 12345.0
        val displayed = UnitConverter.metersToDisplay(original, UnitSystem.IMPERIAL)
        val roundTripped = UnitConverter.displayToMeters(displayed, UnitSystem.IMPERIAL)
        assertEquals(original, roundTripped, 0.001)
    }

    @Test
    fun `formatValue trims trailing zero for whole numbers`() {
        assertEquals("80", UnitConverter.formatValue(80.0))
    }

    @Test
    fun `formatValue keeps up to two decimals`() {
        assertEquals("80.25", UnitConverter.formatValue(80.25))
    }

    @Test
    fun `formatValue rounds to two decimals`() {
        assertEquals("80.13", UnitConverter.formatValue(80.126))
    }

    @Test
    fun `formatValue rounds up to a whole number when the fraction rounds away`() {
        assertEquals("81", UnitConverter.formatValue(80.999))
    }

    @Test
    fun `formatValue handles negative values`() {
        assertEquals("-5", UnitConverter.formatValue(-5.0))
    }

    @Test
    fun `formatValue handles zero`() {
        assertEquals("0", UnitConverter.formatValue(0.0))
    }

    @Test
    fun `formatCompact delegates to formatValue at or below one thousand`() {
        assertEquals("1000", UnitConverter.formatCompact(1000.0))
    }

    @Test
    fun `formatCompact abbreviates values over one thousand`() {
        assertEquals("1.50k", UnitConverter.formatCompact(1500.0))
    }

    @Test
    fun `formatCompact abbreviates large values`() {
        assertEquals("12.35k", UnitConverter.formatCompact(12345.0))
    }
}
