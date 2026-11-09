package com.sportage.obd.ui.dashboard

import androidx.compose.runtime.Composable

/**
 * Single dispatch point for home-screen layout themes.
 *
 * The classic screen is passed as a slot because it still lives in CarLauncherScreen and
 * owns its map/trip/navigation arguments. The two new themes share the same
 * [DashboardData], [DashboardActions] and [DashboardMedia] instances, so switching between
 * them never re-subscribes telemetry or opens a second media session.
 */
@Composable
fun DashboardThemeHost(
    theme: DashboardLayoutTheme,
    data: DashboardData,
    actions: DashboardActions,
    media: DashboardMedia,
    classic: @Composable () -> Unit
) {
    when (theme) {
        DashboardLayoutTheme.Classic -> classic()
        DashboardLayoutTheme.CircularGauge -> CircularDashboardTheme(data, actions, media)
        DashboardLayoutTheme.RectangularWidgets -> RectangularDashboardTheme(data, actions, media)
    }
}
