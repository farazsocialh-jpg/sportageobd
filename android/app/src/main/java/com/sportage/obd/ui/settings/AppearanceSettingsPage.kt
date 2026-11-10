package com.sportage.obd.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.ui.theme.CockpitThemes
import com.sportage.obd.ui.theme.DarkSurface
import com.sportage.obd.ui.theme.DarkSurfaceElevated
import com.sportage.obd.ui.theme.ElectricCyan
import com.sportage.obd.ui.theme.GaugeStyle
import com.sportage.obd.ui.theme.OnAccent
import com.sportage.obd.ui.theme.PrimaryText
import com.sportage.obd.ui.theme.SecondaryText
import com.sportage.obd.ui.theme.ThemeDensity

/**
 * Appearance & Themes Settings Subpage:
 * - Theme selection & visual previews
 * - Accent colour customisation
 * - Layout density (Theme, compact, comfortable, spacious)
 * - Gauge style (Theme, radial, bar, digital, arc, minimal)
 * - Automotive typography & font size scaling
 * - Light/dark/automatic mode & auto night dimming
 */
@Composable
fun AppearanceSettingsPage(
    themeId: String,
    onSetThemeId: (String) -> Unit,
    accentOverride: String?,
    onSetAccentOverride: (String?) -> Unit,
    densityOverride: String? = null,
    onSetDensityOverride: (String?) -> Unit = {},
    gaugeStyleOverride: String? = null,
    onSetGaugeStyleOverride: (String?) -> Unit = {},
    autoNightDim: Boolean,
    onSetAutoNightDim: (Boolean) -> Unit,
    nightDimLevel: Float,
    onSetNightDimLevel: (Float) -> Unit,
    onOpenPrivacyPolicy: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE) }

    var fontScale by remember { mutableFloatStateOf(uiPrefs.getFloat("fontScale", 1.0f).coerceIn(0.85f, 1.30f)) }
    var appearanceMode by remember { mutableStateOf(uiPrefs.getString("appearanceMode", "Automatic (Follow System)") ?: "Automatic (Follow System)") }

    val activePreset = remember(themeId) { CockpitThemes.byId(themeId) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsTopBar(
            title = "Appearance & Themes",
            subtitle = "Customise visual theme, accent colours, and readability",
            onNavigateBack = onNavigateBack
        )

        // 1. THEME SELECTION & PREVIEWS (Interactive themes, accent override, density, gauge style, import/export)
        SettingsSectionHeader(title = "Theme Selection & Customisation")

        com.sportage.obd.ui.ThemeCustomizationCard(
            themeId = themeId,
            accentOverride = accentOverride,
            densityOverride = densityOverride,
            gaugeStyleOverride = gaugeStyleOverride,
            onSetThemeId = onSetThemeId,
            onSetAccentOverride = onSetAccentOverride,
            onSetDensityOverride = onSetDensityOverride,
            onSetGaugeStyleOverride = onSetGaugeStyleOverride,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy
        )

        // 4. DAY / NIGHT & LIGHT / DARK MODE
        SettingsSectionHeader(title = "Day / Night & Display Mode")

        SettingsValueItem(
            title = "Display Mode",
            valueText = appearanceMode,
            description = "Switch between dark theme, light theme, or automatic day/night detection",
            icon = Icons.Default.BrightnessMedium,
            onClick = {
                val next = when (appearanceMode) {
                    "Automatic (Follow System)" -> "Dark Mode (Night Always)"
                    "Dark Mode (Night Always)" -> "Light Mode (Day High-Vis)"
                    else -> "Automatic (Follow System)"
                }
                appearanceMode = next
                uiPrefs.edit().putString("appearanceMode", next).apply()
            }
        )

        SettingsSwitchItem(
            title = "Auto Night Dimming",
            description = "Dim the screen brightness automatically after dark to reduce eye strain",
            checked = autoNightDim,
            onCheckedChange = onSetAutoNightDim,
            icon = Icons.Default.BrightnessMedium
        )

        if (autoNightDim) {
            SettingsSliderItem(
                title = "Night Brightness Level",
                value = 1f - nightDimLevel,
                onValueChange = { onSetNightDimLevel(1f - it) },
                valueRange = 0.15f..0.90f,
                valueDisplay = "${((1f - nightDimLevel) * 100).toInt()}%",
                description = "Controls ambient dark dimming percentage when night mode is active"
            )
        }

        // 5. AUTOMOTIVE TYPOGRAPHY & FONT SIZE
        SettingsSectionHeader(title = "Automotive Typography & Font Size")

        val fontScales = listOf(0.85f to "Compact (0.85×)", 1.0f to "Standard (1.0×)", 1.15f to "Large (1.15×)", 1.30f to "Extra Large (1.3×)")
        SettingsValueItem(
            title = "Font Size Scaling",
            valueText = fontScales.firstOrNull { it.first == fontScale }?.second ?: "Standard (1.0×)",
            description = "In-car readability scaling for zero-glance-delay driving",
            icon = Icons.Default.FormatSize,
            onClick = {
                val currentIdx = fontScales.indexOfFirst { it.first == fontScale }
                val nextIdx = (if (currentIdx == -1) 1 else currentIdx + 1) % fontScales.size
                val nextScale = fontScales[nextIdx].first
                fontScale = nextScale
                uiPrefs.edit().putFloat("fontScale", nextScale).apply()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
