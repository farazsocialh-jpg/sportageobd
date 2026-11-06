package com.sportage.obd

import com.sportage.obd.obd.HealthGrade
import com.sportage.obd.obd.HealthInput
import com.sportage.obd.obd.HealthScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthScoreTest {

    private val healthy = HealthInput(
        coolantTempC = 90,
        batteryVoltage = 13.9f,
        shortTermFuelTrimPct = 1.5f,
        longTermFuelTrimPct = 2.0f,
        engineLoadPct = 30f
    )

    @Test
    fun `a healthy warm engine scores 100 and needs no service`() {
        val report = HealthScorer.score(healthy)

        assertEquals(100, report.score)
        assertEquals(HealthGrade.EXCELLENT, report.grade)
        assertTrue(report.issues.isEmpty())
        assertEquals("Vehicle healthy", report.headline)
    }

    @Test
    fun `check engine light costs points and is explained`() {
        val report = HealthScorer.score(healthy.copy(milOn = true))

        assertEquals(88, report.score)
        assertEquals(HealthGrade.EXCELLENT, report.grade)
        assertTrue(report.issues.any { it.points == 12 && it.reason.contains("Check-engine") })
    }

    @Test
    fun `critical codes are capped so one repeated fault cannot zero the score`() {
        val report = HealthScorer.score(healthy.copy(criticalDtcCount = 9))

        // cap is 30, so the score is 70 rather than 100 - 90.
        assertEquals(30, report.issues.sumOf { it.points })
        assertEquals(70, report.score)
        assertEquals(HealthGrade.GOOD, report.grade)
    }

    @Test
    fun `low battery voltage is penalised in two bands`() {
        val low = HealthScorer.score(healthy.copy(batteryVoltage = 12.0f))
        val critical = HealthScorer.score(healthy.copy(batteryVoltage = 11.5f))

        assertEquals(90, low.score)
        assertEquals(82, critical.score)
        assertTrue(critical.issues.any { it.reason.contains("very low") })
    }

    @Test
    fun `overheating is the largest single sensor penalty`() {
        val report = HealthScorer.score(healthy.copy(coolantTempC = 112))

        assertEquals(88, report.score)
        assertTrue(report.issues.any { it.reason.contains("Coolant temperature high") })
    }

    @Test
    fun `coolant that never warms up is flagged`() {
        val report = HealthScorer.score(healthy.copy(coolantTempC = 40))

        assertEquals(96, report.score)
        assertTrue(report.issues.any { it.reason.contains("not reaching temperature") })
    }

    @Test
    fun `fuel trims are graded by magnitude`() {
        val drifting = HealthScorer.score(healthy.copy(longTermFuelTrimPct = 12f))
        val farOff = HealthScorer.score(healthy.copy(longTermFuelTrimPct = -22f))

        assertEquals(95, drifting.score)
        assertEquals(90, farOff.score)
    }

    @Test
    fun `incomplete readiness monitors are penalised but capped`() {
        val report = HealthScorer.score(healthy.copy(incompleteMonitorCount = 7))

        assertEquals(8, report.issues.sumOf { it.points })
        assertEquals(92, report.score)
    }

    @Test
    fun `everything wrong at once summarises every deduction`() {
        val report = HealthScorer.score(
            HealthInput(
                coolantTempC = 120,
                batteryVoltage = 10.5f,
                shortTermFuelTrimPct = 40f,
                longTermFuelTrimPct = 30f,
                milOn = true,
                criticalDtcCount = 5,
                warningDtcCount = 5,
                incompleteMonitorCount = 9
            )
        )

        // 12 (MIL) + 30 (capped DTCs) + 18 + 12 + 6 + 10 + 8 = 96 points of deductions.
        assertEquals(4, report.score)
        assertEquals(HealthGrade.POOR, report.grade)
        assertTrue(report.issues.isNotEmpty())
        // Issues are ordered worst-first so the UI can show one line.
        assertEquals(report.issues.maxOf { it.points }, report.issues.first().points)
    }

    @Test
    fun `missing sensors do not count against the vehicle`() {
        val report = HealthScorer.score(HealthInput())

        assertEquals(100, report.score)
    }
}
