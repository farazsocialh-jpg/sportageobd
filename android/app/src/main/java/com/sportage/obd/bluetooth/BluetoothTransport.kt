package com.sportage.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

sealed interface ConnectionState {
    object Disconnected : ConnectionState
    object Connecting : ConnectionState
    data class Connected(val deviceName: String, val address: String, val protocol: String = "ISO 15765-4 CAN") : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class BluetoothTransport {
    // Standard Bluetooth Serial Port Profile (SPP) UUID used by ELM327 adapters
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val commandMutex = Mutex()

    /**
     * Connects to ELM327 Bluetooth adapter with robust fallbacks designed
     * specifically for Chinese Android aftermarket head units (TS10, UIS7862, MTK, PX5/PX6).
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

        var connectedSocket: BluetoothSocket? = null
        var lastError: Exception? = null

        // Method 1: Standard Secure RFCOMM Socket
        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            connectedSocket = sock
        } catch (e1: Exception) {
            lastError = e1
        }

        // Method 2: Insecure RFCOMM Socket (Bypasses authentication issues common on head units)
        if (connectedSocket == null) {
            try {
                val sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                connectedSocket = sock
            } catch (e2: Exception) {
                lastError = e2
            }
        }

        // Method 3: Reflection on Hidden RFCOMM Channel 1 (Standard for Chinese Head Unit ROMs)
        if (connectedSocket == null) {
            try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val sock = m.invoke(device, 1) as BluetoothSocket
                sock.connect()
                connectedSocket = sock
            } catch (e3: Exception) {
                lastError = e3
            }
        }

        if (connectedSocket != null && connectedSocket.isConnected) {
            socket = connectedSocket
            inputStream = connectedSocket.inputStream
            outputStream = connectedSocket.outputStream

            _connectionState.value = ConnectionState.Connected(
                deviceName = device.name ?: "ELM327 OBD2",
                address = device.address,
                protocol = "ISO 15765-4 (CAN 11/500)"
            )
            true
        } else {
            disconnect()
            val msg = lastError?.message ?: "Could not establish RFCOMM serial connection with ELM327"
            _connectionState.value = ConnectionState.Error(msg)
            false
        }
    }

    suspend fun sendCommand(command: String, timeoutMs: Long = 2500): String = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val out = outputStream ?: throw IllegalStateException("Socket is not connected")
            val inStream = inputStream ?: throw IllegalStateException("Socket is not connected")

            // Clear any old bytes in buffer
            while (inStream.available() > 0) {
                inStream.read()
            }

            // ELM327 commands must terminate with Carriage Return ('\r')
            val formatted = command.trim() + "\r"
            out.write(formatted.toByteArray(Charsets.US_ASCII))
            out.flush()

            // Read until prompt '>' is received
            val buffer = StringBuilder()
            val startTime = System.currentTimeMillis()
            val byteBuffer = ByteArray(256)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (inStream.available() > 0) {
                    val readCount = inStream.read(byteBuffer)
                    if (readCount > 0) {
                        val chunk = String(byteBuffer, 0, readCount, Charsets.US_ASCII)
                        buffer.append(chunk)
                        if (buffer.contains(">")) {
                            break
                        }
                    }
                } else {
                    Thread.sleep(8)
                }
            }

            buffer.toString()
                .replace("\r", " ")
                .replace("\n", " ")
                .replace(">", "")
                .trim()
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        inputStream = null
        outputStream = null
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
