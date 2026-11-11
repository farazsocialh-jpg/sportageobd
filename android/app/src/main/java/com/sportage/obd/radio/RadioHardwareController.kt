package com.sportage.obd.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RadioBand {
    FM1, FM2, FM3, AM1, AM2
}

data class RadioTunerState(
    val isPoweredOn: Boolean = false,
    val band: RadioBand = RadioBand.FM1,
    val frequencyKHz: Int = 101100,
    val stationName: String? = null,
    val rdsRadioText: String? = null,
    val signalStrength: Int = 0
)

class RadioHardwareController(private val context: Context) {

    private val _state = MutableStateFlow(RadioTunerState())
    val state: StateFlow<RadioTunerState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val extras = intent.extras
            val current = _state.value
            var powered = current.isPoweredOn
            var band = current.band
            var freq = current.frequencyKHz
            var name = current.stationName
            var rt = current.rdsRadioText
            var signal = current.signalStrength

            when (action) {
                "com.microntek.radio.power" -> {
                    powered = extras?.getBoolean("power", extras?.getInt("state", if (current.isPoweredOn) 1 else 0) == 1)
                        ?: current.isPoweredOn
                }
                else -> {
                    extras?.let { b ->
                        if (b.containsKey("power")) powered = b.getBoolean("power", powered)
                        if (b.containsKey("state")) powered = b.getInt("state", if (powered) 1 else 0) == 1
                        b.getString("band")?.let { band = parseBand(it, band) }
                        if (b.containsKey("bandIndex")) band = bandFromIndex(b.getInt("bandIndex", 0))
                        if (b.containsKey("freq")) freq = b.getInt("freq", freq)
                        if (b.containsKey("frequency")) freq = b.getInt("frequency", freq)
                        if (b.containsKey("frequencyKHz")) freq = b.getInt("frequencyKHz", freq)
                        b.getString("name")?.let { name = it }
                        b.getString("station")?.let { name = it }
                        b.getString("ps")?.let { name = it }
                        b.getString("radiotext")?.let { rt = it }
                        b.getString("rt")?.let { rt = it }
                        if (b.containsKey("signal")) signal = b.getInt("signal", signal)
                        if (b.containsKey("rssi")) signal = b.getInt("rssi", signal)
                    }
                    if (action.contains("report", ignoreCase = true)) powered = true
                }
            }
            _state.value = RadioTunerState(
                isPoweredOn = powered,
                band = band,
                frequencyKHz = freq,
                stationName = name,
                rdsRadioText = rt,
                signalStrength = signal.coerceIn(0, 100)
            )
        }
    }

    private val filter = IntentFilter().apply {
        addAction("com.microntek.radio.report")
        addAction("com.microntek.radio.power")
        addAction("com.syu.radio.report")
        addAction("com.syu.ms.radio")
        addAction("com.ts.radioplayer.report")
        addAction("com.android.fmradio.report")
    }

    fun register() {
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    fun unregister() {
        runCatching { context.unregisterReceiver(receiver) }
    }

    fun tuneFrequency(khz: Int) {
        val freq = khz.coerceIn(87500, 108000)
        _state.value = _state.value.copy(frequencyKHz = freq)
        broadcast("com.microntek.radio.setfreq", "freq" to freq, "frequency" to freq, "frequencyKHz" to freq)
        broadcast("com.syu.ms.radio", "cmd" to "tune", "freq" to freq, "frequency" to freq)
        broadcast("com.ts.radioplayer.tune", "freq" to freq, "frequency" to freq)
        broadcast("com.android.fmradio.TUNE", "frequency" to freq)
    }

    fun seekUp() {
        broadcast("com.microntek.radio.seekup")
        broadcast("com.syu.ms.radio", "cmd" to "seekup")
        broadcast("com.ts.radioplayer.seekup")
        broadcast("com.android.fmradio.SEEK_UP")
    }

    fun seekDown() {
        broadcast("com.microntek.radio.seekdown")
        broadcast("com.syu.ms.radio", "cmd" to "seekdown")
        broadcast("com.ts.radioplayer.seekdown")
        broadcast("com.android.fmradio.SEEK_DOWN")
    }

    fun toggleBand() {
        val next = when (_state.value.band) {
            RadioBand.FM1 -> RadioBand.FM2
            RadioBand.FM2 -> RadioBand.FM3
            RadioBand.FM3 -> RadioBand.AM1
            RadioBand.AM1 -> RadioBand.AM2
            RadioBand.AM2 -> RadioBand.FM1
        }
        _state.value = _state.value.copy(band = next)
        broadcast("com.microntek.radio.band", "band" to next.name)
        broadcast("com.syu.ms.radio", "cmd" to "band", "band" to next.name)
        broadcast("com.ts.radioplayer.band", "band" to next.name)
    }

    fun togglePower() {
        val next = !_state.value.isPoweredOn
        _state.value = _state.value.copy(isPoweredOn = next)
        broadcast("com.microntek.radio.power", "power" to next, "state" to if (next) 1 else 0)
        broadcast("com.syu.ms.radio", "cmd" to "power", "power" to next)
        broadcast("com.ts.radioplayer.power", "power" to next)
        broadcast("com.android.fmradio.POWER", "state" to if (next) 1 else 0)
    }

    private fun broadcast(action: String, vararg extras: Pair<String, Any>) {
        runCatching {
            val intent = Intent(action).apply {
                extras.forEach { (k, v) ->
                    when (v) {
                        is Int -> putExtra(k, v)
                        is Boolean -> putExtra(k, v)
                        is String -> putExtra(k, v)
                    }
                }
            }
            context.sendBroadcast(intent)
        }
    }

    private fun parseBand(value: String, fallback: RadioBand): RadioBand =
        runCatching { RadioBand.valueOf(value.uppercase()) }.getOrDefault(fallback)

    private fun bandFromIndex(index: Int): RadioBand =
        RadioBand.entries.getOrElse(index) { RadioBand.FM1 }
}