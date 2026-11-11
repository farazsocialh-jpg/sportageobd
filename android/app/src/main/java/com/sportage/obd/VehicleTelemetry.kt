package com.sportage.obd

/**
 * Decoupled, UI-facing snapshot of vehicle telemetry.
 *
 * Emitted by the OBD manager/repository on a background thread and collected by the
 * dashboard ViewModels. Keeping this model free of Android/UI types means themes can
 * be swapped without touching the OBD connection.
 */
data class VehicleTelemetry(
    val speedKmh: Int = 0,
    val rpm: Int = 0,
    val coolantTempC: Int = 0,
    val fuelLevelPercent: Float = 0f,
    val batteryVoltage: Float = 0f,
    val currentGear: String = "P",
    val isConnected: Boolean = false
)