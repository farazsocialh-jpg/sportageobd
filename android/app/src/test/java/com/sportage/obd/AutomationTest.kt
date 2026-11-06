package com.sportage.obd

import com.sportage.obd.launcher.AutoPauseController
import com.sportage.obd.launcher.DriveModeController
import com.sportage.obd.launcher.NightDim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModeControllerTest {

    @Test
    fun `entry needs the speed to hold, not just touch the threshold`() {
        val controller = DriveModeController(enterKmh = 25, exitKmh = 8, enterHoldMs = 3_000)

        assertFalse(controller.update(30, 0))
        assertFalse(controller.update(30, 1_500))
        assertTrue(controller.update(30, 3_000))
    }

    @Test
    fun `a brief dip below the threshold resets the entry timer`() {
        val controller = DriveModeController(enterKmh = 25, enterHoldMs = 3_000)

        assertFalse(controller.update(30, 0))
        assertFalse(controller.update(10, 1_000))
        assertFalse(controller.update(30, 2_000))
        assertFalse(controller.update(30, 4_500))
        assertTrue(controller.update(30, 5_000))
    }

    @Test
    fun `traffic between the thresholds does not toggle the mode`() {
        val controller = DriveModeController(enterKmh = 25, exitKmh = 8, enterHoldMs = 0, exitHoldMs = 12_000)
        assertTrue(controller.update(30, 0))

        // Sitting at 15 km/h is below entry but above exit: the cockpit must stay as it is.
        assertTrue(controller.update(15, 5_000))
        assertTrue(controller.update(15, 40_000))
    }

    @Test
    fun `exit needs a stop that lasts`() {
        val controller = DriveModeController(enterKmh = 25, exitKmh = 8, enterHoldMs = 0, exitHoldMs = 12_000)
        assertTrue(controller.update(60, 0))

        assertTrue(controller.update(0, 10_000))
        assertFalse(controller.update(0, 22_000))
    }

    @Test
    fun `losing the adapter at speed keeps Drive Mode`() {
        val controller = DriveModeController(enterKmh = 25, enterHoldMs = 0, exitHoldMs = 1_000)
        assertTrue(controller.update(60, 0))
        assertTrue(controller.update(null, 30_000))
    }

    @Test
    fun `manual entry works at standstill and manual exit is immediate`() {
        val controller = DriveModeController()

        controller.enterManually()
        assertTrue(controller.update(0, 0))

        controller.exitManually()
        assertFalse(controller.update(0, 0))
    }

    @Test
    fun `automation off blocks auto entry but keeps a manual session alive`() {
        val controller = DriveModeController(enterKmh = 25, enterHoldMs = 0, exitHoldMs = 12_000)

        // Fast driving must not open Drive Mode when the driver disabled the rule.
        assertFalse(controller.update(90, 0, allowAutoEntry = false))
        assertFalse(controller.update(90, 5_000, allowAutoEntry = false))

        // A session opened by hand survives the same samples.
        controller.enterManually()
        assertTrue(controller.update(0, 6_000, allowAutoEntry = false))
        assertTrue(controller.update(90, 7_000, allowAutoEntry = false))
    }

    @Test
    fun `configuring keeps the exit band strictly below entry`() {
        val controller = DriveModeController()
        controller.configure(enter = 20, exit = 99)
        assertEquals(20, controller.enterKmh)
        assertTrue(controller.exitKmh < 20)
    }
}

class AutoPauseControllerTest {

    @Test
    fun `parked long enough asks for a pause`() {
        val controller = AutoPauseController(parkMinutes = 10)

        assertFalse(controller.update(0, 0))
        assertFalse(controller.update(0, 9 * 60_000L))
        assertTrue(controller.update(0, 10 * 60_000L))
    }

    @Test
    fun `moving resets the parked timer`() {
        val controller = AutoPauseController(parkMinutes = 5)

        assertFalse(controller.update(0, 0))
        assertFalse(controller.update(40, 2 * 60_000L))
        assertFalse(controller.update(0, 4 * 60_000L))
        assertFalse(controller.update(0, 6 * 60_000L))
        assertTrue(controller.update(0, 9 * 60_000L))
    }

    @Test
    fun `unknown speed never pauses a healthy session`() {
        val controller = AutoPauseController(parkMinutes = 1)

        assertFalse(controller.update(null, 0))
        assertFalse(controller.update(null, 10 * 60_000L))
    }

    @Test
    fun `a crawl still counts as parked`() {
        val controller = AutoPauseController(parkMinutes = 1)

        assertFalse(controller.update(AutoPauseController.PARKED_KMH, 0))
        assertTrue(controller.update(AutoPauseController.PARKED_KMH, 61_000L))
    }
}

class NightDimTest {

    @Test
    fun `dimming applies inside the night window and not outside it`() {
        assertEquals(0.55f, NightDim.levelFor(true, 0.55f, hourOfDay = 22)!!, 0.001f)
        assertEquals(0.55f, NightDim.levelFor(true, 0.55f, hourOfDay = 2)!!, 0.001f)
        assertEquals(0.8f, NightDim.levelFor(true, 0.55f, hourOfDay = 12)!!, 0.001f)
    }

    @Test
    fun `manual off means no dimming at all`() {
        assertNull(NightDim.levelFor(false, 0.55f, hourOfDay = 23))
    }

    @Test
    fun `daytime level cannot be pushed past full brightness`() {
        assertEquals(1f, NightDim.levelFor(true, 0.85f, hourOfDay = 12)!!, 0.001f)
    }
}
