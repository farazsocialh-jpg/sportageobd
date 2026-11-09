package com.sportage.obd.ui.dashboard

import android.content.Context

/**
 * Persists the selected layout theme next to the existing cockpit appearance settings.
 *
 * The project already standardises on SharedPreferences for UI preferences
 * ("sportage_obd_ui"), so this deliberately follows that convention instead of adding a
 * second persistence dependency. Corrupt values fall back to [DashboardLayoutTheme.Classic].
 */
class DashboardLayoutStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): DashboardLayoutTheme =
        DashboardLayoutTheme.fromId(prefs.getString(KEY_LAYOUT, null))

    fun save(theme: DashboardLayoutTheme) {
        prefs.edit().putString(KEY_LAYOUT, theme.id).apply()
    }

    companion object {
        const val PREFS_NAME = "sportage_obd_ui"
        const val KEY_LAYOUT = "dashboard_layout"
    }
}
