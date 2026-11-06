# SportageOBD ProGuard / R8 rules
#
# R8 runs on release builds (isMinifyEnabled = true in app/build.gradle.kts). Everything kept here
# is kept for a concrete reason; avoid adding blanket "-keep class **" rules, which would silently
# undo the shrinking and re-inflate the AAB.

# --- Kotlin metadata / reflection -------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-keepclassmembers class kotlin.Metadata { public <methods>; }

# Kotlin enums are read back by name (custom PID formulas persisted in preferences, DTC severity
# values rendered in reports). R8 may otherwise rename enum constants used only via valueOf().
-keepclassmembers enum com.sportage.obd.obd.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Bluetooth ---------------------------------------------------------------------------------
# BluetoothTransport falls back to the hidden BluetoothDevice.createRfcommSocket(int) overload via
# reflection on ROMs that refuse the documented one. The framework class itself is never stripped,
# but the reflective method lookup is name-based, so the name must survive.
-keepclassmembers class com.sportage.obd.bluetooth.** { *; }
-keepclassmembers class android.bluetooth.BluetoothDevice {
    public java.io.Closeable createRfcommSocket(int);
}

# --- OBD model ---------------------------------------------------------------------------------
# Data classes are serialised into CSV rows and crash-report keys; keep their accessors readable so
# a stack trace from a shipped build still means something.
-keepclassmembers class com.sportage.obd.obd.** { *; }
-keepclassmembers class com.sportage.obd.trips.** { *; }
-keepclassmembers class com.sportage.obd.service.** { *; }

# --- Coroutines ---------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlin.reflect.jvm.internal.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Firebase / Crashlytics ---------------------------------------------------------------------
# The SDKs ship consumer rules. Keep the entry points we call explicitly and silence the optional
# annotation processors they reference but do not bundle.
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# --- Compose -------------------------------------------------------------------------------------
# androidx.compose.* artifacts ship their own consumer rules with the AARs; no extra rules needed.
# The app's own @Composable functions are called directly (not reflectively), so they can shrink.

# --- Debug logging ---------------------------------------------------------------------------------
# Release builds do not need verbose logging; keeping it out also trims the APK slightly.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
