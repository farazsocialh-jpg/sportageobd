package com.sportage.obd.service

import kotlin.math.max
import kotlin.math.min

/**
 * Priority tiers for automotive OBD-II PID polling.
 */
enum class PollingTier {
    /** High Priority: Fast dynamic powertrain variables (RPM, Speed, Throttle, Load). */
    TIER_1_HIGH,

    /** Medium Priority (1 Hz): Thermal, intake, and electrical status (Coolant, Intake Temp, Battery Voltage). */
    TIER_2_MEDIUM,

    /** Low Priority (~Once every 30s): Environmental and monitor status (Fuel Level, DTC readiness). */
    TIER_3_LOW
}

/**
 * Representation of an individual scheduled PID polling task.
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
    val tier: PollingTier,
    val isBatched: Boolean = false,
    val expectedPids: List<String> = emptyList()
)

/**
 * Decoded result from a batched Tier 1 transaction (01 0C 0D 11 04).
 */
data class BatchedTier1Result(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val throttlePct: Float? = null,
    val engineLoadPct: Float? = null
)

/**
 * Decoded result from a batched Tier 2 transaction (01 05 0F).
 */
data class BatchedTier2Result(
    val coolantTempC: Int? = null,
    val intakeTempC: Int? = null
)

/**
 * Three-tier adaptive priority scheduler with batched multi-PID querying for Kia Sportage (Nu 2.0L MPI).
 *
 * Implements Module 2:
 * 1. Tier 1 (High Priority - Batched Mode 01): Request combined "01 0C 0D 11 04" (RPM, Speed, Throttle, Load)
 *    in a single ELM327 transaction. Automatically falls back to sequential single-PID polling if the adapter
 *    returns "NO DATA", "?", or an unparseable error.
 * 2. Tier 2 (Medium Priority - 1 Hz): Queries combined "01 05 0F" (Coolant Temp, Intake Temp) and "ATRV" (Battery Voltage).
 * 3. Tier 3 (Low Priority - Once every 30s): Queries "012F" (Fuel Tank Level) and "0101" (DTC monitor readiness).
 * 4. Adaptive delay calculation (adaptiveDelayMs) using an exponential moving average (EMA) of ELM327
 *    transaction round-trip latency to prevent UART buffer overflow and command collision.
 */
