package com.sportage.obd

import com.sportage.obd.fuel.FuelEconomyFormatter as FuelFuelEconomyFormatter

/** Compatibility alias in the root package. */
object FuelEconomyFormatter {
    const val IDLE_SPEED_THRESHOLD_KMH = com.sportage.obd.fuel.FuelEconomyFormatter.IDLE_SPEED_THRESHOLD_KMH

    fun kmPerLiter(speedKmh: Double, fuelFlowLitersPerHour: Double): Double? =
        com.sportage.obd.fuel.FuelEconomyFormatter.kmPerLiter(speedKmh, fuelFlowLitersPerHour)

    fun kmPerLiter(speedKmh: Float, fuelFlowLitersPerHour: Float): Double? =
        com.sportage.obd.fuel.FuelEconomyFormatter.kmPerLiter(speedKmh, fuelFlowLitersPerHour)

    fun kmPerLiter(speedKmh: Int, fuelFlowLitersPerHour: Double): Double? =
        com.sportage.obd.fuel.FuelEconomyFormatter.kmPerLiter(speedKmh, fuelFlowLitersPerHour)

    fun format(speedKmh: Double, fuelFlowLitersPerHour: Double): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.format(speedKmh, fuelFlowLitersPerHour)

    fun format(speedKmh: Float, fuelFlowLitersPerHour: Float): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.format(speedKmh, fuelFlowLitersPerHour)

    fun format(speedKmh: Int, fuelFlowLitersPerHour: Double): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.format(speedKmh, fuelFlowLitersPerHour)

    fun format(speedKmh: Int, fuelFlowLitersPerHour: Float): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.format(speedKmh, fuelFlowLitersPerHour)

    fun formatAverageKmPerLiter(kmPerLiter: Double?): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.formatAverageKmPerLiter(kmPerLiter)

    fun formatQar(amount: Double): String = com.sportage.obd.fuel.FuelEconomyFormatter.formatQar(amount)
    fun formatQar(amount: Float): String = com.sportage.obd.fuel.FuelEconomyFormatter.formatQar(amount)

    fun costPerKm(totalCostQar: Double, distanceKm: Double): Double? =
        com.sportage.obd.fuel.FuelEconomyFormatter.costPerKm(totalCostQar, distanceKm)

    fun formatCostPerKm(totalCostQar: Double, distanceKm: Double): String =
        com.sportage.obd.fuel.FuelEconomyFormatter.formatCostPerKm(totalCostQar, distanceKm)
}