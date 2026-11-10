package com.sportage.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Health assessment of 12V automotive battery based on starter motor inrush voltage trough (V_min).
 */
enum class BatteryCrankStatus(val label: String) {
    /** V_min >= 10.2V: HEALTHY (Green). Cold cranking amps nominal. */
    HEALTHY("HEALTHY"),

    /** 9.6V <= V_min < 10.2V: WEAK_BATTERY (Amber advisory). Moderate degradation. */
    WEAK_BATTERY("WEAK_BATTERY"),

    /** V_min < 9.6V: CRITICAL_REPLACE (Red alert). Severe degradation / high risk of no-start. */
    CRITICAL_REPLACE("CRITICAL_REPLACE");

    companion object {
        /** Alias for backwards compatibility. */
        val HEALTHY_BATTERY: BatteryCrankStatus get() = HEALTHY
    }
}

/**
 * Comprehensive battery cranking diagnostic record.
 */
data class BatteryCrankResult(
    val status: BatteryCrankStatus,
    val minCrankVoltage: Float,
    val restingVoltage: Float,
    val alternatorVoltage: Float,
    val crankDurationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val advisory: String
)

/**
 * Battery Cranking Voltage Health Monitor for Kia Sportage (Nu 2.0L MPI).
 *
 * Implements Module 4:
 * During engine cranking (voltage drop from resting ~12.6V down to lowest trough V_min):
 * - V_min >= 10.2 V: HEALTHY (Green)
 * - 9.6 V <= V_min < 10.2 V: WEAK_BATTERY (Amber advisory)
 * - V_min < 9.6 V: CRITICAL_REPLACE (Red alert: "Battery degraded; risk of no-start")
 */
class BatteryHealthAnalyzer {

    private val _latestResult = MutableStateFlow<BatteryCrankResult?>(null)
    val latestResult: StateFlow<BatteryCrankResult?> = _latestResult.asStateFlow()

    private enum class CrankState {
        IDLE_PRE_CRANK,
        CRANKING,
        RUNNING_EVALUATED
    }

    private var state = CrankState.IDLE_PRE_CRANK
    private var restingVoltage = 12.6f
    private var minVoltageDuringCrank = Float.MAX_VALUE
    private var crankStartTimeMs = 0L
    private var lastSampleTimeMs = 0L

    /**
     * Ingests high-frequency engine speed and bus voltage telemetry.
     *
     * @param rpm Current engine speed (PID 010C).
     * @param voltage Current electrical bus voltage (ATRV or PID 0142).
     * @param nowMs Current timestamp in milliseconds.
     */
    fun onTelemetrySample(rpm: Int?, voltage: Float?, nowMs: Long = System.currentTimeMillis()) {
        if (voltage == null || voltage <= 0f) return
        val currentRpm = rpm ?: 0

        when (state) {
            CrankState.IDLE_PRE_CRANK -> {
                if (currentRpm == 0) {
                    // Pre-crank resting voltage sample
                    restingVoltage = voltage
                } else if (currentRpm in 1..550 && voltage < restingVoltage - 0.4f) {
                    // Starter motor inrush engaged: voltage drop begins
                    state = CrankState.CRANKING
                    crankStartTimeMs = nowMs
                    minVoltageDuringCrank = voltage
                }
            }

            CrankState.CRANKING -> {
                minVoltageDuringCrank = min(minVoltageDuringCrank, voltage)

                // Detect transition to stable idle (> 650 RPM and alternator voltage recovery >= 12.8V)
                if (currentRpm >= 650 && voltage >= 12.8f) {
                    val duration = (nowMs - crankStartTimeMs).coerceAtLeast(100L)
                    val alternatorV = voltage
                    val vMin = minVoltageDuringCrank

                    val status = when {
                        vMin >= 10.2f -> BatteryCrankStatus.HEALTHY
                        vMin >= 9.6f -> BatteryCrankStatus.WEAK_BATTERY
                        else -> BatteryCrankStatus.CRITICAL_REPLACE
                    }

                    val advisory = when (status) {
                        BatteryCrankStatus.HEALTHY ->
                            "Battery healthy. Cold cranking voltage nominal (${"%.2f".format(vMin)}V)."
                        BatteryCrankStatus.WEAK_BATTERY ->
                            "Battery cranking voltage weak (${"%.2f".format(vMin)}V). Recommend testing charging system and terminals."
                        BatteryCrankStatus.CRITICAL_REPLACE ->
                            "Battery degraded; risk of no-start"
                    }

                    _latestResult.value = BatteryCrankResult(
                        status = status,
                        minCrankVoltage = vMin,
                        restingVoltage = restingVoltage,
                        alternatorVoltage = alternatorV,
                        crankDurationMs = duration,
                        timestamp = nowMs,
                        advisory = advisory
                    )

                    state = CrankState.RUNNING_EVALUATED
                }
            }

            CrankState.RUNNING_EVALUATED -> {
                // If engine shuts down (RPM drops to 0), reset back to IDLE_PRE_CRANK for next crank event
                if (currentRpm == 0 && voltage < 13.0f) {
                    state = CrankState.IDLE_PRE_CRANK
                    minVoltageDuringCrank = Float.MAX_VALUE
                }
            }
        }

        lastSampleTimeMs = nowMs
    }

    /**
     * Manually resets analyzer state (e.g. upon new OBD connection).
     */
    fun reset() {
        state = CrankState.IDLE_PRE_CRANK
        restingVoltage = 12.6f
        minVoltageDuringCrank = Float.MAX_VALUE
        crankStartTimeMs = 0L
    }
}
