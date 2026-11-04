package com.sportage.obd.service

import android.content.Context
import android.util.Log
import com.sportage.obd.service.ObdTelemetry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolls CSV telemetry logs to app-accessible external storage so the user
 * can share them with ACTION_SEND (email, Drive, Telegram...).
 */
class CsvLogger(private val context: Context) {

    private var currentFile: File? = null
    private var bytesWritten = 0L

    val activeFile: File? get() = currentFile

    val isLogging: Boolean get() = currentFile != null

    fun start(): File {
        stop()
        val dir = File(context.getExternalFilesDir(null), "obd_logs").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "session_$ts.csv")
        file.appendText(CSV_HEADER)
        bytesWritten = CSV_HEADER.length.toLong()
        currentFile = file
        return file
    }

    fun stop() {
        currentFile = null
        bytesWritten = 0
    }

    /** Appends one telemetry row. Returns false when not logging. */
    fun log(telemetry: ObdTelemetry): Boolean {
        val file = currentFile ?: return false
        return try {
            val row = buildString {
                append(System.currentTimeMillis()).append(',')
                    .append(telemetry.rpm).append(',')
                    .append(telemetry.speedKmh).append(',')
                    .append(telemetry.coolantTempC).append(',')
                    .append(telemetry.intakeTempC).append(',')
                    .append(telemetry.throttlePct).append(',')
                    .append(telemetry.engineLoadPct).append(',')
                    .append(telemetry.fuelLevelPct).append(',')
                    .append(telemetry.batteryVoltage).append(',')
                    .append(telemetry.mafGs).append(',')
                    .append(telemetry.shortTermFuelTrimPct).append(',')
                    .append(telemetry.longTermFuelTrimPct).append(',')
                    .append(telemetry.timingAdvanceDeg).append(',')
                    .append(telemetry.fuelEconomyL100).append('\n')
            }
            file.appendText(row)
            bytesWritten += row.length
            if (bytesWritten > MAX_FILE_BYTES) {
                roll()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "CSV log write failed", e)
            false
        }
    }

    private fun roll() {
        currentFile?.let { old ->
            Log.i(TAG, "Rolling CSV log at ${old.length()} bytes")
        }
        start()
    }

    companion object {
        private const val TAG = "CsvLogger"
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024 // 5 MB per session file

        private const val CSV_HEADER =
            "timestamp,rpm,speed_kmh,coolant_c,intake_c,throttle_pct,load_pct,fuel_pct,battery_v,maf_gs,stft_pct,ltft_pct,timing_deg,l_per_100km\n"
    }
}
