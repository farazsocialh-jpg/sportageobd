package com.sportage.obd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0B1118)
val DarkSurface = Color(0xFF14212B)
val DarkSurfaceElevated = Color(0xFF1B2A36)
val ElectricCyan = Color(0xFF5CDCF4)
val TealAccent = Color(0xFF52D6B0)
val WarningAmber = Color(0xFFF2B95D)
val CriticalRed = Color(0xFFF06464)
val PrimaryText = Color(0xFFEAF5FA)
val SecondaryText = Color(0xFF9BB2C0)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = TealAccent,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = PrimaryText,
    onSurface = PrimaryText,
    error = CriticalRed
)

@Composable
fun SportageObdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
