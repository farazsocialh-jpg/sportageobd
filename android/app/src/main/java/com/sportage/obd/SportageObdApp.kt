package com.sportage.obd

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-level crash capture.
 *
 * Every uncaught exception (from any thread, including the Bluetooth service coroutines)
 * is written to files/last_crash.txt BEFORE the process dies. On the next launch
 * MainActivity reads it back and shows it to the driver, so the exact crash reason is
 * visible on the head unit itself - no adb / logcat needed.
 */
class SportageObdApp : Application() {

    override fun onCreate() {
        super.onCreate()
        org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
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
