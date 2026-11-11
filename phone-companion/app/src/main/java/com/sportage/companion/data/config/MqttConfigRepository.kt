package com.sportage.companion.data.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sportage.companion.data.model.MqttConnectionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MqttConfigRepository(context: Context) {

    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<MqttConnectionProfile?> = _profile.asStateFlow()

    fun current(): MqttConnectionProfile? = _profile.value

    fun save(profile: MqttConnectionProfile) {
        if (!profile.isValid()) return
        prefs.edit()
            .putString(KEY_HOST, profile.brokerHost.trim())
            .putInt(KEY_PORT, profile.brokerPort)
            .putString(KEY_USERNAME, profile.mqttUsername.trim())
            .putString(KEY_PASSWORD, profile.mqttPassword)
            .putString(KEY_VEHICLE_ID, profile.vehicleId.trim())
            .apply()
        _profile.value = profile
    }

    fun clear() {
        prefs.edit().clear().apply()
        _profile.value = null
    }

    fun validate(profile: MqttConnectionProfile): Boolean = profile.isValid()

    private fun loadProfile(): MqttConnectionProfile? {
        val host = prefs.getString(KEY_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(KEY_PORT, 8883)
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        val vehicleId = prefs.getString(KEY_VEHICLE_ID, null)?.trim().orEmpty()
        if (host.isBlank() || username.isBlank() || password.isBlank() || vehicleId.isBlank()) return null
        return MqttConnectionProfile(host, port, username, password, vehicleId)
    }

    companion object {
        private const val PREFS_FILE = "mqtt_secure_prefs"
        private const val KEY_HOST = "broker_host"
        private const val KEY_PORT = "broker_port"
        private const val KEY_USERNAME = "mqtt_username"
        private const val KEY_PASSWORD = "mqtt_password"
        private const val KEY_VEHICLE_ID = "vehicle_id"
    }
}