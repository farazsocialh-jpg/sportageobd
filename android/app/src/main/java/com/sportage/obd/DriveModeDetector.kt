package com.sportage.obd

/**
 * High-level driving mode detected either from the transmission ECU's active shift map
 * (enhanced UDS service 0x21, PID 0x02, header 0x7E1) or, when that data is unavailable,
 * from a dynamic fallback based on throttle position and engine speed.
 */
enum class DriveMode(val label: String) {
    ECO("ECO"),
    NORMAL("NORMAL"),
    SPORT("SPORT");

    /** Ready-to-render badge, e.g. "[ ECO ]". */
    val badge: String get() = "[ $label ]"

    /** Dial accent color suggestion (kept lightweight so the enum stays Compose-free). */
    val accentHex: String get() = when (this) {
        ECO -> "#00E5FF"
        NORMAL -> "#FFB300"
        SPORT -> "#FF3D00"
    }
}

/**
 * Detects the active driving mode.
 *
 * Enhanced path: a UDS response from transmission ECU header `7E1`, PID `21 02`. When the
 * response carries no usable data the detector falls back to the dynamic heuristic.
 */
object DriveModeDetector {

    /** Enhanced UDS request header used to query the transmission shift map. */
    const val ENHANCED_HEADER = "7E1"

    /** Enhanced UDS request payload (mode 21, PID 02). */
    const val ENHANCED_PID = "21 02"

    /** Full enhanced request string, ready to send to the adapter. */
    const val ENHANCED_REQUEST = "AT SH $ENHANCED_HEADER\n$ENHANCED_PID"

    /** Mode byte -> DriveMode mapping used when the ECU answers the enhanced PID. */
    private val MODE_BYTE_MAP = mapOf(
        0x01 to DriveMode.NORMAL,
        0x02 to DriveMode.ECO,
        0x03 to DriveMode.SPORT,
        0x00 to DriveMode.NORMAL
    )

    /**
     * Parse an enhanced-ECU response. Returns null when no data is present so the caller
     * can fall back to the dynamic heuristic.
     */
    fun parseEnhancedResponse(raw: String?): DriveMode? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.uppercase().replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleaned.contains("NODATA") || cleaned.contains("ERROR") || cleaned.contains("?")) return null
        // Try an explicit textual label first.
        when {
            cleaned.contains("ECO") -> return DriveMode.ECO
            cleaned.contains("SPORT") -> return DriveMode.SPORT
            cleaned.contains("NORMAL") -> return DriveMode.NORMAL
        }
        // Otherwise interpret the first data byte after the header/length.
        val bytes = cleaned.chunked(2).mapNotNull { it.toIntOrNull(16) }
        for (b in bytes) {
            MODE_BYTE_MAP[b]?.let { return it }
        }
        return null
    }

    /** Dynamic fallback: throttle position (%) and engine speed (RPM). */
    fun detect(throttlePercent: Float, rpm: Int): DriveMode = detect(throttlePercent.toDouble(), rpm)

    fun detect(throttlePercent: Double, rpm: Int): DriveMode {
        val t = throttlePercent
        val r = rpm
        return when {
            t > 65.0 || r > 3800 -> DriveMode.SPORT
            t < 30.0 && r < 2400 -> DriveMode.ECO
            else -> DriveMode.NORMAL
        }
    }

    /** Convenience: enhanced first, dynamic fallback when enhanced returns no data. */
    fun detect(
        enhancedResponse: String?,
        throttlePercent: Double,
        rpm: Int
    ): DriveMode = parseEnhancedResponse(enhancedResponse) ?: detect(throttlePercent, rpm)

    fun detect(
        enhancedResponse: String?,
        throttlePercent: Float,
        rpm: Int
    ): DriveMode = detect(enhancedResponse, throttlePercent.toDouble(), rpm)
}