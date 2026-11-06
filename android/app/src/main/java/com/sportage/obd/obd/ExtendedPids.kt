package com.sportage.obd.obd

import kotlin.math.roundToInt

/**
 * Extended (non-Mode-01) parameter support.
 *
 * Three mechanisms live here, in increasing order of vendor-specificity:
 *
 * 1. **SAE J1979-2 "OBDonUDS"** — a 2021+ standard that exposes the classic Mode 01 parameters
 *    through UDS `ReadDataByIdentifier` (service 0x22) using DID `0xF400 + PID`. This is
 *    standardised, so [j1979_2Did] is safe for any compliant vehicle.
 * 2. **Manufacturer Mode 22 / Mode 21 DIDs** — *not* standardised. The same physical quantity has a
 *    different DID on every platform and often on every model year. The presets in
 *    [ManufacturerPids] are therefore marked [ManufacturerPid.verified] = false and must be
 *    confirmed against the vehicle before being trusted; the app shows them as experimental.
 * 3. **User-defined PIDs** — the escape hatch that makes the feature useful: any DID with one of a
 *    fixed set of well-known SAE/J1979 decoding shapes ([PidFormula]).
 */
object UdsDecoding {

    /** Strips whitespace, `0:` line numbers, ELM `>` prompts and CAN headers into a hex string. */
    fun normalize(response: String): String {
        val joined = response.lineSequence()
            .map { it.replace(Regex("^\\s*[0-9A-Fa-f]\\s*:\\s*"), "") }
            .joinToString("")
        return joined.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
    }

    /**
     * Returns the data bytes that follow a positive UDS response.
     *
     * @param service request service id (e.g. 0x22), the response is service + 0x40 (0x62).
     * @param idBytes identifier bytes echoed by the ECU (DID for 0x22, PID for 0x21), hex string.
     */
    fun payload(response: String, service: Int, idBytes: String): IntArray? {
        val clean = normalize(response)
        val marker = String.format("%02X", service + 0x40) + idBytes.uppercase()
        val idx = clean.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        if (clean.length < start + 2) return null
        val out = ArrayList<Int>()
        var i = start
        while (i + 2 <= clean.length) {
            val b = clean.substring(i, i + 2).toIntOrNull(16) ?: return out.toIntArray()
            out.add(b)
            i += 2
        }
        return out.toIntArray()
    }

    /** J1979-2: DID for a legacy Mode 01 PID. Only the 0xF400..0xF4FF block is standardised. */
    fun j1979_2Did(pid: Int): String = String.format("F4%02X", pid and 0xFF)

    /** True when the ECU answered with the UDS negative-response service (0x7F). */
    fun isNegativeResponse(response: String): Boolean = normalize(response).contains("7F")

    /** UDS "requestOutOfRange" — the ECU does not implement this DID. */
    fun isUnsupported(response: String): Boolean {
        val clean = normalize(response)
        val idx = clean.indexOf("7F")
        if (idx < 0 || clean.length < idx + 6) return false
        val nrc = clean.substring(idx + 4, idx + 6).toIntOrNull(16) ?: return false
        return nrc == 0x31 || nrc == 0x11 || nrc == 0x12
    }

    /** Big-endian unsigned word from two bytes. */
    fun word(hi: Int, lo: Int): Int = ((hi and 0xFF) shl 8) or (lo and 0xFF)

    /** Big-endian signed 16-bit value. */
    fun signedWord(hi: Int, lo: Int): Int {
        val raw = word(hi, lo)
        return if (raw >= 0x8000) raw - 0x10000 else raw
    }
}

/**
 * Decoding shapes that cover the overwhelming majority of published manufacturer PIDs.
 * Keeping this a closed enum (instead of evaluating user formulas) means no expression parser and
 * no code-evaluation surface, which matters on a device that can be handed to a passenger.
 */
