package com.sportage.obd.obd

/**
 * One on-board monitor test result from SAE J1979 Mode 06.
 *
 * Mode 06 is what the ECU actually measured while running its self-tests (misfire counters,
 * catalyst efficiency margins, O2 sensor response), which makes it far more useful than Mode 03 for
 * intermittent faults: a component can be "passing" today while its margin is almost exhausted.
 */
data class MonitorTestResult(
    val tid: Int,
    val cid: Int,
    val unitAndScale: Int,
    val value: Double,
    val minLimit: Double,
    val maxLimit: Double
) {
    /** Whether the reported value sits inside the ECU's own pass window. */
    val passed: Boolean get() = value in minLimit..maxLimit

    /** 0..1 position inside the pass window; near 0 or 1 means the test is on the edge. */
    val margin: Double
        get() {
            val span = maxLimit - minLimit
            if (span <= 0.0) return if (passed) 1.0 else 0.0
            val middle = (minLimit + maxLimit) / 2.0
            return (1.0 - (kotlin.math.abs(value - middle) / (span / 2.0))).coerceIn(0.0, 1.0)
        }

    val label: String get() = "TID 0x%02X · CID 0x%02X".format(tid, cid)
}

/**
 * Parser for Mode 06 responses.
 *
 * Frame layout (ISO 15765-4 / SAE J1979):
 * `46 <TID> <CID> <UAS> <test value…> <min limit…> <max limit…>`
 * where UAS bits 0-3 give the width in bytes of each of the three values and bit 7 marks them as
 * signed. Many ECUs return several frames in one response, or split a frame across CAN frames; the
 * parser walks the whole payload instead of assuming a single result.
 */
object Mode06Parser {

    /**
     * @return every well-formed test result in [response]. Empty when the ECU has no Mode 06 data,
     *         which is normal on vehicles that do not implement it.
     */
    fun parse(response: String): List<MonitorTestResult> {
        val clean = UdsDecoding.normalize(response)
        // Shortest valid frame: 46 TID CID UAS + one byte each for value/min/max = 7 bytes.
        if (clean.length < 14) return emptyList()

        val results = ArrayList<MonitorTestResult>()
        var search = 0
        while (search < clean.length - 8) {
            val idx = clean.indexOf("46", search)
            if (idx < 0) break
            search = idx + 2

            // A response frame starts at an even offset inside the response; odd offsets are
            // almost always a coincidental "46" inside a data byte.
            if (idx % 2 != 0) continue
            if (idx + 8 > clean.length) break

            val tid = clean.substring(idx + 2, idx + 4).toIntOrNull(16) ?: continue
            val cid = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: continue
            val uas = clean.substring(idx + 6, idx + 8).toIntOrNull(16) ?: continue

            val width = (uas and 0x0F).let { if (it == 0) 1 else it }
            if (width > 4) continue
            val signed = (uas and 0x80) != 0
            val needed = 8 + width * 6
            if (idx + needed > clean.length) continue

            // Hex character offsets: one data byte is two characters.
            val value = readValue(clean, idx + 8, width, signed) ?: continue
            val min = readValue(clean, idx + 8 + width * 2, width, signed) ?: continue
            val max = readValue(clean, idx + 8 + width * 4, width, signed) ?: continue

            results.add(MonitorTestResult(tid, cid, uas, value, min, max))
            search = idx + needed
        }
        return results
    }

    private fun readValue(hex: String, offset: Int, width: Int, signed: Boolean): Double? {
        if (offset + width * 2 > hex.length) return null
        var raw = 0L
        for (i in 0 until width) {
            val byte = hex.substring(offset + i * 2, offset + i * 2 + 2).toIntOrNull(16) ?: return null
            raw = (raw shl 8) or byte.toLong()
        }
        if (signed) {
            val bits = width * 8
            val signBit = 1L shl (bits - 1)
            if (raw and signBit != 0L) raw -= (1L shl bits)
        }
        return raw.toDouble()
    }

    /** Convenience for the UI: only the results whose margin is nearly exhausted. */
    fun marginal(results: List<MonitorTestResult>, threshold: Double = 0.15): List<MonitorTestResult> =
        results.filter { it.margin < threshold }
}
