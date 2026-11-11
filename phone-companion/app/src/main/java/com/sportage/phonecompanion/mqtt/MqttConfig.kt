package com.sportage.phonecompanion.mqtt

object MqttConfig {
    const val HOST: String = "YOUR_CLUSTER.hivemq.cloud"
    const val PORT: Int = 8883
    const val USERNAME: String = "YOUR_USERNAME"
    const val PASSWORD: String = "YOUR_PASSWORD"

    const val TOPIC_TELEMETRY: String = "vehicles/sportage/telemetry"
    const val TOPIC_PARKED: String = "vehicles/sportage/parked_location"
}