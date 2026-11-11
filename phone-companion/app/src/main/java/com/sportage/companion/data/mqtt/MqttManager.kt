package com.sportage.companion.data.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.sportage.companion.data.model.ConnectionStatus
import com.sportage.companion.data.model.LiveTelemetry
import com.sportage.companion.data.model.ParkedLocation
import com.sportage.companion.data.model.VehicleProfile
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
    private val vehicle: VehicleProfile,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost(MqttConfig.HOST)
        .serverPort(MqttConfig.PORT)
        .sslWithDefaultConfig()
        .buildAsync()

    private val _telemetry = MutableStateFlow(LiveTelemetry())
    val telemetry: StateFlow<LiveTelemetry> = _telemetry.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    @Volatile
    private var running = false

    fun connect() {
        running = true
        _connectionStatus.value = ConnectionStatus.CONNECTING
        doConnect()
        startStaleWatcher()
    }

    private fun doConnect() {
        if (!running) return
        scope.launch {
            try {
                client.connectWith()
                    .simpleAuth()
                    .username(MqttConfig.USERNAME)
                    .password(MqttConfig.PASSWORD.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Log.w(TAG, "MQTT connect failed: ${throwable.message}")
                            _connectionStatus.value = ConnectionStatus.OFFLINE_STALE
                            scheduleReconnect()
                        } else {
                            _connectionStatus.value = ConnectionStatus.CONNECTING
                            subscribe()
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "MQTT connect error: ${t.message}")
                _connectionStatus.value = ConnectionStatus.OFFLINE_STALE
                scheduleReconnect()
            }
        }
    }

    private fun subscribe() {
        val telemetryTopics = linkedSetOf(vehicle.telemetryTopic(), MqttConfig.TOPIC_TELEMETRY_LEGACY)
        telemetryTopics.forEach { topic ->
            client.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_MOST_ONCE)
                .callback { publish ->
                    val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                    runCatching {
                        val o = JSONObject(payload)
                        val t = LiveTelemetry(
                            speedKmh = o.optInt("speedKmh", 0),
                            rpm = o.optInt("rpm", 0),
                            coolantTempC = o.optInt("coolantTempC", 0),
                            fuelLevelPct = o.optInt("fuelLevelPct", 0),
                            batteryVoltage = o.optDouble("batteryVoltage", 0.0).toFloat(),
                            timestamp = o.optLong("timestamp", System.currentTimeMillis())
                        )
                        _telemetry.value = t
                        _isDriving.value = t.isEngineRunning
                        _connectionStatus.value = if (t.isEngineRunning)
                            ConnectionStatus.LIVE_ENGINE_RUNNING
                        else
                            ConnectionStatus.STANDBY_IGNITION_ON
                    }.onFailure { Log.w(TAG, "Bad telemetry payload: ${it.message}") }
                }
                .send()
        }

        // Retained parked location: delivered immediately on subscribe even if car off for days.
        val parkingTopics = linkedSetOf(vehicle.parkingTopic(), MqttConfig.TOPIC_PARKED_LEGACY)
        parkingTopics.forEach { topic ->
            client.subscribeWith()
                .topicFilter(topic)
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
    }

    private fun scheduleReconnect() {
        if (!running) return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (running) doConnect()
        }
    }

    private fun startStaleWatcher() {
        scope.launch {
            while (running) {
                delay(STALE_CHECK_MS)
                val last = _telemetry.value.timestamp
                if (System.currentTimeMillis() - last > STALE_THRESHOLD_MS &&
                    _connectionStatus.value != ConnectionStatus.CONNECTING
                ) {
                    _connectionStatus.value = ConnectionStatus.OFFLINE_STALE
                }
            }
        }
    }

    fun disconnect() {
        running = false
        try {
            client.disconnect()
        } catch (_: Throwable) {
        }
        _connectionStatus.value = ConnectionStatus.OFFLINE_STALE
    }

    companion object {
        private const val TAG = "MqttManager"
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val STALE_CHECK_MS = 5_000L
        private const val STALE_THRESHOLD_MS = 15_000L
    }
}