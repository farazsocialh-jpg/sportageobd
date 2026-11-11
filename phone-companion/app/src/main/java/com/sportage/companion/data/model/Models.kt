package com.sportage.companion.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionStatus {
    CONNECTING,
    LIVE_ENGINE_RUNNING,
    STANDBY_IGNITION_ON,
    OFFLINE_STALE
}

data class LiveTelemetry(
    val speedKmh: Int = 0,
    val rpm: Int = 0,
    val coolantTempC: Int = 0,
    val fuelLevelPct: Int = 0,
    val batteryVoltage: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isEngineRunning: Boolean get() = rpm > 0 || speedKmh > 0
}

data class ParkedLocation(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val accuracy: Float
) {
    fun formattedTime(): String =
        SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class VehicleProfile(
    val userId: String,
    val vehicleId: String,
    val displayName: String = "Sportage"
) {
    fun telemetryTopic(): String =
        "users/$userId/vehicles/$vehicleId/telemetry"
    fun parkingTopic(): String =
        "users/$userId/vehicles/$vehicleId/parking"
}

data class UserSession(
    val uid: String,
    val email: String?
)