class PriorityPidScheduler(
    private val fastTargetHz: Float = 20f,
    private val minDelayMs: Long = 8L,
    private val maxDelayMs: Long = 80L,
    val enableBatchedTier1: Boolean = true,
    val enableBatchedTier2: Boolean = true
) {
    companion object {
        const val BATCHED_TIER_1_CMD = "01 0C 0D 11 04"
        const val BATCHED_TIER_2_CMD = "01 05 0F"
    }

    // Tier 1 fallback single PIDs
    private val tier1SinglePids = listOf(
        ScheduledPid(command = "010C", pid = "0C", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "010D", pid = "0D", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "0111", pid = "11", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L),
        ScheduledPid(command = "0104", pid = "04", tier = PollingTier.TIER_1_HIGH, minIntervalMs = 0L)
    )

    // Tier 2 individual PIDs (fallback and auxiliary)
    private val tier2SinglePids = listOf(
        ScheduledPid(command = "0105", pid = "05", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "010F", pid = "0F", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "ATRV", pid = "RV", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "0110", pid = "10", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 800L),
        ScheduledPid(command = "0106", pid = "06", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "0107", pid = "07", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1200L),
        ScheduledPid(command = "010E", pid = "0E", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L),
        ScheduledPid(command = "01A4", pid = "A4", tier = PollingTier.TIER_2_MEDIUM, minIntervalMs = 1000L)
    )

    // Tier 3 low-frequency PIDs (30s cadence)
    private val tier3Pids = listOf(
        ScheduledPid(command = "012F", pid = "2F", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0101", pid = "01", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0146", pid = "46", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L),
        ScheduledPid(command = "0133", pid = "33", tier = PollingTier.TIER_3_LOW, minIntervalMs = 30_000L)
    )

    private val dynamicExtraPids = mutableListOf<ScheduledPid>()

    // Dynamic support flags
    var isBatchedTier1Supported: Boolean = enableBatchedTier1
        private set

    var isBatchedTier2Supported: Boolean = enableBatchedTier2
        private set

    private var tier1Index = 0
    private var tier2Index = 0
    private var tier3Index = 0
    private var tier1CounterSinceAux = 0

    // Timing metrics
    private var smoothedRttMs: Float = 35.0f
    private var consecutiveErrors: Int = 0
    private var sampleCount: Int = 0
    private var lastSampleTimestamp: Long = 0L
    private var currentCalculatedHz: Float = 0f
    private var lastTier2PolledAt: Long = 0L
    private var lastTier3PolledAt: Long = 0L
    private var lastAtrvPolledAt: Long = 0L

    /**
     * Updates extra sensor capabilities discovered from ECU Mode 01 bitmaps.
     */
    fun updateExtraPids(extraPidList: List<String>) {
        val existingPids = (tier1SinglePids + tier2SinglePids + tier3Pids).map { it.pid }.toSet()
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
     * Computes the next OBD-II command according to the 3-tier priority ring.
     */
    fun nextCommand(nowMs: Long, supportedPids: Set<String>): ScheduledExecution {
        fun isPidAllowed(sp: ScheduledPid): Boolean {
            if (!sp.isSupported || sp.failureCount >= 3) return false
            if (sp.command.startsWith("AT")) return true
            return supportedPids.isEmpty() || sp.pid in supportedPids
        }

        // 1. Tier 3: Environmental / readiness state (once every 30s)
        if (nowMs - lastTier3PolledAt >= 30_000L || (lastTier3PolledAt == 0L && tier1CounterSinceAux >= 6)) {
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
                    return ScheduledExecution(
                        command = candidate.command,
                        pid = candidate.pid,
                        tier = PollingTier.TIER_3_LOW
                    )
                }
            }
        }

        // 2. Tier 2: Thermal and voltage (interleaved at ~1 Hz or every 2 Tier-1 cycles)
        if (tier1CounterSinceAux >= 2 && nowMs - lastTier2PolledAt >= 600L) {
            lastTier2PolledAt = nowMs
            tier1CounterSinceAux = 0

            // Alternate between battery voltage (ATRV) and engine thermal sensors (01 05 0F)
            if (nowMs - lastAtrvPolledAt >= 1000L) {
                lastAtrvPolledAt = nowMs
                return ScheduledExecution(
                    command = "ATRV",
                    pid = "RV",
                    tier = PollingTier.TIER_2_MEDIUM
                )
            }

            // Batched Mode 01 thermal query: 01 05 0F
            if (isBatchedTier2Supported && (supportedPids.isEmpty() || ("05" in supportedPids && "0F" in supportedPids))) {
                return ScheduledExecution(
                    command = BATCHED_TIER_2_CMD,
                    pid = "05",
                    tier = PollingTier.TIER_2_MEDIUM,
                    isBatched = true,
                    expectedPids = listOf("05", "0F")
                )
            }

            // Fallback: Individual Tier 2 single PID
            val tier2Count = tier2SinglePids.size
            for (offset in 0 until tier2Count) {
                val idx = (tier2Index + offset) % tier2Count
                val candidate = tier2SinglePids[idx]
                if (isPidAllowed(candidate) && (candidate.lastPolledAtMs == 0L || nowMs - candidate.lastPolledAtMs >= candidate.minIntervalMs)) {
                    tier2Index = (idx + 1) % tier2Count
                    candidate.lastPolledAtMs = nowMs
                    return ScheduledExecution(
                        command = candidate.command,
                        pid = candidate.pid,
                        tier = PollingTier.TIER_2_MEDIUM
                    )
                }
            }
        }

        // 3. Tier 1: High Priority (RPM, Speed, Throttle, Engine Load)
        if (isBatchedTier1Supported) {
            tier1CounterSinceAux++
            return ScheduledExecution(
                command = BATCHED_TIER_1_CMD,
                pid = "0C",
                tier = PollingTier.TIER_1_HIGH,
                isBatched = true,
                expectedPids = listOf("0C", "0D", "11", "04")
            )
        }

        // Tier 1 Fallback: Sequential single-PID round-robin
        val tier1Count = tier1SinglePids.size
        for (offset in 0 until tier1Count) {
            val idx = (tier1Index + offset) % tier1Count
            val candidate = tier1SinglePids[idx]
            if (isPidAllowed(candidate)) {
                tier1Index = (idx + 1) % tier1Count
                candidate.lastPolledAtMs = nowMs
                tier1CounterSinceAux++
                return ScheduledExecution(
                    command = candidate.command,
                    pid = candidate.pid,
                    tier = PollingTier.TIER_1_HIGH
                )
            }
        }

        // Failsafe: return RPM
        tier1Index = (tier1Index + 1) % tier1SinglePids.size
        tier1CounterSinceAux++
        return ScheduledExecution("010C", "0C", PollingTier.TIER_1_HIGH)
    }

    /**
     * Records round-trip transaction latency and evaluates adapter command acceptance.
     * Automatically triggers fallback from batched querying to single-PID querying if the adapter
     * does not support batched Mode 01 requests.
     */
    fun recordResponseTime(
        command: String,
        elapsedMs: Long,
        success: Boolean,
        responseRaw: String = ""
    ) {
        val now = System.currentTimeMillis()
        sampleCount++
        if (lastSampleTimestamp == 0L) {
            lastSampleTimestamp = now
        } else if (now - lastSampleTimestamp >= 1000L) {
            currentCalculatedHz = (sampleCount * 1000f) / (now - lastSampleTimestamp)
            sampleCount = 0
            lastSampleTimestamp = now
        }

        val hasNoDataOrError = !success ||
            responseRaw.contains("NO DATA", ignoreCase = true) ||
            responseRaw.contains("?") ||
            responseRaw.contains("UNABLE TO CONNECT", ignoreCase = true) ||
            responseRaw.contains("BUFFER FULL", ignoreCase = true) ||
            responseRaw.contains("ERROR", ignoreCase = true)

        if (command == BATCHED_TIER_1_CMD && hasNoDataOrError) {
            isBatchedTier1Supported = false
        } else if (command == BATCHED_TIER_2_CMD && hasNoDataOrError) {
            isBatchedTier2Supported = false
        }

        if (!hasNoDataOrError) {
            consecutiveErrors = 0
            // Exponential moving average (alpha = 0.15)
            smoothedRttMs = (smoothedRttMs * 0.85f) + (elapsedMs.toFloat() * 0.15f)
        } else {
            consecutiveErrors++
            smoothedRttMs = max(smoothedRttMs, elapsedMs.toFloat())
            findPid(command)?.let {
                it.failureCount++
                if (it.failureCount >= 3) {
                    it.isSupported = false
                }
            }
        }
    }

    /**
     * Decodes combined Tier 1 multi-PID response (01 0C 0D 11 04).
     * Handles standard ISO 15765-4 concatenated response frames (41 0C xx xx 0D xx 11 xx 04 xx)
     * as well as multi-line responses emitted by ELM327 adapters.
     */
    fun decodeBatchedTier1(response: String): BatchedTier1Result {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        var rpm: Int? = null
        var speed: Int? = null
        var throttle: Float? = null
        var load: Float? = null

        // Pattern 1: Check for explicit individual 41xx tokens
        val idx410c = clean.indexOf("410C")
        if (idx410c != -1 && clean.length >= idx410c + 8) {
            val a = clean.substring(idx410c + 4, idx410c + 6).toIntOrNull(16)
            val b = clean.substring(idx410c + 6, idx410c + 8).toIntOrNull(16)
            if (a != null && b != null) rpm = ((a * 256) + b) / 4
        }

        val idx410d = clean.indexOf("410D")
        if (idx410d != -1 && clean.length >= idx410d + 6) {
            speed = clean.substring(idx410d + 4, idx410d + 6).toIntOrNull(16)
        }

        val idx4111 = clean.indexOf("4111")
        if (idx4111 != -1 && clean.length >= idx4111 + 6) {
            val a = clean.substring(idx4111 + 4, idx4111 + 6).toIntOrNull(16)
            if (a != null) throttle = (a * 100f) / 255f
        }

        val idx4104 = clean.indexOf("4104")
        if (idx4104 != -1 && clean.length >= idx4104 + 6) {
            val a = clean.substring(idx4104 + 4, idx4104 + 6).toIntOrNull(16)
            if (a != null) load = (a * 100f) / 255f
        }

        // Pattern 2: Concatenated ISO-TP multi-PID payload (41 0C A B 0D C 11 D 04 E)
        if (idx410c != -1) {
            var cursor = idx410c + 8
            if (speed == null && clean.length >= cursor + 4 && clean.substring(cursor, cursor + 2) == "0D") {
                speed = clean.substring(cursor + 2, cursor + 4).toIntOrNull(16)
                cursor += 4
            }
            if (throttle == null && clean.length >= cursor + 4 && clean.substring(cursor, cursor + 2) == "11") {
                val a = clean.substring(cursor + 2, cursor + 4).toIntOrNull(16)
                if (a != null) throttle = (a * 100f) / 255f
                cursor += 4
            }
            if (load == null && clean.length >= cursor + 4 && clean.substring(cursor, cursor + 2) == "04") {
                val a = clean.substring(cursor + 2, cursor + 4).toIntOrNull(16)
                if (a != null) load = (a * 100f) / 255f
                cursor += 4
            }
        }

        return BatchedTier1Result(
            rpm = rpm,
            speedKmh = speed,
            throttlePct = throttle,
            engineLoadPct = load
        )
    }

    /**
     * Decodes combined Tier 2 multi-PID response (01 05 0F).
     */
    fun decodeBatchedTier2(response: String): BatchedTier2Result {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        var coolant: Int? = null
        var intake: Int? = null

        val idx4105 = clean.indexOf("4105")
        if (idx4105 != -1 && clean.length >= idx4105 + 6) {
            val a = clean.substring(idx4105 + 4, idx4105 + 6).toIntOrNull(16)
            if (a != null) coolant = a - 40
        }

        val idx410f = clean.indexOf("410F")
        if (idx410f != -1 && clean.length >= idx410f + 6) {
            val a = clean.substring(idx410f + 4, idx410f + 6).toIntOrNull(16)
            if (a != null) intake = a - 40
        }

        // Concatenated payload: 41 05 A 0F B
        if (idx4105 != -1 && intake == null) {
            val cursor = idx4105 + 6
            if (clean.length >= cursor + 4 && clean.substring(cursor, cursor + 2) == "0F") {
                val a = clean.substring(cursor + 2, cursor + 4).toIntOrNull(16)
                if (a != null) intake = a - 40
            }
        }

        return BatchedTier2Result(
            coolantTempC = coolant,
            intakeTempC = intake
        )
    }

    private fun findPid(command: String): ScheduledPid? {
        val all = tier1SinglePids + tier2SinglePids + tier3Pids + dynamicExtraPids
        return all.firstOrNull { it.command == command }
    }

    /**
     * Calculates the adaptive delay in milliseconds between ELM327 queries.
     * Prevents serial buffer overflow while achieving maximum telemetry throughput.
     */
    fun getAdaptiveDelayMs(): Long {
        if (consecutiveErrors >= 2) {
            return min(maxDelayMs, 50L + consecutiveErrors * 10L)
        }

        val targetCycleMs = (1000f / fastTargetHz).toLong()
        val calculatedDelay = targetCycleMs - smoothedRttMs.toLong()

        return when {
            smoothedRttMs > 120f -> 35L
            smoothedRttMs > 70f -> 20L
            else -> calculatedDelay.coerceIn(minDelayMs, maxDelayMs)
        }
    }

    fun getSmoothedRttMs(): Float = smoothedRttMs

    fun getEffectiveHz(): Float = currentCalculatedHz

    fun forceFallbackToSinglePids() {
        isBatchedTier1Supported = false
        isBatchedTier2Supported = false
    }

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
        lastTier2PolledAt = 0L
        lastTier3PolledAt = 0L
        lastAtrvPolledAt = 0L
        isBatchedTier1Supported = enableBatchedTier1
        isBatchedTier2Supported = enableBatchedTier2
        val all = tier1SinglePids + tier2SinglePids + tier3Pids + dynamicExtraPids
        for (item in all) {
            item.lastPolledAtMs = 0L
            item.failureCount = 0
            item.isSupported = true
        }
    }
}
