package com.sportage.phonecompanion.model

data class Telemetry(
    val speedKmh: Int = 0,
    val rpm: Int = 0,
    val coolantTempC: Int = 0,
    val intakeTempC: Int = 0,
    val fuelLevelPct: Int = 0,
    val throttlePct: Float = 0f,
    val engineLoadPct: Float = 0f,
    val batteryVoltage: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float
)