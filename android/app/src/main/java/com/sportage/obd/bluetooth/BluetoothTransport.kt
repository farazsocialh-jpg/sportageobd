package com.sportage.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class BluetoothTransport : ElmTransport {
    // Standard Bluetooth Serial Port Profile (SPP) UUID used by ELM327 adapters
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    /**
     * Connects to an ELM327 Bluetooth adapter with fallbacks for aftermarket head units.
     * Bluetooth permission and device/ROM failures are reported through [connectionState]
     * instead of escaping the service coroutine and crashing the APK.
     */
    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting

        try {
            // Discovery must stop before an RFCOMM connection, but some Android versions
            // throw here when Bluetooth permission was revoked while the dialog was open.
            try {
                BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            } catch (_: SecurityException) {
                // Continue; socket.connect() below will produce the permission error if needed.
            }

            var connectedSocket: BluetoothSocket? = null
            var lastError: Exception? = null

            // Method 1: Standard Secure RFCOMM Socket
            try {
                val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
                candidate.connect()
                connectedSocket = candidate
            } catch (error: Exception) {
                lastError = error
            }

            // Method 2: Insecure RFCOMM Socket (compatibility fallback)
            if (connectedSocket == null) {
                try {
                    val candidate = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    candidate.connect()
                    connectedSocket = candidate
                } catch (error: Exception) {
                    lastError = error
                }
            }

            // Method 3: Hidden RFCOMM channel 1, used by some aftermarket head units
            if (connectedSocket == null) {
                try {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val candidate = method.invoke(device, 1) as BluetoothSocket
                    candidate.connect()
                    connectedSocket = candidate
                } catch (error: Exception) {
                    lastError = error
                }
            }

            val activeSocket = connectedSocket
            if (activeSocket != null && activeSocket.isConnected) {
                socket = activeSocket
                inputStream = activeSocket.inputStream
                outputStream = activeSocket.outputStream

                // Read metadata only after a successful connection and guard against permission
                // revocation between the connection request and completion.
                val deviceName = try { device.name ?: "ELM327 OBD2" } catch (_: SecurityException) { "ELM327 OBD2" }
                val deviceAddress = try { device.address } catch (_: SecurityException) { "Unknown address" }
                _connectionState.value = ConnectionState.Connected(
                    deviceName = deviceName,
                    address = deviceAddress,
                    protocol = "ISO 15765-4 (CAN 11/500)"
                )
                true
            } else {
                disconnect()
                val message = lastError?.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not establish an RFCOMM connection with the adapter"
                _connectionState.value = ConnectionState.Error(message)
                false
            }
        } catch (error: SecurityException) {
            disconnect()
            _connectionState.value = ConnectionState.Error(
                "Bluetooth permission is required. Grant Nearby devices permission and try again."
            )
            false
        } catch (error: Exception) {
            disconnect()
            _connectionState.value = ConnectionState.Error(
                error.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Bluetooth connection failed"
            )
            false
        }
    }

    override suspend fun sendCommand(command: String, timeoutMs: Long): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: throw IllegalStateException("Socket is not connected")
        val inStream = inputStream ?: throw IllegalStateException("Socket is not connected")

        // Clear any old bytes in buffer
        while (inStream.available() > 0) {
            inStream.read()
        }

        // ELM327 commands must terminate with Carriage Return ('\\r')
        val formatted = command.trim() + "\\r"
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
            .replace("\\r", " ")
            .replace("\\n", " ")
            .replace(">", "")
            .trim()
    }

    override fun disconnect() {
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
