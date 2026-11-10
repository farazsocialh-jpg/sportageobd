package com.sportage.obd

import com.sportage.obd.service.PollingTier
import com.sportage.obd.service.PriorityPidScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityPidSchedulerTest {

    @Test
    fun scheduler_initial_queries_prioritize_tier1_powertrain() {
        val scheduler = PriorityPidScheduler()
        val supported = setOf("0C", "0D", "11", "04", "05", "0F", "2F")

        // First queries start with Tier 1
        val cmd1 = scheduler.nextCommand(1000L, supported)
        assertEquals(PollingTier.TIER_1_HIGH, cmd1.tier)
        assertEquals("010C", cmd1.command)

        val cmd2 = scheduler.nextCommand(1050L, supported)
        assertEquals(PollingTier.TIER_1_HIGH, cmd2.tier)
        assertEquals("010D", cmd2.command)
    }

    @Test
    fun scheduler_interleaves_tier2_thermal_and_trim_sensors() {
        val scheduler = PriorityPidScheduler()
        val supported = setOf("0C", "0D", "11", "04", "05", "0F", "2F")

        // 2 Tier 1 commands
        scheduler.nextCommand(1000L, supported)
        scheduler.nextCommand(1050L, supported)

        // After 2 Tier 1 commands, next due Tier 2 command is interleaved
        val auxCmd = scheduler.nextCommand(1100L, supported)
        assertEquals(PollingTier.TIER_2_MEDIUM, auxCmd.tier)
        assertEquals("0105", auxCmd.command)
    }

    @Test
    fun scheduler_schedules_tier3_low_priority_at_30s_interval() {
        val scheduler = PriorityPidScheduler()
        val supported = setOf("0C", "0D", "11", "04", "05", "2F")

        // Advance time to 35 seconds
        val cmd = scheduler.nextCommand(35_000L, supported)
        // Tier 3 is due
        assertEquals(PollingTier.TIER_3_LOW, cmd.tier)
        assertEquals("012F", cmd.command)
    }

    @Test
    fun scheduler_calculates_adaptive_delay_based_on_response_time() {
        val scheduler = PriorityPidScheduler(fastTargetHz = 20f, minDelayMs = 8L, maxDelayMs = 80L)

        // Record fast responses (20ms RTT)
        repeat(10) {
            scheduler.recordResponseTime("010C", 20L, success = true)
        }
        val fastDelay = scheduler.getAdaptiveDelayMs()
        assertTrue("Fast delay should be within reasonable bounds: $fastDelay", fastDelay in 8L..40L)

        // Record slow responses (>120ms RTT on cheap clone adapter)
        repeat(25) {
            scheduler.recordResponseTime("010C", 140L, success = true)
        }
        val slowDelay = scheduler.getAdaptiveDelayMs()
        assertEquals(35L, slowDelay)
    }

    @Test
    fun scheduler_backs_off_delay_on_consecutive_errors() {
        val scheduler = PriorityPidScheduler(minDelayMs = 8L, maxDelayMs = 80L)

        // Report 3 consecutive failures
        scheduler.recordResponseTime("010C", 50L, success = false)
        scheduler.recordResponseTime("010C", 50L, success = false)
        scheduler.recordResponseTime("010C", 50L, success = false)

        val errorDelay = scheduler.getAdaptiveDelayMs()
        assertTrue("Delay must scale up on repeated UART/bus errors: $errorDelay", errorDelay >= 50L)
    }

    @Test
    fun scheduler_skips_unsupported_pids() {
        val scheduler = PriorityPidScheduler()
        // Only RPM, Speed, and Coolant are supported by this ECU
        val supported = setOf("0C", "0D", "05")

        // First query
        val q1 = scheduler.nextCommand(1000L, supported)
        assertEquals("010C", q1.command)

        val q2 = scheduler.nextCommand(1050L, supported)
        assertEquals("010D", q2.command)

        // Tier 2 should only pick 05 because 0F, 10, etc. are not supported
        val q3 = scheduler.nextCommand(1100L, supported)
        assertEquals("0105", q3.command)
    }

    @Test
    fun scheduler_blacklists_pid_after_three_failures() {
        val scheduler = PriorityPidScheduler()
        val supported = setOf("0C", "0D", "11", "04")

        // Fail throttle position 3 times
        scheduler.recordResponseTime("0111", 100L, success = false)
        scheduler.recordResponseTime("0111", 100L, success = false)
        scheduler.recordResponseTime("0111", 100L, success = false)

        // Verify that throttle is skipped in subsequent Tier 1 cycles
        val executedCommands = mutableListOf<String>()
        repeat(10) { idx ->
            executedCommands.add(scheduler.nextCommand(2000L + idx * 50L, supported).command)
        }
        assertFalse("Blacklisted command 0111 must not be scheduled", executedCommands.contains("0111"))
    }
}
