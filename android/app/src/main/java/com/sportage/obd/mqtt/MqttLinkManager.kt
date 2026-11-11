package com.sportage.obd.mqtt

import android.content.Context

class MqttLinkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val adafruitUsername: String? get() = prefs.getString(KEY_USERNAME, null)
    val adafruitKey: String? get() = prefs.getString(KEY_KEY, null)
    val vehicleId: String? get() = prefs.getString(KEY_VEHICLE_ID, null)

    fun isLinked(): Boolean = !adafruitUsername.isNullOrBlank()

    fun savePairing(username: String, vehicleId: String, aioKey: String? = null) {
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_VEHICLE_ID, vehicleId.trim())
            .apply { if (!aioKey.isNullOrBlank()) putString(KEY_KEY, aioKey.trim()) }
            .apply()
    }

    fun clearPairing() {
        prefs.edit().remove(KEY_USERNAME).remove(KEY_VEHICLE_ID).remove(KEY_KEY).apply()
    }

    fun telemetryTopic(): String = "${adafruitUsername ?: MqttConfig.USERNAME}/feeds/telemetry"
    fun parkingTopic(): String = "${adafruitUsername ?: MqttConfig.USERNAME}/feeds/parking"

    companion object {
        private const val PREFS_NAME = "vehicle_link_prefs"
        private const val KEY_USERNAME = "adafruit_username"
        private const val KEY_KEY = "adafruit_key"
        private const val KEY_VEHICLE_ID = "vehicle_id"
    }
}