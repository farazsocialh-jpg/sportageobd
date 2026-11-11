package com.sportage.companion.data.model

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ConnectionStatus {
    CONNECTING,
    LIVE_ENGINE_RUNNING,
    STANDBY_IGNITION_ON,
    OFFLINE_STALE
}

data class TelemetryPayload(
    @SerializedName("speed") val speed: Int = 0,
    @SerializedName("rpm") val rpm: Int = 0,
    @SerializedName("coolantTemp") val coolantTemp: Int = 0,
    @SerializedName("fuelLevel") val fuelLevel: Int = 0,
    @SerializedName("isEngineRunning") val isEngineRunning: Boolean = false,
    @SerializedName("timestamp") val timestamp: Long = 0L
)

data class LiveTelemetry(
    val speed: Int = 0,
    val rpm: Int = 0,
    val coolantTemp: Int = 0,
    val fuelLevel: Int = 0,
    val isEngineRunning: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isDriving: Boolean get() = isEngineRunning || speed > 0
}

data class MqttConnectionProfile(
    val brokerHost: String,
    val brokerPort: Int = 8883,
    val mqttUsername: String,
    val mqttPassword: String,
    val vehicleId: String
) {
    fun isValid(): Boolean =
        brokerHost.isNotBlank() && mqttUsername.isNotBlank() && mqttPassword.isNotBlank() && vehicleId.isNotBlank()

    fun telemetryTopic(): String =
        if (brokerHost.contains("adafruit.com", ignoreCase = true)) "${mqttUsername}/feeds/telemetry"
        else "sportage/${vehicleId.ifBlank { "sportage" }}/telemetry"

    fun parkingTopic(): String =
        if (brokerHost.contains("adafruit.com", ignoreCase = true)) "${mqttUsername}/feeds/parking"
        else "sportage/${vehicleId.ifBlank { "sportage" }}/parking"
}

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    AUTH_FAILED
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