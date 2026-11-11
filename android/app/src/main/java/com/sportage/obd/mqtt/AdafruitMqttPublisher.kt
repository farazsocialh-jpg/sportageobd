package com.sportage.obd.mqtt

import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class AdafruitMqttPublisher(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val linkManager = MqttLinkManager(context)
    private val username: String get() = linkManager.adafruitUsername ?: MqttConfig.USERNAME
    private val password: String get() = linkManager.adafruitKey ?: MqttConfig.PASSWORD

    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost(MqttConfig.HOST)
        .serverPort(MqttConfig.PORT)
        .sslWithDefaultConfig()
        .buildAsync()

    @Volatile
    private var connected: Boolean = false

    fun connect() {
        if (connected || username.isBlank()) return
        scope.launch {
            try {
                client.connectWith()
                    .simpleAuth()
                    .username(username)
                    .password(password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
                    .send()
                    .whenComplete { _, throwable ->
                        if (throwable != null) {
                            Log.w(TAG, "Adafruit MQTT connect failed: ${throwable.message}")
                            connected = false
                        } else {
                            connected = true
                            Log.i(TAG, "Adafruit MQTT connected as $username")
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "Adafruit MQTT connect error: ${t.message}")
                connected = false
            }
        }
    }

    fun publishTelemetry(
        speedKmh: Int?,
        rpm: Int?,
        coolantTempC: Int?,
        fuelLevelPct: Int?
    ) {
        val speed = speedKmh ?: 0
        val rpmValue = rpm ?: 0
        val json = JSONObject().apply {
            put("speed", speed)
            put("rpm", rpmValue)
            put("coolantTemp", coolantTempC ?: 0)
            put("isEngineRunning", rpmValue > 0 || speed > 0)
            put("timestamp", System.currentTimeMillis())
        }
        publish(linkManager.telemetryTopic(), json.toString(), MqttQos.AT_MOST_ONCE, retain = false)
    }

    fun publishParkedLocation(
        latitude: Double,
        longitude: Double,
        timestamp: Long,
        accuracy: Float
    ) {
        val json = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy.toDouble())
            put("timestamp", timestamp)
        }
        publish(linkManager.parkingTopic(), json.toString(), MqttQos.AT_LEAST_ONCE, retain = true)
    }

    private fun publish(topic: String, payload: String, qos: MqttQos, retain: Boolean) {
        connect()
        scope.launch {
            try {
                client.publishWith()
                    .topic(topic)
                    .payload(payload.toByteArray(StandardCharsets.UTF_8))
                    .qos(qos)
                    .retain(retain)
                    .send()
            } catch (t: Throwable) {
                Log.w(TAG, "Adafruit MQTT publish failed on $topic: ${t.message}")
            }
        }
    }

    fun disconnect() {
        try { client.disconnect() } catch (_: Throwable) {}
        connected = false
    }

    companion object {
        private const val TAG = "AdafruitMqttPublisher"
    }
}

object MqttConfig {
    const val HOST: String = "io.adafruit.com"
    const val PORT: Int = 8883
    const val USERNAME: String = "YOUR_ADAFRUIT_USERNAME"
    const val PASSWORD: String = "YOUR_AIO_KEY"
}