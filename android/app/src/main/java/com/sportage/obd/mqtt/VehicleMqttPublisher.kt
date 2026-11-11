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

/**
 * Publishes live telemetry and the retained parked-location pin to HiveMQ Cloud.
 *
 * Configure [MqttConfig] (host / username / password) before use. The client connects
 * lazily on first publish and reconnects on failure.
 */
class VehicleMqttPublisher(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val linkManager = MqttLinkManager(context)
    private val client: Mqtt3AsyncClient = MqttClient.builder()
        .useMqttVersion3()
        .serverHost(MqttConfig.HOST)
        .serverPort(MqttConfig.PORT)
        .sslWithDefaultConfig()
        .buildAsync()

    @Volatile
    private var connected: Boolean = false

    fun connect() {
        if (connected) return
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
                            connected = false
                        } else {
                            connected = true
                            Log.i(TAG, "MQTT connected to ${MqttConfig.HOST}")
                        }
                    }
            } catch (t: Throwable) {
                Log.w(TAG, "MQTT connect error: ${t.message}")
                connected = false
            }
        }
    }

    fun publishTelemetry(
        speedKmh: Int?,
        rpm: Int?,
        coolantTempC: Int?,
        intakeTempC: Int?,
        fuelLevelPct: Int?,
        throttlePct: Float?,
        engineLoadPct: Float?,
        batteryVoltage: Float?
    ) {
        val speed = speedKmh ?: 0
        val rpmValue = rpm ?: 0
        val json = JSONObject().apply {
            put("speed", speed)
            put("rpm", rpmValue)
            put("coolantTemp", coolantTempC ?: 0)
            put("fuelLevel", fuelLevelPct ?: 0)
            put("isEngineRunning", rpmValue > 0 || speed > 0)
            put("timestamp", System.currentTimeMillis())
        }
        publish(linkManager.telemetryTopic(), json.toString(), qos = MqttQos.AT_MOST_ONCE, retain = false)
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
        // Retained + QoS 1 so a phone connecting hours later still receives the last pin.
        publish(linkManager.parkingTopic(), json.toString(), qos = MqttQos.AT_LEAST_ONCE, retain = true)
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
                Log.w(TAG, "MQTT publish failed on $topic: ${t.message}")
            }
        }
    }

    fun disconnect() {
        try {
            client.disconnect()
        } catch (_: Throwable) {
        }
        connected = false
    }

    companion object {
        private const val TAG = "VehicleMqttPublisher"
    }
}

object MqttConfig {
    // HiveMQ Cloud (free tier) endpoint. Fill these in before shipping.
    const val HOST: String = "YOUR_CLUSTER.hivemq.cloud"
    const val PORT: Int = 8883
    const val USERNAME: String = "YOUR_USERNAME"
    const val PASSWORD: String = "YOUR_PASSWORD"

    const val TOPIC_TELEMETRY: String = "sportage/sportage/telemetry"
    const val TOPIC_PARKED: String = "sportage/sportage/parking"
}