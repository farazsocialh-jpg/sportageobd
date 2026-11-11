package com.sportage.companion.data.mqtt

object MqttConfig {
    const val HOST: String = "YOUR_CLUSTER.hivemq.cloud"
    const val PORT: Int = 8883
    const val USERNAME: String = "YOUR_USERNAME"
    const val PASSWORD: String = "YOUR_PASSWORD"

    // Legacy topics used by the existing car head unit app.
    const val TOPIC_TELEMETRY_LEGACY: String = "vehicles/sportage/telemetry"
    const val TOPIC_PARKED_LEGACY: String = "vehicles/sportage/parked_location"
}