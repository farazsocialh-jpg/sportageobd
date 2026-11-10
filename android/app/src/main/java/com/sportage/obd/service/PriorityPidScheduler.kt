package com.sportage.obd.service

import kotlin.math.max
import kotlin.math.min

/**
 * Priority tiers for automotive OBD-II PID polling.
 */
enum class PollingTier {
    /** 15 - 25 Hz: Fast dynamic powertrain variables (RPM, Speed, Throttle, Load). */
    TIER_1_HIGH,

    /** 1 - 2 Hz: Thermal, electrical, and fuel trim values (Coolant, Intake, Battery, STFT, LTFT, MAF, Timing, Gear). */
    TIER_2_MEDIUM,

    /** ~0.033 Hz (Every 30s): Slow-changing environmental, tank, and readiness state (Fuel level, Ambient, Baro, Readiness). */
    TIER_3_LOW
}

/**
 * Representation of an individual PID polling task.
 */
data class ScheduledPid(
    val command: String,
    val pid: String,
    val tier: PollingTier,
    val minIntervalMs: Long,
    var lastPolledAtMs: Long = 0L,
    var failureCount: Int = 0,
    var isSupported: Boolean = true
)

/**
 * Result returned by the scheduler for the next command execution.
 */
data class ScheduledExecution(
    val command: String,
    val pid: String,
    val tier: PollingTier
)

/**
 * Three-tier adaptive priority scheduler for ELM327 OBD-II polling on Kia Sportage (SL/QL Nu 2.0L MPI).
 *
 * Solves the sequential round-robin starvation problem by prioritizing high-frequency powertrain
 * sensors (15 - 25 Hz) while interleaving medium-frequency thermal/trim diagnostics (1 - 2 Hz)
 * and low-frequency environmental metrics (every 30s).
 *
 * Features automatic ELM327 response-time measurement (adaptiveDelayMs) using an exponential
 * moving average (EMA) to prevent UART buffer overflow and command collision on budget adapters.
 */
