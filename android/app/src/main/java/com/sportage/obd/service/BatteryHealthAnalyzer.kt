package com.sportage.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

/**
 * Health assessment of 12V lead-acid battery based on starter motor inrush voltage trough.
 */
enum class BatteryCrankStatus(val label: String) {
    /** V_min >= 10.2V: Excellent cold cranking reserve. */
    HEALTHY_BATTERY("HEALTHY"),

    /** 9.6V <= V_min < 10.2V: Moderate degradation or sulfation. Advisory warning. */
    WEAK_BATTERY("WEAK (ADVISORY)"),

    /** V_min < 9.6V: Severely degraded plates. Immediate replacement advised. */
    CRITICAL_REPLACE("CRITICAL REPLACE")
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
 * Samples bus voltage at 50ms intervals during engine startup transitions to capture the
 * lowest cranking voltage trough (V_min).
 *
 * Threshold Evaluation:
 * - V_min >= 10.2V: HEALTHY_BATTERY
 * - 9.6V <= V_min < 10.2V: WEAK_BATTERY
 * - V_min < 9.6V: CRITICAL_REPLACE ("Battery degraded; risk of no-start")
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
     * @param voltage Current electrical bus voltage (AT RV or PID 0142).
     * @param nowMs Current monotonic or wall clock timestamp in milliseconds.
     */
    fun onTelemetrySample(rpm: Int?, voltage: Float?, nowMs: Long = System.currentTimeMillis()) {
        if (voltage == null || voltage <= 0f) return
        val currentRpm = rpm ?: 0

        when (state) {
            CrankState.IDLE_PRE_CRANK -> {
                if (currentRpm == 0) {
                    // Update rolling pre-crank resting voltage
                    restingVoltage = voltage
                } else if (currentRpm in 1..550 && voltage < restingVoltage - 0.5f) {
                    // Starter motor engaged: inrush current triggered voltage trough
                    state = CrankState.CRANKING
                    crankStartTimeMs = nowMs
                    minVoltageDuringCrank = voltage
                }
            }

            CrankState.CRANKING -> {
                minVoltageDuringCrank = min(minVoltageDuringCrank, voltage)

                // Detect transition to stable engine idle (> 600 RPM and voltage rising back up)
                if (currentRpm >= 650 && voltage >= 12.8f) {
                    val duration = (nowMs - crankStartTimeMs).coerceAtLeast(100L)
                    val alternatorV = voltage
                    val vMin = minVoltageDuringCrank

                    val status = when {
                        vMin >= 10.2f -> BatteryCrankStatus.HEALTHY_BATTERY
                        vMin >= 9.6f -> BatteryCrankStatus.WEAK_BATTERY
                        else -> BatteryCrankStatus.CRITICAL_REPLACE
                    }

                    val advisory = when (status) {
                        BatteryCrankStatus.HEALTHY_BATTERY ->
                            "Battery and starter healthy (trough: ${"%.2f".format(vMin)}V). Cold cranking amps nominal."
                        BatteryCrankStatus.WEAK_BATTERY ->
                            "Battery cranking voltage weak (${"%.2f".format(vMin)}V). Recommend testing charging system and terminals."
                        BatteryCrankStatus.CRITICAL_REPLACE ->
                            "Critical: Battery cranking voltage dropped to ${"%.2f".format(vMin)}V. High risk of no-start; replace battery."
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
                // If engine shuts down (RPM drops to 0), reset back to IDLE_PRE_CRANK for next startup
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
