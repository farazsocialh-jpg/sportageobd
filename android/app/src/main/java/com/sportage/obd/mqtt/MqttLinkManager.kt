package com.sportage.obd.mqtt

import android.content.Context

class MqttLinkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val vehicleId: String? get() = prefs.getString(KEY_VEHICLE_ID, null)

    fun isLinked(): Boolean = !vehicleId.isNullOrBlank()

    fun savePairing(vehicleId: String) {
        prefs.edit().putString(KEY_VEHICLE_ID, vehicleId.trim()).apply()
    }

    fun clearPairing() {
        prefs.edit().remove(KEY_VEHICLE_ID).apply()
    }

    fun telemetryTopic(): String = "sportage/${vehicleId ?: "sportage"}/telemetry"
    fun parkingTopic(): String = "sportage/${vehicleId ?: "sportage"}/parking"

    companion object {
        private const val PREFS_NAME = "vehicle_link_prefs"
        private const val KEY_VEHICLE_ID = "vehicle_id"
    }
}