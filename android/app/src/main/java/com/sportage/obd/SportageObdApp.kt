package com.sportage.obd

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}