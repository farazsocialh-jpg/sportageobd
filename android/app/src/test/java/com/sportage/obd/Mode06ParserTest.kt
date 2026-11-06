package com.sportage.obd

import com.sportage.obd.obd.Mode06Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mode06ParserTest {

    @Test
    fun `parses a single unsigned result inside its window`() {
        // 46 | TID 01 | CID 00 | UAS 01 | value 0A | min 00 | max 14
        val results = Mode06Parser.parse("46 01 00 01 0A 00 14")

        assertEquals(1, results.size)
        val result = results.first()
        assertEquals(0x01, result.tid)
        assertEquals(0x00, result.cid)
        assertEquals(10.0, result.value, 0.001)
        assertEquals(0.0, result.minLimit, 0.001)
        assertEquals(20.0, result.maxLimit, 0.001)
        assertTrue(result.passed)
        assertEquals(1.0, result.margin, 0.001)
    }

    @Test
    fun `parses consecutive frames and flags one outside its window`() {
        val response = "46 01 00 01 0A 00 14" + "46 02 01 01 1E 00 14"

        val results = Mode06Parser.parse(response)

        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertFalse(results[1].passed)
        assertEquals(0.0, results[1].margin, 0.001)
    }

    @Test
    fun `decodes signed one byte values`() {
        // UAS 0x81 = signed, width 1. value -10 must sit inside [-16, 0].
        val results = Mode06Parser.parse("46 21 01 81 F6 F0 00")

        assertEquals(1, results.size)
        assertEquals(-10.0, results.first().value, 0.001)
        assertEquals(-16.0, results.first().minLimit, 0.001)
        assertEquals(0.0, results.first().maxLimit, 0.001)
        assertTrue(results.first().passed)
    }

    @Test
    fun `decodes two byte widths`() {
        // UAS 0x02: width 2. value 0x0100 = 256, min 0x0000, max 0x0200 = 512.
        val results = Mode06Parser.parse("46 05 02 02 01 00 00 00 02 00")

        assertEquals(1, results.size)
        assertEquals(256.0, results.first().value, 0.001)
        assertEquals(512.0, results.first().maxLimit, 0.001)
        assertTrue(results.first().passed)
    }

    @Test
    fun `truncated responses are ignored rather than half-parsed`() {
        assertTrue(Mode06Parser.parse("46 01 00 01 0A").isEmpty())
        assertTrue(Mode06Parser.parse("").isEmpty())
        assertTrue(Mode06Parser.parse("NO DATA").isEmpty())
        assertTrue(Mode06Parser.parse("7F 06 12").isEmpty())
    }

    @Test
    fun `marginal returns only results close to a limit`() {
        // Value 0x13 (19) inside 0..20 is 0.9 margin; value 0x14-1 .. construct a tight one.
        val healthy = Mode06Parser.parse("46 01 00 01 0A 00 14").first()
        // value 20, min 00, max 15 (21): 20 of 21 is close to the ceiling.
        val tight = Mode06Parser.parse("46 02 00 01 14 00 15").first()

        val marginal = Mode06Parser.marginal(listOf(healthy, tight))

        assertEquals(1, marginal.size)
        assertEquals(tight.tid, marginal.first().tid)
    }
}

class Mode06SupportedTidTest {

    @Test
    fun `decodes the supported test bitmap`() {
        // 46 00 then bitmap 0xC0 00 00 00: bit 7 -> TID 01, bit 6 -> TID 02.
        assertEquals(listOf(1, 2), Mode06Parser.parseSupportedTids("46 00 C0 00 00 00"))
    }

    @Test
    fun `tolerates ECUs that echo an extra three bytes`() {
        assertEquals(
            listOf(1, 2),
            Mode06Parser.parseSupportedTids("46 00 00 00 C0 00 00 00")
        )
    }

    @Test
    fun `bitmap at the end of the byte range maps to the high TIDs`() {
        // 0x00 0x00 0x00 0x01 -> bit 0 of byte 3 -> TID 32.
        assertEquals(listOf(32), Mode06Parser.parseSupportedTids("46 00 00 00 00 01"))
    }

    @Test
    fun `no data or a negative response yields no TIDs`() {
        assertTrue(Mode06Parser.parseSupportedTids("NO DATA").isEmpty())
        assertTrue(Mode06Parser.parseSupportedTids("7F 06 12").isEmpty())
        assertTrue(Mode06Parser.parseSupportedTids("").isEmpty())
    }
}

class Mode06MetadataTest {

    @Test
    fun `uas scale exponent becomes a decimal scale`() {
        // UAS 0x12: width 2, scale exponent 1 -> tenths.
        val result = Mode06Parser.parse("46 21 00 12 00 2A 00 00 00 37").first()
        assertEquals(0.1, result.scale, 0.0001)
        assertEquals(4.2, result.scaledValue, 0.0001)
        assertEquals(5.5, result.scaledMaxLimit, 0.0001)
    }

    @Test
    fun `common monitors get readable names and unknown ones stay raw`() {
        assertEquals("Misfire monitor · general", Mode06Parser.monitorLabel(0x01))
        assertEquals("Misfire monitor · cylinder 1", Mode06Parser.monitorLabel(0x02))
        assertEquals("Catalyst monitor · bank 1", Mode06Parser.monitorLabel(0x21))
        assertEquals("Monitor test 0x99", Mode06Parser.monitorLabel(0x99))
    }

    @Test
    fun `severity bands follow the remaining margin`() {
        val comfortable = Mode06Parser.parse("46 01 00 01 0A 00 14").first()
        // 18 of a 0..21 window sits 29% from the middle: still passing, visibly tightening.
        val tight = Mode06Parser.parse("46 02 00 01 12 00 15").first()
        val exhausted = Mode06Parser.parse("46 51 00 01 1E 00 14").first()

        assertEquals(0, comfortable.severity)
        assertEquals(1, tight.severity)
        assertEquals(2, exhausted.severity)
    }

    @Test
    fun `no data detection accepts the spaced ELM reply`() {
        assertTrue(Mode06Parser.isNoData("NO DATA"))
        assertTrue(Mode06Parser.isNoData("no data\r"))
        assertFalse(Mode06Parser.isNoData("46 01 00 01 0A 00 14"))
    }
}
