package com.sportage.phonecompanion.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.sportage.phonecompanion.model.ParkedLocation
import com.sportage.phonecompanion.model.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class PhoneMqttSubscriber(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost(MqttConfig.HOST)
        .serverPort(MqttConfig.PORT)
        .sslWithDefaultConfig()
        .buildAsync()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private val _parkedLocation = MutableStateFlow<ParkedLocation?>(null)
    val parkedLocation: StateFlow<ParkedLocation?> = _parkedLocation.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _isDriving = MutableStateFlow(false)
    val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

    fun connect() {
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
                            _connected.value = false
                        } else {
                            _connected.value = true
                            subscribe()
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "MQTT connect error: ${t.message}")
                _connected.value = false
            }
        }
    }

    private fun subscribe() {
        client.subscribeWith()
            .topicFilter(MqttConfig.TOPIC_TELEMETRY)
            .qos(MqttQos.AT_MOST_ONCE)
            .callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                runCatching {
                    val o = JSONObject(payload)
                    _telemetry.value = Telemetry(
                        speedKmh = o.optInt("speedKmh", 0),
                        rpm = o.optInt("rpm", 0),
                        coolantTempC = o.optInt("coolantTempC", 0),
                        intakeTempC = o.optInt("intakeTempC", 0),
                        fuelLevelPct = o.optInt("fuelLevelPct", 0),
                        throttlePct = o.optDouble("throttlePct", 0.0).toFloat(),
                        engineLoadPct = o.optDouble("engineLoadPct", 0.0).toFloat(),
                        batteryVoltage = o.optDouble("batteryVoltage", 0.0).toFloat(),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                    _isDriving.value = (o.optInt("speedKmh", 0) > 0) || (o.optInt("rpm", 0) > 0)
                }.onFailure { Log.w(TAG, "Bad telemetry payload: ${it.message}") }
            }
            .send()

        client.subscribeWith()
            .topicFilter(MqttConfig.TOPIC_PARKED)
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

    fun disconnect() {
        try {
            client.disconnect()
        } catch (_: Throwable) {
        }
        _connected.value = false
    }

    companion object {
        private const val TAG = "PhoneMqttSubscriber"
    }
}