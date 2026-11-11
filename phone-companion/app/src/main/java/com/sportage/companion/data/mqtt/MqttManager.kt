package com.sportage.companion.data.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.model.MqttConnectionProfile
import com.sportage.companion.data.model.ParkedLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MqttManager(
    private val profile: MqttConnectionProfile,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost(profile.brokerHost)
        .serverPort(profile.brokerPort)
        .sslWithDefaultConfig()
        .buildAsync()

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    @Volatile
    private var running = false
    private var reconnectAttempt = 0

    fun connect() {
        running = true
        _connectionState.value = ConnectionState.CONNECTING
        doConnect()
    }

    private fun doConnect() {
        if (!running) return
        scope.launch {
            try {
                client.connectWith()
                    .simpleAuth()
                    .username(profile.mqttUsername)
                    .password(profile.mqttPassword.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Log.w(TAG, "MQTT connect failed: ${throwable.message}")
                            _connectionState.value = if (isAuthError(throwable)) ConnectionState.AUTH_FAILED else ConnectionState.DISCONNECTED
                            scheduleReconnect()
                        } else {
                            reconnectAttempt = 0
                            _connectionState.value = ConnectionState.CONNECTED
                            subscribe()
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "MQTT connect error: ${t.message}")
                _connectionState.value = if (isAuthError(t)) ConnectionState.AUTH_FAILED else ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private fun subscribe() {
        client.subscribeWith()
            .topicFilter(profile.telemetryTopic())
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                runCatching {
                    val o = JSONObject(payload)
                    val t = LiveTelemetry(
                        speed = o.optInt("speed", 0),
                        rpm = o.optInt("rpm", 0),
                        coolantTemp = o.optInt("coolantTemp", 0),
                        fuelLevel = o.optInt("fuelLevel", 0),
                        isEngineRunning = o.optBoolean("isEngineRunning", false),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                    _telemetry.value = t
                    _isDriving.value = t.isDriving
                }.onFailure { Log.w(TAG, "Bad telemetry payload: ${it.message}") }
            }
            .send()

        client.subscribeWith()
            .topicFilter(profile.parkingTopic())
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                runCatching {
                    val o = JSONObject(payload)
                    _parkedLocation.value = ParkedLocation(
                        latitude = o.getDouble("latitude"),
                        longitude = o.getDouble("longitude"),
                        timestamp = o.getLong("timestamp"),
                        accuracy = o.optDouble("accuracy", 0.0).toFloat()
                    )
                    _isDriving.value = false
                }.onFailure { Log.w(TAG, "Bad parked payload: ${it.message}") }
            }
            .send()
    }

    private fun scheduleReconnect() {
        if (!running) return
        scope.launch {
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            val delayMs = (1_000L shl reconnectAttempt).coerceAtMost(30_000L)
            delay(delayMs)
            if (running && _connectionState.value != ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTING
                doConnect()
            }
        }
    }

    fun disconnect() {
        running = false
        try { client.disconnect() } catch (_: Throwable) {}
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun isAuthError(t: Throwable): Boolean {
        val msg = t.message?.lowercase().orEmpty()
        return msg.contains("not authorized") || msg.contains("auth") || msg.contains("credentials")
    }

    companion object {
        private const val TAG = "MqttManager"
    }
}