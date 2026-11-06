package com.sportage.obd.diagnostics

import android.content.Context
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Product analytics and crash reporting, gated on explicit user consent.
 *
 * The rule this file exists to enforce: **no diagnostic data leaves the device unless the user
 * turned it on in onboarding or Settings**. [log] and [recordException] are safe to call from
 * anywhere — they check consent first and become no-ops.
 *
 * Firebase is optional at configuration time too. The Gradle build applies the Google Services and
 * Crashlytics plugins only when `android/app/google-services.json` exists; without it every call
 * below throws `IllegalStateException` ("Default FirebaseApp is not initialized"), which is caught
 * here, so a developer build needs no Firebase project at all.
 */
object Diagnostics {

    private const val TAG = "SportageDiagnostics"

    enum class Event(val wireName: String) {
        ONBOARDING_COMPLETED("onboarding_completed"),
        OBD_CONNECT_ATTEMPT("obd_connect_attempt"),
        OBD_CONNECT_SUCCESS("obd_connect_success"),
        OBD_CONNECT_FAILURE("obd_connect_failure"),
        DTC_SCAN_COMPLETED("dtc_scan_completed"),
        DTC_CLEARED("dtc_cleared"),
        TRIP_RECORDED("trip_recorded"),
        PAGE_VIEW("page_view"),
        CUSTOM_PID_ADDED("custom_pid_added"),
        AI_EXPLANATION_REQUESTED("ai_explanation_requested")
    }

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    val isEnabled: Boolean get() = enabled

    /** Called once from [com.sportage.obd.SportageObdApp] with the persisted consent value. */
    fun init(context: Context, consent: Boolean) {
        enabled = consent
        if (!consent) return
        val appContext = context.applicationContext
        analytics = runCatching { FirebaseAnalytics.getInstance(appContext) }
            .onFailure { Log.i(TAG, "Analytics unavailable: ${it.message}") }
            .getOrNull()
        crashlytics = runCatching { FirebaseCrashlytics.getInstance() }
            .onFailure { Log.i(TAG, "Crash reporting unavailable: ${it.message}") }
            .getOrNull()
        crashlytics?.setCrashlyticsCollectionEnabled(true)
    }

    /** Persist the user's choice and start/stop collection immediately. */
    fun setEnabled(context: Context, consent: Boolean) {
        AppPreferences(context).diagnosticsEnabled = consent
        if (consent) {
            init(context, true)
        } else {
            crashlytics?.setCrashlyticsCollectionEnabled(false)
            crashlytics?.deleteUnsentReports()
            analytics = null
            crashlytics = null
            enabled = false
        }
    }

    /**
     * Records a coarse product event. Parameters must never contain VINs, coordinates, adapter MAC
     * addresses, free text or anything else that could identify a person or a car.
     */
    fun log(event: Event, params: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val sink = analytics ?: return
        runCatching {
            val bundle = android.os.Bundle()
            params.forEach { (key, value) ->
                when (value) {
                    is Int -> bundle.putInt(key, value)
                    is Long -> bundle.putLong(key, value)
                    is Float -> bundle.putFloat(key, value)
                    is Double -> bundle.putDouble(key, value)
                    is Boolean -> bundle.putBoolean(key, value)
                    else -> bundle.putString(key, value.toString())
                }
            }
            sink.logEvent(event.wireName, bundle)
        }
    }

    /** Crashlytics only records these; local crash-to-file handling is separate and always on. */
    fun recordException(throwable: Throwable) {
        if (!enabled) return
        crashlytics?.let { runCatching { it.recordException(throwable) } }
    }

    fun setUserContext(key: String, value: String) {
        if (!enabled) return
        crashlytics?.let { runCatching { it.setCustomKey(key, value) } }
    }
}

/**
 * Local, always-available preferences. Analytics consent lives here because it must be readable
 * before any analytics SDK initialises.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("sportage_prefs", Context.MODE_PRIVATE)

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    var diagnosticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIAGNOSTICS, false)
        set(value) = prefs.edit().putBoolean(KEY_DIAGNOSTICS, value).apply()

    /** True once the driver has been shown the privacy summary at least once. */
    var privacyAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ACK, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_ACK, value).apply()

    private companion object {
        const val KEY_ONBOARDING_DONE = "onboarding_completed"
        const val KEY_DIAGNOSTICS = "diagnostics_enabled"
        const val KEY_PRIVACY_ACK = "privacy_acknowledged"
    }
}
