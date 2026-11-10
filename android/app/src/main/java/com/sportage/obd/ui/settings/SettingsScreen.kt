package com.sportage.obd.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sportage.obd.viewmodel.ObdViewModel

/**
 * Main Settings Architecture Entry Point:
 * Replaces the monolithic scrolling page with a modular Android Settings experience.
 *
 * - When [currentRoute] is null: Displays [SettingsHomeScreen] with ONLY the 8 categories.
 * - When a category is tapped: Displays that category's dedicated subpage.
 * - [BackHandler] intercepts back presses within subpages to return to Settings Home.
 */
@Composable
fun SettingsPage(
    speedUnit: String,
    onToggleSpeedUnit: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBluetoothDialog: () -> Unit = {},
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    onResetTrip: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {},
    fontScale: Float = 1.0f,
    onSetFontScale: (Float) -> Unit = {},
    appearanceMode: String = "Automatic (Follow System)",
    onSetAppearanceMode: (String) -> Unit = {},
    mapArrangement: String = "Balanced (50/50)",
    onSetMapArrangement: (String) -> Unit = {},
    showMiniGaugesOnMap: Boolean = false,
    onSetShowMiniGaugesOnMap: (Boolean) -> Unit = {},
    showQuickActionDock: Boolean = true,
    onSetShowQuickActionDock: (Boolean) -> Unit = {},
    keepScreenAwake: Boolean = true,
    onSetKeepScreenAwake: (Boolean) -> Unit = {},
    themeId: String = "cockpit-dark",
    accentOverride: String? = null,
    densityOverride: String? = null,
    gaugeStyleOverride: String? = null,
    onSetThemeId: (String) -> Unit = {},
    onSetAccentOverride: (String?) -> Unit = {},
    onSetDensityOverride: (String?) -> Unit = {},
    onSetGaugeStyleOverride: (String?) -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    dashboardLayoutId: String = com.sportage.obd.ui.dashboard.DashboardLayoutTheme.DEFAULT_ID,
    onSetDashboardLayoutId: (String) -> Unit = {},
    onResetPreferences: (() -> Unit)? = null,
    onNavigateToHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // Intercept back navigation when inside a subpage: returns to Settings Home
    BackHandler(enabled = currentRoute != null) {
        currentRoute = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (currentRoute) {
            null -> {
                SettingsHomeScreen(
                    onNavigateToCategory = { route ->
                        currentRoute = route
                    },
                    onNavigateBack = onNavigateToHome
                )
            }
            "appearance" -> {
                AppearanceSettingsPage(
                    themeId = themeId,
                    onSetThemeId = onSetThemeId,
                    accentOverride = accentOverride,
                    onSetAccentOverride = onSetAccentOverride,
                    densityOverride = densityOverride,
                    onSetDensityOverride = onSetDensityOverride,
                    gaugeStyleOverride = gaugeStyleOverride,
                    onSetGaugeStyleOverride = onSetGaugeStyleOverride,
                    autoNightDim = autoNightDim,
                    onSetAutoNightDim = onSetAutoNightDim,
                    nightDimLevel = nightDimLevel,
                    onSetNightDimLevel = onSetNightDimLevel,
                    fontScale = fontScale,
                    onSetFontScale = onSetFontScale,
                    appearanceMode = appearanceMode,
                    onSetAppearanceMode = onSetAppearanceMode,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "dashboard" -> {
                DashboardCustomisationPage(
                    dashboardLayoutId = dashboardLayoutId,
                    onSetDashboardLayoutId = onSetDashboardLayoutId,
                    mapArrangement = mapArrangement,
                    onSetMapArrangement = onSetMapArrangement,
                    showMiniGaugesOnMap = showMiniGaugesOnMap,
                    onSetShowMiniGaugesOnMap = onSetShowMiniGaugesOnMap,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "connectivity" -> {
                ConnectivitySettingsPage(
                    isPollingPaused = isPollingPaused,
                    onTogglePausePolling = onTogglePausePolling,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    onOpenBluetoothDialog = onOpenBluetoothDialog,
                    viewModel = viewModel,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "vehicle" -> {
                VehicleDrivingPage(
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    viewModel = viewModel,
                    onResetTrip = onResetTrip,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "alerts" -> {
                AlertsSettingsPage(
                    viewModel = viewModel,
                    speedUnit = speedUnit,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "navigation" -> {
                NavigationLauncherPage(
                    onSetDefaultLauncher = onSetDefaultLauncher,
                    onOpenAppDrawer = onOpenAppDrawer,
                    showQuickActionDock = showQuickActionDock,
                    onSetShowQuickActionDock = onSetShowQuickActionDock,
                    keepScreenAwake = keepScreenAwake,
                    onSetKeepScreenAwake = onSetKeepScreenAwake,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "data" -> {
                DataPrivacyPage(
                    viewModel = viewModel,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "advanced" -> {
                AboutAdvancedPage(
                    isDemoMode = isDemoMode,
                    onToggleDemoMode = onToggleDemoMode,
                    viewModel = viewModel,
                    onOpenBluetoothDialog = onOpenBluetoothDialog,
                    onResetPreferences = onResetPreferences,
                    onNavigateBack = { currentRoute = null }
                )
            }
            else -> {
                SettingsHomeScreen(
                    onNavigateToCategory = { route ->
                        currentRoute = route
                    },
                    onNavigateBack = onNavigateToHome
                )
            }
        }
    }
}
