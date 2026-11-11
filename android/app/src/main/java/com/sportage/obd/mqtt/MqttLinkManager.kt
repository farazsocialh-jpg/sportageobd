package com.sportage.obd.mqtt

import android.content.Context

/**
 * Stores the pairing established with the phone companion app
 * (userId + vehicleId) and derives the scoped MQTT topics.
 */
class MqttLinkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val vehicleId: String? get() = prefs.getString(KEY_VEHICLE_ID, null)

    fun isLinked(): Boolean = !userId.isNullOrBlank() && !vehicleId.isNullOrBlank()

    fun savePairing(userId: String, vehicleId: String) {
        prefs.edit()
            .putString(KEY_USER_ID, userId.trim())
            .putString(KEY_VEHICLE_ID, vehicleId.trim())
            .apply()
    }

    fun clearPairing() {
        prefs.edit().remove(KEY_USER_ID).remove(KEY_VEHICLE_ID).apply()
    }

    fun telemetryTopic(): String =
        if (isLinked()) "users/$userId/vehicles/$vehicleId/telemetry"
        else MqttConfig.TOPIC_TELEMETRY

    fun parkingTopic(): String =
        if (isLinked()) "users/$userId/vehicles/$vehicleId/parking"
        else MqttConfig.TOPIC_PARKED

    companion object {
        private const val PREFS_NAME = "vehicle_link_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_VEHICLE_ID = "vehicle_id"
    }
}