package com.sportage.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.resume

/**
 * ELM327 link over Bluetooth Low Energy (BLE GATT).
 *
 * Works with the common vgate/Chinese BLE adapters that expose a Nordic
 * UART-style service or the FFF0/FFE0 transparent bridges:
 *  - 6E400001-... Nordic UART Service (write 6E400002, notify 6E400003)
 *  - FFF0 service (write FFF2, notify FFF1)
 *  - FFE0 service (FFE1 is commonly both writable and notifiable)
 */
class BleTransport(private val context: Context) : ElmTransport {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val responseBuffer = StringBuilder()
    private var responseAwaiter: ((String) -> Unit)? = null

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            // GATT callbacks arrive on a binder thread: an escaping SecurityException here kills
            // the process, so every branch is guarded.
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _connectionState.value = ConnectionState.Error("BLE link lost (status $status)")
                    } else {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    val awaiter = responseAwaiter
                    responseAwaiter = null
                    awaiter?.invoke("")
                }
            } catch (_: SecurityException) {
                _connectionState.value = ConnectionState.Error(
                    "Bluetooth permission was revoked. Grant Nearby devices and reconnect."
                )
            } catch (_: Exception) {
                _connectionState.value = ConnectionState.Error("BLE link error")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    _connectionState.value = ConnectionState.Error("BLE service discovery failed")
                    return
                }
                if (configureCharacteristics(g)) {
                    // Reading name/address needs BLUETOOTH_CONNECT; use the guarded accessors so a
                    // revoked permission cannot crash inside this callback.
                    _connectionState.value = ConnectionState.Connected(
                        deviceName = BluetoothDeviceInfo.displayName(context, g.device, "ELM327 BLE"),
                        address = BluetoothDeviceInfo.address(context, g.device) ?: "unknown",
                        protocol = "ISO 15765-4 (CAN 11/500) via BLE"
                    )
                } else {
                    _connectionState.value = ConnectionState.Error("Adapter has no known ELM327 BLE service")
                }
            } catch (_: SecurityException) {
                _connectionState.value = ConnectionState.Error(
                    "Bluetooth permission was revoked. Grant Nearby devices and reconnect."
                )
            } catch (_: Exception) {
                _connectionState.value = ConnectionState.Error("BLE service discovery failed")
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val chunk = String(value, Charsets.US_ASCII)
            synchronized(responseBuffer) {
                responseBuffer.append(chunk)
                if (responseBuffer.contains(">")) {
                    val full = responseBuffer.toString()
                    responseBuffer.setLength(0)
                    val awaiter = responseAwaiter
                    responseAwaiter = null
                    awaiter?.invoke(full)
                }
            }
        }
    }

    /**
     * Finds the ELM327 write/notify pair among the adapter's services.
     */
    private fun configureCharacteristics(g: BluetoothGatt): Boolean {
        for (service in g.services) {
            var write: BluetoothGattCharacteristic? = null
            var notify: BluetoothGattCharacteristic? = null

            for (ch in service.characteristics) {
                val chUuid = ch.uuid.toString().uppercase()
                val props = ch.properties
                when {
                    chUuid.startsWith("6E400002") || chUuid.startsWith("FFF2") -> write = ch
                    chUuid.startsWith("6E400003") || chUuid.startsWith("FFF1") -> notify = ch
                    chUuid.startsWith("FFE1") -> {
                        if (props and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) write = ch
                        if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) notify = ch
                    }
                    chUuid.startsWith("49535343") && props and BluetoothGattCharacteristic.PROPERTY_WRITE > 0 -> write = ch
                }
            }

            if (write != null && notify != null) {
                writeChar = write
                notifyChar = notify
                enableNotifications(g, notify)
                return true
            }
            // Some FFE0 adapters use a single characteristic for both directions
            if (write != null && notify == null) {
                writeChar = write
                notifyChar = write
                enableNotifications(g, write)
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val ccc = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val descriptor: BluetoothGattDescriptor? = characteristic.getDescriptor(ccc)
        if (descriptor != null) {
            g.setCharacteristicNotification(characteristic, true)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Connecting(address = device.address)

        // connectGatt throws SecurityException when BLUETOOTH_CONNECT is missing and
        // IllegalArgumentException for a malformed device handle; both used to escape the service
        // coroutine and crash the app.
        val g = try {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }

        if (g == null) {
            _connectionState.value = ConnectionState.Error(
                "Bluetooth permission is required for a BLE link. Grant Nearby devices and retry."
            )
            return@withContext false
        }
        gatt = g

        val finalState = try {
            withTimeout(15_000L) {
                connectionState.first { state ->
                    state is ConnectionState.Connected ||
                        state is ConnectionState.Error ||
                        state == ConnectionState.Disconnected
                }
            }
        } catch (e: Exception) {
            ConnectionState.Error("BLE connection timed out")
        }

        if (finalState is ConnectionState.Connected) true else {
            disconnect()
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendCommand(command: String, timeoutMs: Long): String =
        withContext(Dispatchers.IO) {
            val ch = writeChar ?: throw IllegalStateException("BLE not connected")

            // Drain stale bytes from any previous exchange
            synchronized(responseBuffer) { responseBuffer.setLength(0) }

            try {
                withTimeout(timeoutMs) {
                    suspendCancellableCoroutine { cont ->
                        val awaiter: (String) -> Unit = { raw ->
                            cont.resume(Elm327Command.cleanResponse(raw))
                        }
                        responseAwaiter = awaiter
                        val data = Elm327Command.format(command).toByteArray(Charsets.US_ASCII)
                        @Suppress("DEPRECATION")
                        ch.value = data
                        gatt?.writeCharacteristic(ch)
                    }
                }
            } finally {
                responseAwaiter = null
            }
        }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: Exception) {}
        gatt = null
        writeChar = null
        notifyChar = null
        _connectionState.value = ConnectionState.Disconnected
    }

    companion object {
        /** True when the device advertises one of the known ELM327 BLE services. */
        fun looksLikeElmBle(uuids: List<ParcelUuid>): Boolean =
            uuids.any { p ->
                val s = p.uuid.toString()
                s.startsWith("fff0", ignoreCase = true) ||
                    s.startsWith("ffe0", ignoreCase = true) ||
                    s.startsWith("6e400001", ignoreCase = true) ||
                    s.startsWith("49535343", ignoreCase = true)
            }
    }
}