enum class PidFormula(val label: String, val example: String) {
    RAW_A("Raw byte A", "A"),
    RAW_WORD("Unsigned word (A*256+B)", "(A*256)+B"),
    PERCENT_A("Percentage (A*100/255)", "A*100/255"),
    RAW_A_MINUS_40("Byte A - 40 (temperature)", "A-40"),
    WORD_DIV10_MINUS_40("Word/10 - 40 (temp x10)", "(A*256+B)/10-40"),
    WORD_DIV100("Word/100 (pressure/fuel)", "(A*256+B)/100"),
    WORD_DIV10("Word/10", "(A*256+B)/10"),
    WORD_DIV4("Word/4 (RPM style)", "(A*256+B)/4"),
    SIGNED_WORD_DIV10("Signed word / 10", "s(A*256+B)/10"),
    A_DIV2_MINUS_64("Advance: A/2 - 64", "A/2-64");

    /** @param bytes payload after the service/DID echo; may be longer than the formula needs. */
    fun apply(bytes: IntArray): Float? {
        if (bytes.isEmpty()) return null
        val a = bytes[0] and 0xFF
        val b = if (bytes.size > 1) bytes[1] and 0xFF else null
        return when (this) {
            RAW_A -> a.toFloat()
            RAW_WORD -> if (b == null) null else UdsDecoding.word(a, b).toFloat()
            PERCENT_A -> a * 100f / 255f
            RAW_A_MINUS_40 -> (a - 40).toFloat()
            WORD_DIV10_MINUS_40 -> if (b == null) null else UdsDecoding.word(a, b) / 10f - 40f
            WORD_DIV100 -> if (b == null) null else UdsDecoding.word(a, b) / 100f
            WORD_DIV10 -> if (b == null) null else UdsDecoding.word(a, b) / 10f
            WORD_DIV4 -> if (b == null) null else UdsDecoding.word(a, b) / 4f
            SIGNED_WORD_DIV10 -> if (b == null) null else UdsDecoding.signedWord(a, b) / 10f
            A_DIV2_MINUS_64 -> a / 2f - 64f
        }
    }
}

/** A vendor-specific parameter definition. */
data class ManufacturerPid(
    val brand: String,
    val label: String,
    val service: Int,
    val did: String,
    val formula: PidFormula,
    val unit: String,
    /**
     * Whether the DID has been confirmed on a real vehicle by this project. Presets are shipped
     * with `false` and surfaced in the UI as "experimental" until somebody validates them.
     */
    val verified: Boolean = false,
    val note: String = ""
)

/**
 * Curated starting points for manufacturer-specific data. These identifiers come from publicly
 * circulated OBD PID lists and legitimately differ between model years, engines and modules, so
 * treat every entry as a hypothesis to confirm with a real adapter, not as documentation.
 */
object ManufacturerPids {

    val presets: List<ManufacturerPid> = listOf(
        ManufacturerPid(
            brand = "Ford",
            label = "Transmission fluid temperature",
            service = 0x22,
            did = "1E1C",
            formula = PidFormula.WORD_DIV10_MINUS_40,
            unit = "°C",
            note = "Common on Ford 6F/6R automatics; some modules answer on DID 1E1C only when warm."
        ),
        ManufacturerPid(
            brand = "GM",
            label = "Transmission fluid temperature",
            service = 0x22,
            did = "1940",
            formula = PidFormula.RAW_A_MINUS_40,
            unit = "°C",
            note = "Widely reported for GM 6T/8L transmissions."
        ),
        ManufacturerPid(
            brand = "Toyota",
            label = "CVT/AT fluid temperature",
            service = 0x21,
            did = "0182",
            formula = PidFormula.RAW_A_MINUS_40,
            unit = "°C",
            note = "Toyota/Lexus often answer on Mode 21 rather than Mode 22; verify the DID per model."
        ),
        ManufacturerPid(
            brand = "VAG",
            label = "Engine oil temperature",
            service = 0x22,
            did = "1311",
            formula = PidFormula.RAW_A_MINUS_40,
            unit = "°C",
            note = "VW/Audi/Škoda/SEAT; EA888-era ECUs commonly expose oil temp here."
        ),
        ManufacturerPid(
            brand = "BMW",
            label = "Engine oil temperature",
            service = 0x22,
            did = "4405",
            formula = PidFormula.RAW_A_MINUS_40,
            unit = "°C",
            note = "Reported for N20/N55/B58 engines; older MSV70 cars use a different DID."
        )
    )

    fun forBrand(brand: String): List<ManufacturerPid> =
        presets.filter { it.brand.equals(brand, ignoreCase = true) }

    fun brands(): List<String> = presets.map { it.brand }.distinct()

