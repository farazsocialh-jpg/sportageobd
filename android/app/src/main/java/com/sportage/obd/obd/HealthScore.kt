package com.sportage.obd.obd

import kotlin.math.abs

/** Snapshot of everything the health model is allowed to look at. */
data class HealthInput(
    val coolantTempC: Int? = null,
    val batteryVoltage: Float? = null,
    val shortTermFuelTrimPct: Float? = null,
    val longTermFuelTrimPct: Float? = null,
    val engineLoadPct: Float? = null,
    val milOn: Boolean = false,
    val criticalDtcCount: Int = 0,
    val warningDtcCount: Int = 0,
    val incompleteMonitorCount: Int = 0
)

data class HealthIssue(val reason: String, val points: Int)

enum class HealthGrade { EXCELLENT, GOOD, FAIR, POOR }

data class HealthReport(
    val score: Int,
    val grade: HealthGrade,
    val issues: List<HealthIssue>
) {
    val headline: String
        get() = when (grade) {
            HealthGrade.EXCELLENT -> "Vehicle healthy"
            HealthGrade.GOOD -> "Minor attention needed"
            HealthGrade.FAIR -> "Service recommended"
            HealthGrade.POOR -> "Service now"
        }
}

/**
 * Deterministic, explainable health score.
 *
 * Every deduction is listed with its reason and weight so the driver can see *why* the number moved
 * instead of trusting a black box. The model is deliberately simple and bounded: it starts at 100
 * and subtracts for conditions that any OBD-II vehicle exposes, with per-category caps so a single
 * repeated fault cannot drive the score to zero on its own.
 *
 * It is **not** a substitute for a mechanic and is not a safety system.
 */
object HealthScorer {

    private const val MAX_DTC_PENALTY = 30
    private const val MAX_MONITOR_PENALTY = 8

    fun score(input: HealthInput): HealthReport {
        val issues = mutableListOf<HealthIssue>()

        if (input.milOn) {
            issues += HealthIssue("Check-engine light is on", 12)
        }

        val dtcPenalty = minOf(
            MAX_DTC_PENALTY,
            input.criticalDtcCount * 10 + input.warningDtcCount * 4
        )
        if (dtcPenalty > 0) {
            issues += HealthIssue(
                buildString {
                    append(input.criticalDtcCount).append(" critical / ")
                    append(input.warningDtcCount).append(" advisory stored code(s)")
                },
                dtcPenalty
            )
        }

        input.batteryVoltage?.let { volts ->
            when {
                volts < 11.8f -> issues += HealthIssue(
                    "Battery voltage very low (%.1f V)".format(volts), 18
                )
                volts < 12.2f -> issues += HealthIssue(
                    "Battery voltage low (%.1f V)".format(volts), 10
                )
            }
        }

        input.coolantTempC?.let { temp ->
            // A warm engine should sit in the thermostat's band; too cold wastes fuel and too hot
            // risks the head gasket. Values are only meaningful once the engine has run a while,
            // which the caller guarantees by only scoring warmed-up telemetry.
            if (temp > 105) issues += HealthIssue("Coolant temperature high (${temp}°C)", 12)
            else if (temp in 1..60) issues += HealthIssue("Coolant not reaching temperature (${temp}°C)", 4)
        }

        input.longTermFuelTrimPct?.let { trim ->
            if (abs(trim) > 15f) issues += HealthIssue(
                "Long-term fuel trim far from zero (%.0f%%)".format(trim), 10
            ) else if (abs(trim) > 10f) issues += HealthIssue(
                "Long-term fuel trim drifting (%.0f%%)".format(trim), 5
            )
        }

        input.shortTermFuelTrimPct?.let { trim ->
            if (abs(trim) > 20f) issues += HealthIssue(
                "Short-term fuel trim swinging (%.0f%%)".format(trim), 6
            )
        }

        if (input.incompleteMonitorCount > 0) {
            val points = minOf(MAX_MONITOR_PENALTY, input.incompleteMonitorCount * 2)
            issues += HealthIssue("${input.incompleteMonitorCount} readiness monitor(s) incomplete", points)
        }

        val score = (100 - issues.sumOf { it.points }).coerceIn(0, 100)
        return HealthReport(
            score = score,
            grade = when {
                score >= 85 -> HealthGrade.EXCELLENT
                score >= 70 -> HealthGrade.GOOD
                score >= 50 -> HealthGrade.FAIR
                else -> HealthGrade.POOR
            },
            issues = issues.sortedByDescending { it.points }
        )
    }
}
