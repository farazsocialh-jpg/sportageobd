package com.sportage.obd.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DarkBackground = Color(0xFF101417)
val DarkSurface = Color(0xFF1B2228)
val DarkSurfaceElevated = Color(0xFF2A353E)
val ElectricCyan = Color(0xFF8BD8E7)
val TealAccent = Color(0xFF92D7BC)
val WarningAmber = Color(0xFFEFC681)
val AmberWarning = WarningAmber
val CriticalRed = Color(0xFFFF8D97)
val PrimaryText = Color(0xFFF0F4F6)
val SecondaryText = Color(0xFFAFBEC8)

private val CockpitTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = ElectricCyan,
    secondary = TealAccent,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = PrimaryText,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = SecondaryText,
    outline = SecondaryText.copy(alpha = 0.5f),
    outlineVariant = DarkSurfaceElevated,
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
        typography = CockpitTypography,
        content = content
    )
}
