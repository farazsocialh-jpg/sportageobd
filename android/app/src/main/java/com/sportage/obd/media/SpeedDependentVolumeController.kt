package com.sportage.obd.media

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * State of the speed-compensated volume governor.
 */
data class VolumeGovernorState(
    val isEnabled: Boolean = false,
    val baseVolumeIndex: Int = 0,
    val currentBoostIndex: Int = 0,
    val currentSpeedKmh: Int = 0,
    val effectiveVolumeIndex: Int = 0,
    val maxVolumeIndex: Int = 15
)

/**
 * Automotive Speed-Dependent Volume Control (SDVC) for Kia Sportage Cockpit.
 *
 * Automatically compensates for tire road noise, aerodynamic drag, and exhaust drone:
 * - 0 - 50 km/h: Baseline volume (+0 index)
 * - 51 - 90 km/h: Step 1 boost (+1 index)
 * - 91 - 120+ km/h: Step 2 boost (+2 indices)
 *
 * Implements a 3-second hysteresis filter to prevent volume hunting during transient stop-and-go traffic.
 */
class SpeedDependentVolumeController(
    private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _state = MutableStateFlow(
        VolumeGovernorState(
            maxVolumeIndex = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        )
    )
    val state: StateFlow<VolumeGovernorState> = _state.asStateFlow()

    private var baseVolumeIndex: Int = -1
    private var currentAppliedBoost: Int = 0
    private var pendingTargetBoost: Int = 0
    private var pendingBoostSinceMs: Long = 0L

    companion object {
        private const val HYSTERESIS_MS = 3_000L
    }

    /**
     * Enables or disables automatic speed-dependent volume adjustment.
     */
    fun setEnabled(enabled: Boolean) {
        if (enabled && !_state.value.isEnabled) {
            captureCurrentAsBase()
        } else if (!enabled && _state.value.isEnabled) {
            restoreBaseVolume()
        }
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    private fun captureCurrentAsBase() {
        val am = audioManager ?: return
        val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        baseVolumeIndex = (currentVol - currentAppliedBoost).coerceAtLeast(0)
        _state.value = _state.value.copy(
            baseVolumeIndex = baseVolumeIndex,
            currentBoostIndex = currentAppliedBoost,
            effectiveVolumeIndex = currentVol
        )
    }

    private fun restoreBaseVolume() {
        val am = audioManager ?: return
        if (baseVolumeIndex >= 0 && currentAppliedBoost != 0) {
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, baseVolumeIndex, 0)
            } catch (_: Exception) {}
            currentAppliedBoost = 0
            pendingTargetBoost = 0
        }
    }

    /**
     * Updates vehicle speed and applies volume step compensation if hysteresis condition is met.
     *
     * @param speedKmh Current vehicle speed from OBD PID 010D.
     * @param nowMs Current monotonic or system clock timestamp in milliseconds.
     */
    fun onSpeedUpdate(speedKmh: Int?, nowMs: Long = System.currentTimeMillis()) {
        if (!_state.value.isEnabled || speedKmh == null) return
        val am = audioManager ?: return

        val speed = speedKmh.coerceAtLeast(0)

        // Calibrated Road-Noise Thresholds
        val targetBoost = when {
            speed >= 91 -> 2
            speed >= 51 -> 1
            else -> 0
        }

        // Hysteresis timing check
        if (targetBoost != currentAppliedBoost) {
            if (targetBoost != pendingTargetBoost) {
                pendingTargetBoost = targetBoost
                pendingBoostSinceMs = nowMs
            } else if (nowMs - pendingBoostSinceMs >= HYSTERESIS_MS) {
                // Apply the volume step
                applyBoost(am, targetBoost)
            }
        } else {
            pendingTargetBoost = targetBoost
            pendingBoostSinceMs = nowMs
        }

        val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        _state.value = _state.value.copy(
            currentSpeedKmh = speed,
            currentBoostIndex = currentAppliedBoost,
            effectiveVolumeIndex = currentVol
        )
    }

    private fun applyBoost(am: AudioManager, boost: Int) {
        if (baseVolumeIndex < 0) {
            captureCurrentAsBase()
        }

        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (baseVolumeIndex + boost).coerceIn(0, maxVol)

        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            currentAppliedBoost = boost
        } catch (_: Exception) {}
    }

    /**
     * Call when user manually adjusted hardware volume rocker so the baseline is adapted.
     */
    fun onManualVolumeChange() {
        captureCurrentAsBase()
    }

    fun release() {
        restoreBaseVolume()
    }
}
