package com.sportage.obd

import android.app.Application
import com.sportage.obd.diagnostics.AppPreferences
import com.sportage.obd.diagnostics.Diagnostics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-level startup.
 *
 * Two jobs:
 * 1. Initialise optional diagnostics **only** when the driver has consented (the preference is
 *    read here, before any UI exists, so no event can be logged ahead of consent).
 * 2. Capture uncaught exceptions to files/last_crash.txt, before the process dies, so the crash
 *    reason is visible on the head unit itself — no adb/logcat needed.
 */
class SportageObdApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val preferences = AppPreferences(this)
        Diagnostics.init(this, preferences.diagnosticsEnabled)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Report first: once the default handler runs, the process is gone.
            runCatching { Diagnostics.recordException(throwable) }
            try {
                val log = File(filesDir, "last_crash.txt")
                log.writeText(
                    buildString {
                        append("Time: ")
                            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                            .append('\n')
                        append("Thread: ").append(thread.name).append('\n')
                        append("Exception: ").append(throwable.javaClass.name).append('\n')
                        append("Message: ").append(throwable.localizedMessage ?: "").append('\n')
                        append('\n')
                        append(throwable.stackTraceToString())
                    }
                )
            } catch (_: Exception) {
                // Writing the log must never mask the original crash.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
