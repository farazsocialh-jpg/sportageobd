package com.sportage.obd

/**
 * User-selectable whole-screen home dashboard theme.
 */
enum class DashboardTheme(val id: String, val title: String) {
    ORIGINAL("original", "Default Home"),
    MODULAR_CARD("modular_card", "Modular Flip Card"),
    FUTURISTIC_COCKPIT("futuristic_cockpit", "Neon Cockpit");

    companion object {
        fun fromId(id: String?): DashboardTheme =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}