    /** Decodes a raw adapter response for [pid]; null when the ECU did not answer positively. */
    fun decode(pid: ManufacturerPid, response: String): Float? {
        if (UdsDecoding.isNegativeResponse(response)) return null
        val idBytes = if (pid.service == 0x22) pid.did else pid.did
        val data = UdsDecoding.payload(response, pid.service, idBytes) ?: return null
        return pid.formula.apply(data)
    }
}

/** A parameter the user added themselves in Settings → Custom PIDs. */
data class CustomPid(
    val label: String,
    val service: Int,
    val did: String,
    val formula: PidFormula,
    val unit: String
) {
    fun decode(response: String): Float? {
        if (UdsDecoding.isNegativeResponse(response)) return null
        val data = UdsDecoding.payload(response, service, did) ?: return null
        return formula.apply(data)
    }

    /** The ELM327 command that requests this parameter, e.g. `221E1C`. */
    fun command(): String = String.format("%02X", service) + did.uppercase()

    val isValid: Boolean
        get() = label.isNotBlank() &&
            did.matches(Regex("[0-9A-Fa-f]{2,4}")) &&
            service in 0x21..0x22
}

/**
 * Live parameters mirrored over SAE J1979-2 ("OBDonUDS"): the classic Mode 01 quantities addressed
 * through UDS `ReadDataByIdentifier` with DID `0xF400 + PID`.
 *
 * This is the standardised half of [ExtendedPids]-style support, so — unlike the manufacturer
 * presets — the DIDs here are safe to request blindly and the only reason a vehicle refuses one is
 * that it speaks the older Mode 01 protocol instead.
 */
enum class J1979Pid(val pid: Int, val label: String, val unit: String) {
    ENGINE_LOAD(0x04, "Calculated engine load", "%"),
    COOLANT_TEMP(0x05, "Engine coolant temperature", "°C"),
    SHORT_TERM_FUEL_TRIM(0x06, "Short-term fuel trim", "%"),
    LONG_TERM_FUEL_TRIM(0x07, "Long-term fuel trim", "%"),
    INTAKE_PRESSURE(0x0B, "Intake manifold pressure", "kPa"),
    RPM(0x0C, "Engine speed", "rpm"),
    SPEED(0x0D, "Vehicle speed", "km/h"),
    TIMING_ADVANCE(0x0E, "Timing advance", "°"),
    INTAKE_TEMP(0x0F, "Intake air temperature", "°C"),
    MAF(0x10, "Mass air flow", "g/s"),
    THROTTLE(0x11, "Throttle position", "%"),
    FUEL_LEVEL(0x2F, "Fuel tank level", "%");

    /** The standardised DID, e.g. `F40C` for engine speed. */
    val did: String get() = UdsDecoding.j1979_2Did(pid)

    /** The ELM327 request that reads this parameter, e.g. `22F40C`. */
    val command: String get() = "22" + did

    /**
     * @param bytes payload after the `62 <DID>` echo. Mode 01 scaling applies unchanged to the
     *              payload of the equivalent DID, which is the whole point of J1979-2.
     */
    fun decode(bytes: IntArray): Float? {
        if (bytes.isEmpty()) return null
        val a = bytes[0] and 0xFF
        val word = if (bytes.size > 1) UdsDecoding.word(a, bytes[1] and 0xFF) else null
        return when (this) {
            ENGINE_LOAD, THROTTLE, FUEL_LEVEL -> a * 100f / 255f
            COOLANT_TEMP, INTAKE_TEMP -> (a - 40).toFloat()
            SHORT_TERM_FUEL_TRIM, LONG_TERM_FUEL_TRIM -> a / 1.28f - 100f
            INTAKE_PRESSURE -> a.toFloat()
            SPEED -> a.toFloat()
            TIMING_ADVANCE -> a / 2f - 64f
            RPM -> word?.let { it / 4f }
            MAF -> word?.let { it / 100f }
        }
    }
}

/** One decoded J1979-2 parameter, as streamed to the UI. */
data class J1979Reading(val pid: J1979Pid, val value: Float, val updatedAt: Long) {
    val formatted: String
        get() = when (pid.unit) {
            "rpm", "kPa" -> value.roundToInt().toString()
            "km/h" -> value.roundToInt().toString()
            else -> String.format("%.1f", value)
        }
}
