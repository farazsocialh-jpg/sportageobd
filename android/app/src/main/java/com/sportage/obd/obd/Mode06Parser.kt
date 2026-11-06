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

    /**
     * UAS bits 4-6 hold the scale exponent: a value of 2 means the reported integers are tenths.
     * Bits 0-3 give the width and bit 7 the signedness, both already applied by [Mode06Parser].
     */
    val scale: Double
        get() = Math.pow(10.0, -((unitAndScale shr 4) and 0x07).toDouble())

    val scaledValue: Double get() = value * scale
    val scaledMinLimit: Double get() = minLimit * scale
    val scaledMaxLimit: Double get() = maxLimit * scale

    /** 0..1 position inside the pass window; near 0 or 1 means the test is on the edge. */
    val margin: Double
        get() {
            val span = maxLimit - minLimit
            if (span <= 0.0) return if (passed) 1.0 else 0.0
            val middle = (minLimit + maxLimit) / 2.0
            return (1.0 - (kotlin.math.abs(value - middle) / (span / 2.0))).coerceIn(0.0, 1.0)
        }

    val label: String get() = "TID 0x%02X · CID 0x%02X".format(tid, cid)

    /** Human name of the monitor, falling back to the raw TID when it is not a common one. */
    val monitor: String get() = Mode06Parser.monitorLabel(tid)

    /** Colour band used by the UI: 0 = comfortable, 1 = tightening, 2 = exhausted/failing. */
    val severity: Int
        get() = when {
            !passed || margin < 0.15 -> 2
            margin < 0.45 -> 1
            else -> 0
        }
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
     * Test IDs worth probing when the ECU does not answer the `06 00` supported-test bitmap.
     * Deliberately short: each entry costs one round trip, and on a vehicle without Mode 06 the
     * negatives are cached so they are only ever asked once.
     */
    val COMMON_TEST_IDS: List<Int> = listOf(0x01, 0x02, 0x21, 0x22, 0x31, 0x41, 0x42, 0x51, 0x52)

    /**
     * Names for the monitor test IDs that are stable across manufacturers. Anything outside this
     * list is shown as a raw TID instead of a guessed name, because the rest of the J1979 table is
     * sparse and varies by emissions standard.
     */
    fun monitorLabel(tid: Int): String = when (tid) {
        0x01 -> "Misfire monitor · general"
        0x21 -> "Catalyst monitor · bank 1"
        0x22 -> "Catalyst monitor · bank 2"
        0x31 -> "EGR / VVT monitor · bank 1"
        0x41 -> "O2 sensor monitor · bank 1 sensor 1"
        0x42 -> "O2 sensor monitor · bank 1 sensor 2"
        0x51 -> "O2 heater monitor · bank 1 sensor 1"
        0x52 -> "O2 heater monitor · bank 1 sensor 2"
        in 0x02..0x0B -> "Misfire monitor · cylinder ${tid - 0x01}"
        else -> "Monitor test 0x%02X".format(tid)
    }

    /** True when the adapter answered that the ECU has nothing to say for this request. */
    fun isNoData(response: String): Boolean =
        response.uppercase().replace(" ", "").replace("\n", "").contains("NODATA")

    /**
     * Decodes the `06 00` supported-test bitmap into TIDs (bit 7 of byte 0 is TID 0x01).
     *
     * ECUs disagree on how much of the frame they echo — some answer `46 00 <4 bytes>`, others
     * `46 00 00 00 <4 bytes>` — so the bitmap is taken from the tail of the payload either way.
     * An unusable answer simply yields an empty list and the caller falls back to [COMMON_TEST_IDS].
     */
    fun parseSupportedTids(response: String): List<Int> {
        val clean = UdsDecoding.normalize(response)
        val marker = clean.indexOf("4600")
        if (marker < 0) return emptyList()
        val payload = clean.substring(marker + 4)
        if (payload.length < 8) return emptyList()
        val bitmap = payload.takeLast(8)
        val tids = ArrayList<Int>()
        for (index in 0 until 32) {
            val offset = index / 8 * 2
            val byte = bitmap.substring(offset, offset + 2).toIntOrNull(16) ?: return tids
            val mask = 0x80 shr (index % 8)
            if (byte and mask != 0) tids.add(index + 1)
        }
        return tids
    }

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