class PriorityPidScheduler(
    private val fastTargetHz: Float = 20f,
    private val minDelayMs: Long = 8L,
    private val maxDelayMs: Long = 80L
) {
    // Tier 1: High Priority (15 - 25 Hz)
    private val tier1Pids = listOf(
        ScheduledPid(command = "010C", pid = "0C", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "010D", pid = "0D", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "0111", pid = "11", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "0104", pid = "04", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L)
    )

    // Tier 2: Medium Priority (1 - 2 Hz)
    private val tier2Pids = listOf(
        ScheduledPid(command = "0105", pid = "05", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 600L),
        ScheduledPid(command = "010F", pid = "0F", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 800L),
        ScheduledPid(command = "ATRV", pid = "RV", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 800L),
        ScheduledPid(command = "0110", pid = "10", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 500L),
        ScheduledPid(command = "0106", pid = "06", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "0107", pid = "07", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1200L),
        ScheduledPid(command = "010E", pid = "0E", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 800L),
        ScheduledPid(command = "01A4", pid = "A4", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 800L)
    )

    // Tier 3: Low Priority (Every 30s)
    private val tier3Pids = listOf(
        ScheduledPid(command = "012F", pid = "2F", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0146", pid = "46", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0133", pid = "33", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0101", pid = "01", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L)
    )

    private val dynamicExtraPids = mutableListOf<ScheduledPid>()

    private var tier1Index = 0
    private var tier2Index = 0
    private var tier3Index = 0
    private var tier1CounterSinceAux = 0

    // Timing & Adaptive Delay metrics
    private var smoothedRttMs: Float = 35.0f
    private var consecutiveErrors: Int = 0
    private var sampleCount: Int = 0
    private var lastSampleTimestamp: Long = 0L
    private var currentCalculatedHz: Float = 0f
    private var lastTier3PolledAt: Long = 0L

    /**
     * Updates extra sensor capabilities discovered from ECU Mode 01 bitmaps.
     */
    fun updateExtraPids(extraPidList: List<String>) {
        val existingPids = (tier1Pids + tier2Pids + tier3Pids).map { it.pid }.toSet()
        val currentExtras = dynamicExtraPids.map { it.pid }.toSet()
        for (pid in extraPidList) {
            if (pid !in existingPids && pid !in currentExtras) {
                dynamicExtraPids.add(
                    ScheduledPid(
                        command = "01$pid",
                        pid = pid,
                        tier = PollingTier.TIER_3_LOW,
                        minIntervalMs = 30_000L
                    )
                )
            }
        }
    }

    /**
     * Determines the next command to transmit to the ELM327 adapter based on priority tiers,
     * time elapsed since last sample, and ECU PID capability support.
     */
    fun nextCommand(nowMs: Long, supportedPids: Set<String>): ScheduledExecution {
        fun isPidAllowed(sp: ScheduledPid): Boolean {
            if (!sp.isSupported || sp.failureCount >= 3) return false
            if (sp.command.startsWith("AT")) return true
            return supportedPids.isEmpty() || sp.pid in supportedPids
        }

        // 1. Check Tier 3 (Once every 30s) if due and spacing allows (at most 1 Tier 3 every 3s)
        if (nowMs - lastTier3PolledAt >= 3_000L) {
            val allTier3 = tier3Pids + dynamicExtraPids
            val tier3Count = allTier3.size
            for (offset in 0 until tier3Count) {
                val idx = (tier3Index + offset) % tier3Count
                val candidate = allTier3[idx]
                if (isPidAllowed(candidate) && (candidate.lastPolledAtMs == 0L || nowMs - candidate.lastPolledAtMs >= candidate.minIntervalMs)) {
                    tier3Index = (idx + 1) % tier3Count
                    candidate.lastPolledAtMs = nowMs
                    lastTier3PolledAt = nowMs
                    tier1CounterSinceAux = 0
                    return ScheduledExecution(candidate.command, candidate.pid, PollingTier.TIER_3_LOW)
                }
            }
        }

        // 2. Interleave Tier 2 (1 - 2 Hz) after at least 2 consecutive Tier 1 queries
        if (tier1CounterSinceAux >= 2) {
            val tier2Count = tier2Pids.size
            for (offset in 0 until tier2Count) {
                val idx = (tier2Index + offset) % tier2Count
                val candidate = tier2Pids[idx]
                if (isPidAllowed(candidate) && (candidate.lastPolledAtMs == 0L || nowMs - candidate.lastPolledAtMs >= candidate.minIntervalMs)) {
                    tier2Index = (idx + 1) % tier2Count
                    candidate.lastPolledAtMs = nowMs
                    tier1CounterSinceAux = 0
                    return ScheduledExecution(candidate.command, candidate.pid, PollingTier.TIER_2_MEDIUM)
                }
            }
        }

        // 3. Fallback to Tier 1: High Priority (RPM, Speed, Throttle, Load)
        val tier1Count = tier1Pids.size
        for (offset in 0 until tier1Count) {
            val idx = (tier1Index + offset) % tier1Count
            val candidate = tier1Pids[idx]
            if (isPidAllowed(candidate)) {
                tier1Index = (idx + 1) % tier1Count
                candidate.lastPolledAtMs = nowMs
                tier1CounterSinceAux++
                return ScheduledExecution(candidate.command, candidate.pid, PollingTier.TIER_1_HIGH)
            }
        }

        // Failsafe: return RPM
        tier1Index = (tier1Index + 1) % tier1Pids.size
        tier1CounterSinceAux++
        return ScheduledExecution("010C", "0C", PollingTier.TIER_1_HIGH)
    }

    /**
     * Records elapsed round-trip time for a command response to dynamically calculate
     * the UART safety delay and monitor telemetry throughput.
     */
    fun recordResponseTime(command: String, elapsedMs: Long, success: Boolean) {
        val now = System.currentTimeMillis()
        sampleCount++
        if (lastSampleTimestamp == 0L) {
            lastSampleTimestamp = now
        } else if (now - lastSampleTimestamp >= 1000L) {
            currentCalculatedHz = (sampleCount * 1000f) / (now - lastSampleTimestamp)
            sampleCount = 0
            lastSampleTimestamp = now
        }

        if (success) {
            consecutiveErrors = 0
            // Exponential moving average (alpha = 0.15)
            smoothedRttMs = (smoothedRttMs * 0.85f) + (elapsedMs.toFloat() * 0.15f)
        } else {
            consecutiveErrors++
            smoothedRttMs = max(smoothedRttMs, elapsedMs.toFloat())
            // Mark failure against the matching PID
            findPid(command)?.let {
                it.failureCount++
                if (it.failureCount >= 3) {
                    it.isSupported = false
                }
            }
        }
    }

    private fun findPid(command: String): ScheduledPid? {
        val all = tier1Pids + tier2Pids + tier3Pids + dynamicExtraPids
        return all.firstOrNull { it.command == command }
    }

    /**
     * Calculates the adaptive delay in milliseconds between ELM327 queries.
     * Prevents UART buffer overflow on slower clone adapters while achieving 20-25 Hz on fast links.
     */
    fun getAdaptiveDelayMs(): Long {
        if (consecutiveErrors >= 2) {
            return min(maxDelayMs, 50L + consecutiveErrors * 10L)
        }

        // Target cycle time for fastTargetHz (e.g. 50ms for 20Hz)
        val targetCycleMs = (1000f / fastTargetHz).toLong()
        val calculatedDelay = targetCycleMs - smoothedRttMs.toLong()

        return when {
            smoothedRttMs > 120f -> 35L // Slower adapter: maintain safety spacing
            smoothedRttMs > 70f -> 20L  // Average adapter
            else -> calculatedDelay.coerceIn(minDelayMs, maxDelayMs)
        }
    }

    fun getSmoothedRttMs(): Float = smoothedRttMs

    fun getEffectiveHz(): Float = currentCalculatedHz

    fun reset() {
        smoothedRttMs = 35.0f
        consecutiveErrors = 0
        sampleCount = 0
        lastSampleTimestamp = 0L
        currentCalculatedHz = 0f
        tier1Index = 0
        tier2Index = 0
        tier3Index = 0
        tier1CounterSinceAux = 0
        val all = tier1Pids + tier2Pids + tier3Pids + dynamicExtraPids
        for (item in all) {
            item.lastPolledAtMs = 0L
            item.failureCount = 0
            item.isSupported = true
        }
    }
}
