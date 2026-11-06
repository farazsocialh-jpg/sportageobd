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
