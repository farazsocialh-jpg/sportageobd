package com.sportage.obd

import com.sportage.obd.obd.CustomPid
import com.sportage.obd.obd.ManufacturerPids
import com.sportage.obd.obd.PidFormula
import com.sportage.obd.obd.UdsDecoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extended PID decoding: UDS framing, J1979-2 DID mapping and the formula catalogue.
 *
 * Payloads below are hand-built from the documented formulas, so these tests pin the *maths*.
 * Whether a given vendor DID exists on a given car cannot be tested here — that needs an adapter.
 */
class ExtendedPidTest {

    @Test
    fun `normalize strips can headers line numbers and prompts`() {
        val raw = "7E8 04 62 F4 0C 1A F8\r\n0: 62 F4 0C 1A\r\n1: F8 00\r\n>"

        val clean = UdsDecoding.normalize(raw)

        assertTrue(clean.startsWith("7E80462F40C1AF8"))
        assertFalse(clean.contains(" "))
        assertFalse(clean.contains(">"))
    }

    @Test
    fun `payload returns bytes after the service and identifier echo`() {
        val data = UdsDecoding.payload("62 F4 0C 1A F8", 0x22, "F40C")

        assertEquals(listOf(0x1A, 0xF8), data?.toList())
    }

    @Test
    fun `payload is null for a negative response`() {
        assertNull(UdsDecoding.payload("7F 22 31", 0x22, "F40C"))
    }

    @Test
    fun `negative response detection distinguishes unsupported from other errors`() {
        assertTrue(UdsDecoding.isNegativeResponse("7F 22 31"))
        assertTrue(UdsDecoding.isUnsupported("7F 22 31"))
        assertFalse(UdsDecoding.isUnsupported("7F 22 78")) // responsePending
        assertFalse(UdsDecoding.isNegativeResponse("62 F4 0C 1A F8"))
    }

    @Test
    fun `mode 21 responses decode with the same payload helper`() {
        assertEquals(listOf(0x5F), UdsDecoding.payload("61 01 82 5F", 0x21, "0182")?.toList())
    }

    @Test
    fun `j1979_2 maps classic pids into the F400 did block`() {
        assertEquals("F40C", UdsDecoding.j1979_2Did(0x0C))
        assertEquals("F40D", UdsDecoding.j1979_2Did(0x0D))
        assertEquals("F400", UdsDecoding.j1979_2Did(0x00))
    }

    @Test
    fun `rpm via j1979_2 did matches the mode 01 formula`() {
        // 0x1AF8 = 6904, /4 = 1726 rpm
        val data = UdsDecoding.payload("62 F4 0C 1A F8", 0x22, UdsDecoding.j1979_2Did(0x0C))

        assertEquals(1726f, PidFormula.WORD_DIV4.apply(data!!)!!, 0.001f)
    }

    @Test
    fun `every formula handles short payloads without throwing`() {
        PidFormula.entries.forEach { formula ->
            assertNull("formula $formula returned a value with no bytes", formula.apply(IntArray(0)))
        }
        // Byte-only formulas must still work with a single byte.
        assertEquals(95f, PidFormula.RAW_A.apply(intArrayOf(95))!!, 0.001f)
        assertEquals(55f, PidFormula.RAW_A_MINUS_40.apply(intArrayOf(95))!!, 0.001f)
        assertNull(PidFormula.WORD_DIV10.apply(intArrayOf(95)))
    }

    @Test
    fun `signed word formula handles negative values`() {
        // 0xFF9C = -100, /10 = -10.0
        assertEquals(-10f, PidFormula.SIGNED_WORD_DIV10.apply(intArrayOf(0xFF, 0x9C))!!, 0.001f)
    }

    @Test
    fun `ford transmission temperature preset decodes its documented shape`() {
        val pid = ManufacturerPids.forBrand("Ford").first()

        // 0x0A2D = 2605 -> 260.5 - 40 = 220.5 °C (deliberately extreme, this is a maths check).
        val value = ManufacturerPids.decode(pid, "62 1E 1C 0A 2D")

        assertEquals(220.5f, value!!, 0.001f)
    }

    @Test
    fun `gm transmission temperature preset decodes a single byte`() {
        val pid = ManufacturerPids.forBrand("GM").first()

        assertEquals(55f, ManufacturerPids.decode(pid, "62 19 40 5F")!!, 0.001f)
    }

    @Test
    fun `manufacturer presets are not claimed as verified`() {
        // The UI relies on this flag to label them "experimental"; flipping it without a real
        // vehicle validation would mislead the driver.
        assertTrue(ManufacturerPids.presets.isNotEmpty())
        assertTrue(ManufacturerPids.presets.none { it.verified })
    }

    @Test
    fun `unsupported did yields null instead of a bogus number`() {
        val pid = ManufacturerPids.forBrand("BMW").first()

        assertNull(ManufacturerPids.decode(pid, "7F 22 31"))
        assertNull(ManufacturerPids.decode(pid, "NO DATA"))
    }

    @Test
    fun `custom pid builds the expected elm command and decodes`() {
        val pid = CustomPid(
            label = "Oil temperature",
            service = 0x22,
            did = "1311",
            formula = PidFormula.RAW_A_MINUS_40,
            unit = "°C"
        )

        assertEquals("221311", pid.command())
        assertTrue(pid.isValid)
        assertEquals(85f, pid.decode("62 13 11 7D")!!, 0.001f)
    }

    @Test
    fun `custom pid rejects invalid definitions`() {
        val invalid = CustomPid("", 0x22, "ZZZZ", PidFormula.RAW_A, "x")
        assertFalse(invalid.isValid)

        val wrongService = CustomPid("x", 0x10, "1311", PidFormula.RAW_A, "x")
        assertFalse(wrongService.isValid)
    }
}
