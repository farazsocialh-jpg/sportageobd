package com.sportage.obd.ui.boards

import org.json.JSONObject
import java.util.Locale

/**
 * Per-widget configuration. Values are validated and clamped by [normalized] before they
 * are persisted or rendered, so a hand-edited/imported value can never crash a widget.
 */
data class WidgetConfig(
    /** Replaces the widget's catalog title when set. */
    val titleOverride: String? = null,
    /** Replaces the default unit label (e.g. "km/h" -> "mph") when set. */
    val unitOverride: String? = null,
    val minValue: Float? = null,
    val maxValue: Float? = null,
    val warnThreshold: Float? = null,
    val criticalThreshold: Float? = null,
    val decimals: Int = 0,
    /** Grid columns this widget spans: 1 = single cell, 2 = full row. */
    val span: Int = 1,
    val refreshMs: Int = DEFAULT_REFRESH_MS,
    val visible: Boolean = true,
    /** auto | digital | bar | arc | minimal */
    val style: String = "auto"
) {
    fun normalized(): WidgetConfig {
        var min = minValue
        var max = maxValue
        if (min != null && max != null && min > max) {
            val swap = min; min = max; max = swap
        }
        var warn = warnThreshold
        var critical = criticalThreshold
        if (warn != null && critical != null && warn >= critical) {
            warn = critical
        }
        if (warn != null) warn = clampToRange(warn, min, max)
        if (critical != null) critical = clampToRange(critical, min, max)
        return copy(
            titleOverride = titleOverride?.trim()?.take(MAX_TITLE)?.takeIf { it.isNotEmpty() },
            unitOverride = unitOverride?.trim()?.take(MAX_UNIT)?.takeIf { it.isNotEmpty() },
            minValue = min,
            maxValue = max,
            warnThreshold = warn,
            criticalThreshold = critical,
            decimals = decimals.coerceIn(0, 2),
            span = span.coerceIn(1, 2),
            refreshMs = refreshMs.coerceIn(MIN_REFRESH_MS, MAX_REFRESH_MS),
            style = if (style.lowercase(Locale.US) in STYLES) style.lowercase(Locale.US) else "auto"
        )
    }

    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (titleOverride != null && titleOverride.trim().length > MAX_TITLE) {
            errors += "Title must be at most $MAX_TITLE characters"
        }
        if (unitOverride != null && unitOverride.trim().length > MAX_UNIT) {
            errors += "Unit must be at most $MAX_UNIT characters"
        }
        if (minValue != null && maxValue != null && minValue >= maxValue) {
            errors += "Minimum must be below maximum"
        }
        if (warnThreshold != null && criticalThreshold != null && warnThreshold >= criticalThreshold) {
            errors += "Warning threshold must be below critical threshold"
        }
        if (decimals !in 0..2) errors += "Decimals must be 0-2"
        if (span !in 1..2) errors += "Span must be 1 or 2 columns"
        if (refreshMs !in MIN_REFRESH_MS..MAX_REFRESH_MS) {
            errors += "Refresh must be ${MIN_REFRESH_MS}-${MAX_REFRESH_MS} ms"
        }
        if (style.lowercase(Locale.US) !in STYLES) errors += "Unknown display style"
        return errors
    }

    fun toJson(): JSONObject = JSONObject().apply {
        titleOverride?.let { put("title", it) }
        unitOverride?.let { put("unit", it) }
        minValue?.let { put("min", it) }
        maxValue?.let { put("max", it) }
        warnThreshold?.let { put("warn", it) }
        criticalThreshold?.let { put("critical", it) }
        put("decimals", decimals)
        put("span", span)
        put("refreshMs", refreshMs)
        put("visible", visible)
        put("style", style)
    }

    /** Numeric formatting for values shown by the widget. */
    fun format(value: Number?): String =
        if (value == null) "--" else String.format(Locale.US, "%.${decimals}f", value.toDouble())

    companion object {
        const val MIN_REFRESH_MS = 100
        const val MAX_REFRESH_MS = 5000
        const val DEFAULT_REFRESH_MS = 250
        const val MAX_TITLE = 24
        const val MAX_UNIT = 12
        val STYLES = setOf("auto", "digital", "bar", "arc", "minimal")
        val REFRESH_CHOICES = listOf(100, 250, 500, 1000, 2000, 5000)

        fun fromJson(obj: JSONObject?): WidgetConfig? {
            if (obj == null) return null
            return WidgetConfig(
                titleOverride = obj.optString("title", "").takeIf { it.isNotBlank() },
                unitOverride = obj.optString("unit", "").takeIf { it.isNotBlank() },
                minValue = obj.optDouble("min", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                maxValue = obj.optDouble("max", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                warnThreshold = obj.optDouble("warn", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                criticalThreshold = obj.optDouble("critical", Double.NaN).takeIf { !it.isNaN() }?.toFloat(),
                decimals = obj.optInt("decimals", 0),
                span = obj.optInt("span", 1),
                refreshMs = obj.optInt("refreshMs", DEFAULT_REFRESH_MS),
                visible = obj.optBoolean("visible", true),
                style = obj.optString("style", "auto")
            ).normalized()
        }

        private fun clampToRange(value: Float, min: Float?, max: Float?): Float {
            var result = value
            if (min != null && result < min) result = min
            if (max != null && result > max) result = max
            return result
        }
    }
}

/** Sensible starting configuration per widget type (decimal precision and full-width map). */
fun BoardWidgetType.defaultWidgetConfig(): WidgetConfig = when (this) {
    BoardWidgetType.VOLTAGE,
    BoardWidgetType.MAF,
    BoardWidgetType.TIMING,
    BoardWidgetType.TRIP_DISTANCE -> WidgetConfig(decimals = 1)
    BoardWidgetType.MAP -> WidgetConfig(span = 2)
    else -> WidgetConfig()
}
