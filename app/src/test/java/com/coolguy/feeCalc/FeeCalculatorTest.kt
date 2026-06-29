package com.coolguy.feeCalc

import org.junit.Assert.assertEquals
import org.junit.Test

class FeeCalculatorTest {

    @Test
    fun `calculate with zero distances returns zero fee`() {
        val input = FeeInput(
            homeToStartMeters = 0,
            homeToStartSeconds = 0,
            startToEndMeters = 0,
            startToEndSeconds = 0,
            endToHomeMeters = 0,
            endToHomeSeconds = 0,
            mileageRate = 0.34,
            laborRate = 30.0
        )

        val result = FeeCalculator.calculate(input)

        assertEquals(0.0, result.totalMiles, 0.01)
        assertEquals(0.67, result.totalHours, 0.01)
        assertEquals(20.0, result.totalFee, 0.01)
    }

    @Test
    fun `calculate with known route values matches expected fee`() {
        val input = FeeInput(
            homeToStartMeters = 5149,
            homeToStartSeconds = 420,
            startToEndMeters = 11265,
            startToEndSeconds = 720,
            endToHomeMeters = 6115,
            endToHomeSeconds = 600,
            mileageRate = 0.34,
            laborRate = 30.0
        )

        val result = FeeCalculator.calculate(input)

        assertEquals(14.0, result.totalMiles, 0.1)
        assertEquals(1.15, result.totalHours, 0.01)
    }

    @Test
    fun `calculate with high rates multiplies correctly`() {
        val input = FeeInput(
            homeToStartMeters = 1609,
            homeToStartSeconds = 120,
            startToEndMeters = 1609,
            startToEndSeconds = 120,
            endToHomeMeters = 1609,
            endToHomeSeconds = 120,
            mileageRate = 1.0,
            laborRate = 60.0
        )

        val result = FeeCalculator.calculate(input)

        assertEquals(3.0, result.totalMiles, 0.01)
        val expectedHours = (360.0 / 60.0 + 40.0) / 60.0
        assertEquals(expectedHours, result.totalHours, 0.01)
    }

    @Test
    fun `calculate single mile at default rates`() {
        val input = FeeInput(
            homeToStartMeters = 1609,
            homeToStartSeconds = 120,
            startToEndMeters = 0,
            startToEndSeconds = 0,
            endToHomeMeters = 0,
            endToHomeSeconds = 0,
            mileageRate = 0.34,
            laborRate = 30.0
        )

        val result = FeeCalculator.calculate(input)

        val expectedMiles = 1609.0 / 1609.344
        assertEquals(expectedMiles, result.totalMiles, 0.01)
    }
}
