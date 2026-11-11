package com.sportage.obd.mqtt

import android.content.Context

class MqttLinkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val brokerHost: String get() = prefs.getString(KEY_HOST, null) ?: MqttConfig.HOST
    val brokerPort: Int get() = prefs.getInt(KEY_PORT, MqttConfig.PORT)
    val adafruitUsername: String? get() = prefs.getString(KEY_USERNAME, null)
    val adafruitKey: String? get() = prefs.getString(KEY_KEY, null)
    val vehicleId: String? get() = prefs.getString(KEY_VEHICLE_ID, null)

    fun isLinked(): Boolean = !adafruitUsername.isNullOrBlank()

    fun saveSettings(
        host: String,
        port: Int,
        username: String,
        aioKey: String,
        vehicleId: String
    ) {
        prefs.edit()
            .putString(KEY_HOST, host.trim())
            .putInt(KEY_PORT, port)
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_KEY, aioKey.trim())
            .putString(KEY_VEHICLE_ID, vehicleId.trim())
            .apply()
    }

    fun clearPairing() {
        prefs.edit().remove(KEY_USERNAME).remove(KEY_VEHICLE_ID).remove(KEY_KEY).apply()
    }

    fun telemetryTopic(): String {
        val user = adafruitUsername ?: MqttConfig.USERNAME
        return if (brokerHost.contains("adafruit.com", ignoreCase = true)) {
            "$user/feeds/telemetry"
        } else {
            "sportage/${vehicleId ?: "sportage"}/telemetry"
        }
    }

    fun parkingTopic(): String {
        val user = adafruitUsername ?: MqttConfig.USERNAME
        return if (brokerHost.contains("adafruit.com", ignoreCase = true)) {
            "$user/feeds/parking"
        } else {
            "sportage/${vehicleId ?: "sportage"}/parking"
        }
    }

    companion object {
        private const val PREFS_NAME = "vehicle_link_prefs"
        private const val KEY_HOST = "broker_host"
        private const val KEY_PORT = "broker_port"
        private const val KEY_USERNAME = "adafruit_username"
        private const val KEY_KEY = "adafruit_key"
        private const val KEY_VEHICLE_ID = "vehicle_id"
    }
}