package com.coolguy.feeCalc

data class FeeInput(
    val homeToStartMeters: Int,
    val homeToStartSeconds: Int,
    val startToEndMeters: Int,
    val startToEndSeconds: Int,
    val endToHomeMeters: Int,
    val endToHomeSeconds: Int,
    val mileageRate: Double,
    val laborRate: Double
)

data class FeeResult(
    val totalMiles: Double,
    val totalHours: Double,
    val totalFee: Double
)

object FeeCalculator {
    private const val LOAD_IN_MINUTES = 20
    private const val LOAD_OUT_MINUTES = 20

    fun calculate(input: FeeInput): FeeResult {
        val totalMeters = input.homeToStartMeters +
                input.startToEndMeters +
                input.endToHomeMeters
        val totalMiles = totalMeters / 1609.344

        val totalDriveSeconds = input.homeToStartSeconds +
                input.startToEndSeconds +
                input.endToHomeSeconds
        val totalLaborMinutes = (totalDriveSeconds / 60.0) +
                LOAD_IN_MINUTES +
                LOAD_OUT_MINUTES
        val totalHours = totalLaborMinutes / 60.0

        val mileageFee = totalMiles * input.mileageRate
        val laborFee = totalHours * input.laborRate
        val totalFee = mileageFee + laborFee

        return FeeResult(totalMiles, totalHours, totalFee)
    }
}
