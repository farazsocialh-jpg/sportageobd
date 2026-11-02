package com.sportage.obd.obd

object PidDecoders {
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
}
