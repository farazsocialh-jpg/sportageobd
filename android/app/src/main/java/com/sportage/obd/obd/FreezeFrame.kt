package com.sportage.obd.obd

/** Data class representing a freeze frame from an OBD-II event. */
data class FreezeFrame(
    /** List of parameter items with their labels and values. */
    val items: List<FreezeFrameItem>
)

/** An individual parameter item within a freeze frame. */
data class FreezeFrameItem(
    /** Human-readable label, e.g. "Engine RPM", "Coolant Temp", etc. */
    val label: String,
    /** Value of the parameter, e.g. "95°C", "1726", "72 km/h". */
    val value: String
)