package com.sportage.obd.obd

/** Result of decoding Mode 01 PID 01 (Current DTCs Readiness). */
data class ReadinessState(
    /** True if the Malfunction Indicator Lamp (MIL) is on. */
    val milOn: Boolean,
    /** Number of stored DTCs that triggered the MIL. */
    val storedDtcCount: Int = 0,
    /** Individual spark-ignition monitor completion status. */
    val sparkIgnitionMonitors: List<MonitorCompletion> = emptyList()
)

/** Completion status for a single spark-ignition monitor. */
data class MonitorCompletion(
    /** Human-readable monitor name, e.g. "Misfire", "Catalyst", "O2 Sensor". */
    val name: String,
    /** True when the monitor has completed its self-test. */
    val complete: Boolean
)