package com.sportage.obd.trips

import java.io.File

/**
 * Offline-first trip history.
 *
 * Stored as CSV in the app's private directory. CSV was chosen over a database because the only
 * queries the app needs are "list all" and "export", and because it makes the export path the same
 * code as the storage path — there is no risk of the exported file disagreeing with the history the
 * driver sees in the app.
 *
 * All methods are synchronous and file-scoped; callers on Android should invoke them from an IO
 * dispatcher.
 */
class TripStore(private val file: File) {

    fun load(): List<TripSummary> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .drop(1) // header
                .filter { it.isNotBlank() }
                .mapNotNull(::parseRow)
        }.getOrDefault(emptyList())
    }

    fun append(trip: TripSummary) {
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(EXPORT_HEADER + "\n")
        file.appendText(trip.toCsvRow() + "\n")
    }

    fun replaceAll(trips: List<TripSummary>) {
        file.parentFile?.mkdirs()
        file.writeText(exportCsv(trips))
    }

    fun clear() {
        file.parentFile?.mkdirs()
        file.writeText(EXPORT_HEADER + "\n")
    }

    /** Full CSV document, ready to share. */
    fun exportCsv(trips: List<TripSummary> = load()): String = buildString {
        append(EXPORT_HEADER).append('\n')
        trips.forEach { append(it.toCsvRow()).append('\n') }
    }

    companion object {
        const val EXPORT_HEADER =
            "started_at,ended_at,distance_km,duration_s,moving_s,max_speed_kmh,avg_speed_kmh," +
                "zero_to_hundred_s,eco_score,fuel_litres,vin"

        fun tripFile(directory: File): File = File(directory, "trips.csv")

        private fun TripSummary.toCsvRow(): String = listOf(
            startedAtMs,
            endedAtMs,
            distanceKm,
            durationSeconds,
            movingSeconds,
            maxSpeedKmh,
            avgSpeedKmh,
            zeroToHundredSeconds?.toString() ?: "",
            ecoScore,
            fuelLitres?.toString() ?: "",
            vin?.let { csvQuote(it) } ?: ""
        ).joinToString(",")

        private fun parseRow(row: String): TripSummary? {
            val cells = splitCsv(row)
            if (cells.size < 9) return null
            fun cell(i: Int): String = cells.getOrElse(i) { "" }
            return runCatching {
                TripSummary(
                    startedAtMs = cell(0).trim().toLong(),
                    endedAtMs = cell(1).trim().toLong(),
                    distanceKm = cell(2).trim().toDouble(),
                    durationSeconds = cell(3).trim().toLong(),
                    movingSeconds = cell(4).trim().toLong(),
                    maxSpeedKmh = cell(5).trim().toInt(),
                    avgSpeedKmh = cell(6).trim().toInt(),
                    zeroToHundredSeconds = cell(7).trim().toDoubleOrNull(),
                    ecoScore = cell(8).trim().toInt(),
                    fuelLitres = cell(9).trim().toDoubleOrNull(),
                    vin = cell(10).trim().takeIf { it.isNotEmpty() }
                )
            }.getOrNull()
        }

        private fun csvQuote(value: String): String =
            if (value.contains(',') || value.contains('"')) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }

        private fun splitCsv(row: String): List<String> {
            val out = ArrayList<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < row.length) {
                val c = row[i]
                when {
                    c == '"' && inQuotes && i + 1 < row.length && row[i + 1] == '"' -> {
                        current.append('"'); i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> {
                        out.add(current.toString()); current.clear()
                    }
                    else -> current.append(c)
                }
                i++
            }
            out.add(current.toString())
            return out
        }
    }
}
