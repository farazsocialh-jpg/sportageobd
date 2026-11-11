package com.sportage.obd.fuel

/**
 * Primary fuel-economy engine expressed in km/L (kilometers per liter).
 *
 * Idle transition: at 0 (or very low) speed the display switches to fuel flow in L/h,
 * and returns to km/L as soon as speed exceeds 3 km/h.
 */
object FuelEconomyFormatter {

    /** Speed threshold (km/h) below which fuel flow is shown instead of economy. */
    const val IDLE_SPEED_THRESHOLD_KMH = 3.0

    /** km/L = speedKmh / fuelFlowLitersPerHour. Null when the vehicle is idle or flow is zero. */
    fun kmPerLiter(speedKmh: Double, fuelFlowLitersPerHour: Double): Double? =
        if (speedKmh > IDLE_SPEED_THRESHOLD_KMH && fuelFlowLitersPerHour > 0.0) speedKmh / fuelFlowLitersPerHour else null

    fun kmPerLiter(speedKmh: Float, fuelFlowLitersPerHour: Float): Double? =
        kmPerLiter(speedKmh.toDouble(), fuelFlowLitersPerHour.toDouble())

    fun kmPerLiter(speedKmh: Int, fuelFlowLitersPerHour: Double): Double? =
        kmPerLiter(speedKmh.toDouble(), fuelFlowLitersPerHour)

    /** Fuel flow in L/h, shown while idling. */
    fun litersPerHour(fuelFlowLitersPerHour: Double): Double = fuelFlowLitersPerHour

    /**
     * Format a live reading: "0.8 L/hr" while idle (speed <= 3 km/h), otherwise "12.4 km/L".
     */
    fun format(speedKmh: Double, fuelFlowLitersPerHour: Double): String {
        return if (speedKmh > IDLE_SPEED_THRESHOLD_KMH) {
            val kmL = speedKmh / fuelFlowLitersPerHour
            "%.1f km/L".format(java.util.Locale.US, kmL)
        } else {
            "%.1f L/hr".format(java.util.Locale.US, fuelFlowLitersPerHour)
        }
    }

    fun format(speedKmh: Float, fuelFlowLitersPerHour: Float): String =
        format(speedKmh.toDouble(), fuelFlowLitersPerHour.toDouble())

    fun format(speedKmh: Int, fuelFlowLitersPerHour: Double): String =
        format(speedKmh.toDouble(), fuelFlowLitersPerHour)

    fun format(speedKmh: Int, fuelFlowLitersPerHour: Float): String =
        format(speedKmh.toDouble(), fuelFlowLitersPerHour.toDouble())

    /** Format a trip average expressed in km/L. */
    fun formatAverageKmPerLiter(kmPerLiter: Double?): String =
        kmPerLiter?.let { "%.1f km/L".format(java.util.Locale.US, it) } ?: "-- km/L"

    /** Format a cost in QAR (Qatari Riyal). */
    fun formatQar(amount: Double): String = "%.2f QAR".format(java.util.Locale.US, amount)

    fun formatQar(amount: Float): String = formatQar(amount.toDouble())

    /** Cost per kilometer, QAR/km. */
    fun costPerKm(totalCostQar: Double, distanceKm: Double): Double? =
        if (distanceKm > 0.0) totalCostQar / distanceKm else null

    /** Format cost per km. */
    fun formatCostPerKm(totalCostQar: Double, distanceKm: Double): String =
        costPerKm(totalCostQar, distanceKm)?.let { "%.3f QAR/km".format(java.util.Locale.US, it) } ?: "-- QAR/km"
}