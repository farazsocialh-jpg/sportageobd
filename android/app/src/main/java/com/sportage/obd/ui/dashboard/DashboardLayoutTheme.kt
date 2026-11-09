package com.sportage.obd.ui.dashboard

/**
 * A user-selectable dashboard layout theme.
 *
 * Unlike [com.sportage.obd.ui.theme.ThemeSpec], which describes colors, typography and
 * gauge tokens, a layout theme decides the whole home-screen arrangement. The ids are
 * persisted, so they are part of the on-disk contract: rename a theme's title freely,
 * but never reuse or change an id without a migration.
 */
sealed interface DashboardLayoutTheme {
    val id: String
    val title: String
    val description: String

    /** Existing map + telemetry home screen. Kept as the default for continuity. */
    data object Classic : DashboardLayoutTheme {
        override val id = DEFAULT_ID
        override val title = "Map & Cockpit"
        override val description = "Live map beside the analogue cockpit. The original Sportage home screen."
    }

    /** Theme A: central car, large circular speedometer, circular music player, icon dock. */
    data object CircularGauge : DashboardLayoutTheme {
        override val id = "circular-gauge"
        override val title = "Circular Gauge"
        override val description = "Centre car graphic with a large speed dial and circular now-playing disc."
    }

    /** Theme B: rectangular speed/car and clock/media widgets above an icon dock. */
    data object RectangularWidgets : DashboardLayoutTheme {
        override val id = "widget-board"
        override val title = "Widget Board"
        override val description = "Rectangular speed, clock and media widgets laid out like a car launcher."
    }

    companion object {
        const val DEFAULT_ID = "classic"

        val all: List<DashboardLayoutTheme> = listOf(Classic, CircularGauge, RectangularWidgets)

        /** Unknown or corrupt persisted ids fall back to the classic dashboard, never crash. */
        fun fromId(id: String?): DashboardLayoutTheme =
            all.firstOrNull { it.id == id } ?: Classic
    }
}
