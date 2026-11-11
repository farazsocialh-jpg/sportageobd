package com.sportage.obd.ui

/**
 * Drive-mode detector for the cockpit package, reusing the existing [DriveMode] enum
 * (ECO / NORMAL / SPORT / CRITICAL) defined in this package.
 */
object DriveModeDetector {

    const val ENHANCED_HEADER = "7E1"
    const val ENHANCED_PID = "21 02"
    const val ENHANCED_REQUEST = "AT SH $ENHANCED_HEADER\n$ENHANCED_PID"

    private val MODE_BYTE_MAP = mapOf(
        0x01 to DriveMode.NORMAL,
        0x02 to DriveMode.ECO,
        0x03 to DriveMode.SPORT,
        0x00 to DriveMode.NORMAL
    )

    fun parseEnhancedResponse(raw: String?): DriveMode? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.uppercase().replace(" ", "").replace("\r", "").replace("\n", "")
        if (cleaned.contains("NODATA") || cleaned.contains("ERROR") || cleaned.contains("?")) return null
        when {
            cleaned.contains("ECO") -> return DriveMode.ECO
            cleaned.contains("SPORT") -> return DriveMode.SPORT
            cleaned.contains("NORMAL") -> return DriveMode.NORMAL
        }
        val bytes = cleaned.chunked(2).mapNotNull { it.toIntOrNull(16) }
        for (b in bytes) {
            MODE_BYTE_MAP[b]?.let { return it }
        }
        return null
    }

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

    fun detect(enhancedResponse: String?, throttlePercent: Double, rpm: Int): DriveMode =
        parseEnhancedResponse(enhancedResponse) ?: detect(throttlePercent, rpm)

    fun detect(enhancedResponse: String?, throttlePercent: Float, rpm: Int): DriveMode =
        detect(enhancedResponse, throttlePercent.toDouble(), rpm)
}