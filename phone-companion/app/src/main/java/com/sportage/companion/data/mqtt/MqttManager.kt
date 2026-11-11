package com.sportage.companion.data.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.google.gson.Gson
import com.sportage.companion.data.model.ConnectionState
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.model.MqttConnectionProfile
import com.sportage.companion.data.model.ParkedLocation
import com.sportage.companion.data.model.TelemetryPayload
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
    private val autoSubscribe: Boolean = true,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val clientId = "phone-" + java.util.UUID.randomUUID().toString().take(8)
    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .identifier(clientId)
        .serverHost(profile.brokerHost)
        .serverPort(profile.brokerPort)
        .sslWithDefaultConfig()
        .buildAsync()

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    private val gson = Gson()
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _lastTelemetryAt = MutableStateFlow(0L)
    val lastTelemetryAt: StateFlow<Long> = _lastTelemetryAt.asStateFlow()

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
                            if (autoSubscribe) subscribe()
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
        val telemetryTopic = if (profile.brokerHost.contains("io.adafruit.com", ignoreCase = true)) {
            "${profile.mqttUsername}/feeds/telemetry"
        } else {
            profile.telemetryTopic()
        }
        val parkingTopic = if (profile.brokerHost.contains("io.adafruit.com", ignoreCase = true)) {
            "${profile.mqttUsername}/feeds/parking"
        } else {
            profile.parkingTopic()
        }
        Log.d("MQTT_STREAM", "Subscribing to: $telemetryTopic")
        client.subscribeWith()
            .topicFilter(telemetryTopic)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { message ->
                Log.d("MQTT_RAW", "Received message: " + String(message.payloadAsBytes))
                runCatching {
                    val parsed = gson.fromJson(String(message.payloadAsBytes, StandardCharsets.UTF_8), TelemetryPayload::class.java)
                    val t = LiveTelemetry(
                        speed = parsed.speed,
                        rpm = parsed.rpm,
                        coolantTemp = parsed.coolantTemp,
                        fuelLevel = parsed.fuelLevel,
                        isEngineRunning = parsed.isEngineRunning,
                        timestamp = if (parsed.timestamp > 0) parsed.timestamp else System.currentTimeMillis()
                    )
                    scope.launch(Dispatchers.Main) {
                        _telemetry.value = t
                        _lastTelemetryAt.value = if (t.timestamp > 0) t.timestamp else System.currentTimeMillis()
                        _isDriving.value = t.isDriving
                    }
                }.onFailure { Log.w(TAG, "Bad telemetry payload: ${it.message}") }
            }
            .send()

        client.subscribeWith()
            .topicFilter(parkingTopic)
            .qos(MqttQos.AT_LEAST_ONCE)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                runCatching {
                    val o = org.json.JSONObject(payload)
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