package com.sportage.obd.bluetooth

import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for any ELM327 link: classic Bluetooth SPP, Bluetooth LE
 * GATT or (later) WiFi/TCP. ObdService talks to this interface only.
 */
interface ElmTransport {
    val connectionState: StateFlow<ConnectionState>

    /** Establishes the link. Returns true when connected. */
    suspend fun connect(device: android.bluetooth.BluetoothDevice): Boolean

    /** Sends one ELM327 command and returns the response (prompt stripped). */
    suspend fun sendCommand(command: String, timeoutMs: Long = 2500): String

    fun disconnect()
}
