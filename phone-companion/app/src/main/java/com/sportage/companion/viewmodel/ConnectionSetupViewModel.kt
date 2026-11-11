package com.sportage.companion.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportage.companion.data.config.MqttConfigRepository
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.MqttConnectionProfile
import com.sportage.companion.data.mqtt.MqttManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionSetupUiState(
    val brokerHost: String = "io.adafruit.com",
    val brokerPort: String = "8883",
    val mqttUsername: String = "",
    val mqttPassword: String = "YOUR_AIO_KEY",
    val vehicleId: String = "",
    val saved: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null,
    val error: String? = null
)

class ConnectionSetupViewModel(
    private val configRepo: MqttConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionSetupUiState())
    val state: StateFlow<ConnectionSetupUiState> = _state.asStateFlow()

    init {
        configRepo.current()?.let { p ->
            _state.value = _state.value.copy(
                brokerHost = p.brokerHost,
                brokerPort = p.brokerPort.toString(),
                mqttUsername = p.mqttUsername,
                mqttPassword = p.mqttPassword,
                vehicleId = p.vehicleId,
                saved = true
            )
        }
    }

    fun onBrokerHostChange(v: String) { _state.value = _state.value.copy(brokerHost = v, saved = false, testResult = null) }
    fun onBrokerPortChange(v: String) { _state.value = _state.value.copy(brokerPort = v, saved = false, testResult = null) }
    fun onUsernameChange(v: String) { _state.value = _state.value.copy(mqttUsername = v, saved = false, testResult = null) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(mqttPassword = v, saved = false, testResult = null) }
    fun onVehicleIdChange(v: String) { _state.value = _state.value.copy(vehicleId = v, saved = false, testResult = null) }

    fun applyScannedPayload(json: String) {
        runCatching {
            val o = org.json.JSONObject(json)
            _state.value = _state.value.copy(
                brokerHost = o.optString("brokerHost", _state.value.brokerHost),
                brokerPort = o.optInt("brokerPort", _state.value.brokerPort.toIntOrNull() ?: 8883).toString(),
                mqttUsername = o.optString("mqttUsername", _state.value.mqttUsername),
                mqttPassword = o.optString("mqttPassword", _state.value.mqttPassword),
                vehicleId = o.optString("vehicleId", _state.value.vehicleId),
                saved = false,
                testResult = null
            )
        }
    }

    fun save(): Boolean {
        val s = _state.value
        val port = s.brokerPort.toIntOrNull() ?: 8883
        val profile = MqttConnectionProfile(s.brokerHost, port, s.mqttUsername, s.mqttPassword, s.vehicleId)
        if (!profile.isValid()) {
            _state.value = s.copy(error = "All fields are required")
            return false
        }
        configRepo.save(profile)
        _state.value = s.copy(saved = true, error = null)
        return true
    }

    private var testJob: Job? = null

    fun testConnection() {
        if (!save()) return
        val profile = configRepo.current() ?: return
        testJob?.cancel()
        _state.value = _state.value.copy(testing = true, testResult = null, error = null)
        testJob = viewModelScope.launch {
            val manager = MqttManager(profile, false, this)
            manager.connect()
            var result: String? = null
            val deadline = System.currentTimeMillis() + 10_000L
            while (System.currentTimeMillis() < deadline) {
                when (manager.connectionState.value) {
                    ConnectionState.CONNECTED -> { result = "Connected successfully"; break }
                    ConnectionState.AUTH_FAILED -> { result = "Authentication failed"; break }
                    else -> {}
                }
                delay(250)
            }
            if (result == null) result = "Connection timed out"
            manager.disconnect()
            _state.value = _state.value.copy(testing = false, testResult = result)
        }
    }

    fun pairingPayload(): String {
        val s = _state.value
        val port = s.brokerPort.toIntOrNull() ?: 8883
        return org.json.JSONObject().apply {
            put("brokerHost", s.brokerHost)
            put("brokerPort", port)
            put("mqttUsername", s.mqttUsername)
            put("mqttPassword", s.mqttPassword)
            put("vehicleId", s.vehicleId)
        }.toString()
    }
}