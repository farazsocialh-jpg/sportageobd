package com.sportage.obd.obd

data class DtcItem(
    val code: String,
    val system: String,
    val description: String,
    val severity: DtcSeverity,
    val probableCause: String
)

enum class DtcSeverity {
    CRITICAL,
    WARNING,
    ADVISORY
}

object PidDecoders {

    // Common standard OBD-II diagnostic fault code definitions
    private val DTC_DATABASE = mapOf(
        "P0300" to Triple("Random/Multiple Cylinder Misfire Detected", DtcSeverity.CRITICAL, "Faulty spark plugs, ignition coils, low fuel pressure"),
        "P0301" to Triple("Cylinder 1 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 1 spark plug, ignition coil, fuel injector"),
        "P0302" to Triple("Cylinder 2 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 2 spark plug, ignition coil, fuel injector"),
        "P0303" to Triple("Cylinder 3 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 3 spark plug, ignition coil, fuel injector"),
        "P0304" to Triple("Cylinder 4 Misfire Detected", DtcSeverity.CRITICAL, "Cylinder 4 spark plug, ignition coil, fuel injector"),
        "P0171" to Triple("System Too Lean (Bank 1)", DtcSeverity.WARNING, "Vacuum leak, dirty MAF sensor, weak fuel pump"),
        "P0172" to Triple("System Too Rich (Bank 1)", DtcSeverity.WARNING, "Leaking fuel injector, stuck fuel pressure regulator"),
        "P0420" to Triple("Catalyst System Efficiency Below Threshold (Bank 1)", DtcSeverity.WARNING, "Aging catalytic converter, exhaust leak, oxygen sensor"),
        "P0128" to Triple("Coolant Thermostat Malfunction", DtcSeverity.WARNING, "Thermostat stuck open, coolant temperature sensor"),
        "P0133" to Triple("O2 Sensor Circuit Slow Response (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Contaminated heated oxygen sensor, exhaust leak"),
        "P0135" to Triple("O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 1)", DtcSeverity.WARNING, "Blown O2 heater fuse, damaged wiring harness"),
        "P0101" to Triple("Mass Air Flow (MAF) Circuit Range/Performance", DtcSeverity.WARNING, "Dirty MAF sensor wire, intake boot air leak"),
        "P0113" to Triple("Intake Air Temperature Sensor 1 Circuit High", DtcSeverity.ADVISORY, "IAT sensor unplugged, faulty wiring"),
        "P0500" to Triple("Vehicle Speed Sensor 'A' Malfunction", DtcSeverity.WARNING, "VSS sensor failure, instrument cluster wiring"),
        "P0700" to Triple("Transmission Control System (MIL Request)", DtcSeverity.WARNING, "TCM detected fault in automatic transmission")
    )

    /**
     * Engine RPM (PID 010C): ((A*256)+B)/4
     */
    fun decodeRpm(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410C")
        if (idx != -1 && clean.length >= idx + 8) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            return ((a * 256) + b) / 4
        }
        return null
    }

    /**
     * Vehicle Speed (PID 010D): A km/h
     */
    fun decodeSpeed(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410D")
        if (idx != -1 && clean.length >= idx + 6) {
            return clean.substring(idx + 4, idx + 6).toInt(16)
        }
        return null
    }

    /**
     * Engine Coolant Temperature (PID 0105): A - 40 °C
     */
    fun decodeCoolantTemp(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4105")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return a - 40
        }
        return null
    }

    /**
     * Intake Air Temperature (PID 010F): A - 40 °C
     */
    fun decodeIntakeTemp(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("410F")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return a - 40
        }
        return null
    }

    /**
     * Fuel Tank Level (PID 012F): (A*100)/255 %
     */
    fun decodeFuelLevel(response: String): Int? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("412F")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return ((a * 100) / 255)
        }
        return null
    }

    /**
     * Throttle Position (PID 0111): (A*100)/255 %
     */
    fun decodeThrottle(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4111")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return (a * 100f) / 255f
        }
        return null
    }

    /**
     * Calculated Engine Load (PID 0104): (A*100)/255 %
     */
    fun decodeLoad(response: String): Float? {
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "")
        val idx = clean.indexOf("4104")
        if (idx != -1 && clean.length >= idx + 6) {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            return (a * 100f) / 255f
        }
        return null
    }

    /**
     * Parse battery voltage from ATRV response
     */
    fun decodeBatteryVoltage(response: String): Float? {
        val match = Regex("""([\d\.]+)\s*V""", RegexOption.IGNORE_CASE).find(response)
        return match?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Decodes Mode 03 (Stored) or Mode 07 (Pending) Diagnostic Trouble Codes
     * Response standard format: "43 01 33 00 00 ..." or "47 ..."
     */
    fun decodeDtcCodes(response: String): List<DtcItem> {
        val dtcs = mutableListOf<DtcItem>()
        val clean = response.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()

        // Look for 43 or 47 response prefix
        val modeIdx = when {
            clean.contains("43") -> clean.indexOf("43") + 2
            clean.contains("47") -> clean.indexOf("47") + 2
            else -> -1
        }

        if (modeIdx == -1 || clean.length < modeIdx + 4) {
            return emptyList()
        }

        val codePayload = clean.substring(modeIdx)
        var i = 0
        while (i + 4 <= codePayload.length) {
            val chunk = codePayload.substring(i, i + 4)
            if (chunk != "0000") {
                val byte1 = chunk.substring(0, 2).toIntOrNull(16) ?: 0
                val byte2 = chunk.substring(2, 4)

                val prefix = when ((byte1 and 0xC0) shr 6) {
                    0 -> "P" // Powertrain
                    1 -> "C" // Chassis
                    2 -> "B" // Body
                    else -> "U" // Network
                }

                val digit2 = ((byte1 and 0x30) shr 4).toString()
                val digit3 = (byte1 and 0x0F).toString(16).uppercase()
                val code = "$prefix$digit2$digit3$byte2"

                val info = DTC_DATABASE[code] ?: Triple(
                    "Standard Diagnostic Trouble Code ($code)",
                    DtcSeverity.WARNING,
                    "General ECU fault flag. Inspect system sensors and connections."
                )

                dtcs.add(
                    DtcItem(
                        code = code,
                        system = when (prefix) {
                            "P" -> "Powertrain / Engine"
                            "C" -> "Chassis / ABS / AWD"
                            "B" -> "Body & Lighting"
                            else -> "CAN Network Bus"
                        },
                        description = info.first,
                        severity = info.second,
                        probableCause = info.third
                    )
                )
            }
            i += 4
        }
        return dtcs
    }
}
