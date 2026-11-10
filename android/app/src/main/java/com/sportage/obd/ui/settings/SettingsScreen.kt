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
    isPollingPaused: Boolean = false,
    onTogglePausePolling: () -> Unit = {},
    onOpenAppDrawer: () -> Unit = {},
    viewModel: ObdViewModel? = null,
    autoNightDim: Boolean = true,
    nightDimLevel: Float = 0.55f,
    onSetAutoNightDim: (Boolean) -> Unit = {},
    onSetNightDimLevel: (Float) -> Unit = {},
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
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "dashboard" -> {
                DashboardCustomisationPage(
                    dashboardLayoutId = dashboardLayoutId,
                    onSetDashboardLayoutId = onSetDashboardLayoutId,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "connectivity" -> {
                ConnectivitySettingsPage(
                    isPollingPaused = isPollingPaused,
                    onTogglePausePolling = onTogglePausePolling,
                    onOpenBluetoothSettings = onOpenBluetoothSettings,
                    viewModel = viewModel,
                    onNavigateBack = { currentRoute = null }
                )
            }
            "vehicle" -> {
                VehicleDrivingPage(
                    speedUnit = speedUnit,
                    onToggleSpeedUnit = onToggleSpeedUnit,
                    viewModel = viewModel,
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
