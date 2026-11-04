# SportageOBD ProGuard / R8 rules
#
# minifyEnabled is currently false, so this file is inert. It is kept correct so
# that enabling R8 (recommended before any Play Store or wide distribution) does
# not break reflection, coroutines, or Compose.

# --- Coroutines (debugger/inspection metadata) ---
-dontwarn kotlinx.coroutines.debug.**

# --- Kotlin metadata / reflection ---
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlin.reflect.jvm.internal.**

# BluetoothTransport falls back to the hidden createRfcommSocket(int) method via
# reflection on android.bluetooth.BluetoothDevice. The target class is part of
# the Android framework (never stripped), but keep our call sites intact.
-keepclassmembers class com.sportage.obd.bluetooth.** { *; }

# --- Compose / Activities are kept by the default Android rules via the
#     merged proguard-android-optimize.txt baseline; no extra rules needed. ---

# --- Remove debug log calls in release builds (optional, safe) ---
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
# }
