package com.sportage.obd.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sportage.obd.MainActivity
import com.sportage.obd.bluetooth.BluetoothTransport
import com.sportage.obd.bluetooth.ConnectionState
import com.sportage.obd.obd.DtcItem
import com.sportage.obd.obd.PidDecoders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import com.sportage.obd.bluetooth.BluetoothDeviceInfo

/**
 * Data model for live vehicle telemetry collected by ObdService.
 */
data class ObdTelemetry(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null,
    val fuelLevelPct: Int? = null,
    val throttlePct: Float? = null,
    val engineLoadPct: Float? = null,
    val batteryVoltage: Float? = null,
    val estimatedGear: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val sampleRateHz: Float = 0f
)

/**
 * ObdService: Android Foreground Service with type 'connectedDevice'.
 * Runs continuously in the background, decoupled from Activity lifecycle,
 * maintaining an active Bluetooth link with ELM327 adapters and streaming
 * vehicle telemetry.
 */
class ObdService : Service() {

    companion object {
        const val CHANNEL_ID = "sportage_obd_service_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.sportage.obd.action.START_SERVICE"
        const val ACTION_STOP = "com.sportage.obd.action.STOP_SERVICE"
        const val ACTION_PAUSE_POLLING = "com.sportage.obd.action.PAUSE_POLLING"
        const val ACTION_RESUME_POLLING = "com.sportage.obd.action.RESUME_POLLING"

        /** True while the foreground service is alive, so a restarted UI can re-attach to it. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val binder = ObdServiceBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val transport = BluetoothTransport()
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null

    // Target Bluetooth device to maintain connection with
    private var activeDevice: BluetoothDevice? = null

    // Telemetry and connection state
    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    private val _isPollingPaused = MutableStateFlow(false)
    val isPollingPaused: StateFlow<Boolean> = _isPollingPaused.asStateFlow()

    private val _dtcList = MutableStateFlow<List<DtcItem>>(emptyList())
    val dtcList: StateFlow<List<DtcItem>> = _dtcList.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = transport.connectionState

    // Diagnostic / frequency tracking
    private var packetCount = 0
    private var lastHzCalculationTime = System.currentTimeMillis()
    private var currentHz = 0f

    inner class ObdServiceBinder : Binder() {
        fun getService(): ObdService = this@ObdService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("OBD Service Initializing..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling()
                transport.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_POLLING -> {
                pausePolling()
            }
            ACTION_RESUME_POLLING -> {
                resumePolling()
            }
            ACTION_START -> {
                updateNotification("Ready for Bluetooth link")
            }
        }
        return START_STICKY
    }

    /**
     * Connect to target Bluetooth OBD adapter with automatic reconnection.
     */
    fun connectToDevice(device: BluetoothDevice) {
        activeDevice = device
        reconnectJob?.cancel()
        serviceScope.launch {
            val success = transport.connect(device)
            if (success) {
                updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, device)} · Polling active")
                initializeElm327()
                startPollingLoop()
            } else {
                updateNotification("Connection failed. Retrying...")
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        activeDevice = null
        reconnectJob?.cancel()
        stopPolling()
        transport.disconnect()
        updateNotification("Disconnected")
    }

    fun pausePolling() {
        _isPollingPaused.value = true
        updateNotification("Polling paused (Ignition off mode)")
    }

    fun resumePolling() {
        _isPollingPaused.value = false
        if (pollingJob == null || pollingJob?.isActive == false) {
            startPollingLoop()
        }
        updateNotification("Streaming vehicle telemetry")
    }

    suspend fun sendRawCommand(command: String): String {
        return transport.sendCommand(command)
    }

    suspend fun scanDtcCodes(): List<DtcItem> {
        return try {
            val response = transport.sendCommand("03")
            val parsed = PidDecoders.decodeDtcCodes(response)
            _dtcList.value = parsed
            parsed
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun clearDtcCodes(): Boolean {
        return try {
            val response = transport.sendCommand("04")
            if (response.contains("OK") || response.contains("44")) {
                _dtcList.value = emptyList()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun initializeElm327() {
        try {
            transport.sendCommand("ATZ")      // Reset ELM327
            delay(150)
            transport.sendCommand("ATE0")     // Echo off
            delay(50)
            transport.sendCommand("ATL0")     // Linefeeds off
            delay(50)
            transport.sendCommand("ATS0")     // Spaces off
            delay(50)
            transport.sendCommand("ATH0")     // Headers off
            delay(50)
            transport.sendCommand("ATSP0")    // Auto protocol detect (matches CAN 11/500 for Sportage)
            delay(100)
        } catch (_: Exception) {}
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                if (_isPollingPaused.value) {
                    delay(500)
                    continue
                }

                try {
                    // 1. Engine RPM (PID 010C) - high priority
                    val rpmResp = transport.sendCommand("010C")
                    val rpm = PidDecoders.decodeRpm(rpmResp)

                    // 2. Vehicle Speed (PID 010D) - high priority
                    val spdResp = transport.sendCommand("010D")
                    val speed = PidDecoders.decodeSpeed(spdResp)

                    // Measure sampling frequency
                    packetCount += 2
                    val now = System.currentTimeMillis()
                    if (now - lastHzCalculationTime >= 1000) {
                        currentHz = (packetCount * 1000f) / (now - lastHzCalculationTime)
                        packetCount = 0
                        lastHzCalculationTime = now
                    }

                    // Estimate gear based on speed and RPM for Kia Sportage 6-speed automatic
                    val gear = estimateGear(speed ?: 0, rpm ?: 0)

                    // Lower-frequency secondary PIDs
                    var coolant = _telemetry.value.coolantTempC
                    var intake = _telemetry.value.intakeTempC
                    var fuel = _telemetry.value.fuelLevelPct
                    var throttle = _telemetry.value.throttlePct
                    var load = _telemetry.value.engineLoadPct
                    var voltage = _telemetry.value.batteryVoltage

                    // Query one auxiliary PID per cycle
                    when ((packetCount / 2) % 6) {
                        0 -> {
                            val resp = transport.sendCommand("0105")
                            coolant = PidDecoders.decodeCoolantTemp(resp) ?: coolant
                        }
                        1 -> {
                            val resp = transport.sendCommand("010F")
                            intake = PidDecoders.decodeIntakeTemp(resp) ?: intake
                        }
                        2 -> {
                            val resp = transport.sendCommand("012F")
                            fuel = PidDecoders.decodeFuelLevel(resp) ?: fuel
                        }
                        3 -> {
                            val resp = transport.sendCommand("0111")
                            throttle = PidDecoders.decodeThrottle(resp) ?: throttle
                        }
                        4 -> {
                            val resp = transport.sendCommand("0104")
                            load = PidDecoders.decodeLoad(resp) ?: load
                        }
                        5 -> {
                            val resp = transport.sendCommand("ATRV")
                            voltage = PidDecoders.decodeBatteryVoltage(resp) ?: voltage
                        }
                    }

                    _telemetry.value = ObdTelemetry(
                        speedKmh = speed,
                        rpm = rpm,
                        coolantTempC = coolant,
                        intakeTempC = intake,
                        fuelLevelPct = fuel,
                        throttlePct = throttle,
                        engineLoadPct = load,
                        batteryVoltage = voltage,
                        estimatedGear = gear,
                        timestamp = now,
                        sampleRateHz = currentHz
                    )

                    delay(30) // Fast cycle rate
                } catch (e: Exception) {
                    if (isActive && activeDevice != null) {
                        scheduleReconnect()
                        break
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun scheduleReconnect() {
        stopPolling()
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var attempt = 0
            val target = activeDevice ?: return@launch
            while (isActive && activeDevice != null) {
                attempt++
                val backoffSec = min(16, 1 shl (attempt - 1))
                updateNotification("Reconnecting in ${backoffSec}s (Attempt $attempt)...")
                delay(backoffSec * 1000L)

                if (!isActive || activeDevice == null) break

                updateNotification("Reconnecting to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}...")
                val ok = transport.connect(target)
                if (ok) {
                    updateNotification("Connected to ${BluetoothDeviceInfo.displayName(this@ObdService, target)}")
                    initializeElm327()
                    startPollingLoop()
                    break
                }
            }
        }
    }

    private fun estimateGear(speedKmh: Int, rpm: Int): Int {
        if (speedKmh <= 2 || rpm < 600) return 0 // P / N
        val ratio = rpm.toFloat() / speedKmh.toFloat()
        return when {
            ratio > 80f -> 1
            ratio in 55f..80f -> 2
            ratio in 38f..55f -> 3
            ratio in 28f..38f -> 4
            ratio in 22f..28f -> 5
            else -> 6
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sportage OBD Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps ELM327 OBD-II streaming active in background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sportage OBD-II Intelligence")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopPolling()
        reconnectJob?.cancel()
        serviceScope.cancel()
        transport.disconnect()
    }
}
