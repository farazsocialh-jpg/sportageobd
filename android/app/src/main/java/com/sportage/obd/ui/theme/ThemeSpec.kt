package com.sportage.obd.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Versioned theme schema. A theme is a complete visual description of the cockpit:
 * colors, typography, shapes, spacing, motion, elevation, iconography, background,
 * gauge presentation and the default widget layout. Screens must read these tokens
 * (directly or through the [LocalCockpitColors] / [LocalCockpitTokens] locals) instead
 * of hardcoding visual constants.
 */
const val ThemeSchemaVersion: String = "2.0"

/** Color mode a theme is designed for. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Layout density preset. */
enum class ThemeDensity(val id: String) {
    COMPACT("compact"), COMFORTABLE("comfortable"), SPACIOUS("spacious");

    companion object {
        fun fromId(id: String?): ThemeDensity =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: COMFORTABLE
    }
}

/** Gauge presentation styles. */
enum class GaugeStyle(val id: String) {
    RADIAL("radial"), BAR("bar"), DIGITAL("digital"), ARC("arc"), MINIMAL("minimal");

    companion object {
        fun fromId(id: String?): GaugeStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DIGITAL
    }
}

/** Background rendering styles. Only bundled patterns are allowed (no remote images). */
enum class BackgroundStyle(val id: String) {
    SOLID("solid"), GRADIENT("gradient"), PATTERN("pattern");

    companion object {
        fun fromId(id: String?): BackgroundStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SOLID
    }
}

/** Icon treatment. */
enum class IconStyle(val id: String) {
    FILLED("filled"), OUTLINED("outlined");

    companion object {
        fun fromId(id: String?): IconStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OUTLINED
    }
}

data class ThemeColorTokens(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color,
    val success: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val gaugeTrack: Color,
    val gaugeFill: Color,
    val chart: List<Color>
)

data class ThemeTypography(
    val fontFamily: FontFamily,
    val numericFontFamily: FontFamily,
    val displayLargeSp: Float,
    val displayMediumSp: Float,
    val headlineLargeSp: Float,
    val titleLargeSp: Float,
    val titleMediumSp: Float,
    val bodyLargeSp: Float,
    val bodyMediumSp: Float,
    val labelLargeSp: Float,
    val labelMediumSp: Float,
    val weightRegular: Int,
    val weightMedium: Int,
    val weightBold: Int,
    val letterSpacingEm: Float
)

data class ThemeShapes(
    val cornerDp: Float,
    val cardCornerDp: Float,
    val borderDp: Float,
    val gaugeStrokeDp: Float
)

data class ThemeSpacing(
    val density: ThemeDensity,
    val itemGapDp: Float,
    val cardPaddingDp: Float,
    val safeMarginDp: Float
)

data class ThemeMotion(
    val fastMs: Int,
    val normalMs: Int,
    val slowMs: Int,
    val easing: String
)

data class ThemeElevation(
    val cardElevationDp: Float,
    val glowAlpha: Float
)

data class ThemeIconography(
    val style: IconStyle,
    val strokeDp: Float
)

data class ThemeBackground(
    val style: BackgroundStyle,
    val base: Color,
    val accent: Color,
    val gradientAngle: Int,
    /** Bundled pattern name (lowercase letters, digits, dashes only). */
    val pattern: String
)

data class ThemeGauge(
    val style: GaugeStyle,
    val needle: String,
    val tickCount: Int,
    val warnFraction: Float,
    val criticalFraction: Float
)

data class ThemeLayout(
    val launcherRows: Int,
    val launcherCols: Int,
    val widgetMinWidthDp: Int,
    val widgetMinHeightDp: Int,
    /** Default widgets per board, referenced by [com.sportage.obd.ui.boards.BoardWidgetType.id]. */
    val widgetPresets: List<List<String>>
)

/**
 * A fully resolved theme. Built-in themes are pure Kotlin values so they can be used
 * from JVM unit tests without Android resources.
 */
data class ThemeSpec(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val mode: ThemeMode,
    val baseId: String?,
    val colors: ThemeColorTokens,
    val typography: ThemeTypography,
    val shapes: ThemeShapes,
    val spacing: ThemeSpacing,
    val motion: ThemeMotion,
    val elevation: ThemeElevation,
    val iconography: ThemeIconography,
    val background: ThemeBackground,
    val gauge: ThemeGauge,
    val layout: ThemeLayout
) {
    /** Maps the extended palette onto the historical accessor model used by screens. */
    fun toCockpitColors(): CockpitThemeColors = CockpitThemeColors(
        isDark = mode != ThemeMode.LIGHT,
        background = colors.background,
        surface = colors.surface,
        surfaceElevated = colors.surfaceVariant,
        deepShade = colors.background,
        accent = colors.accent,
        secondary = colors.secondary,
        warning = colors.warning,
        critical = colors.danger,
        textPrimary = colors.textPrimary,
        textSecondary = colors.textSecondary,
        onAccent = readableOn(colors.accent),
        success = colors.success,
        outline = colors.outline,
        gaugeTrack = colors.gaugeTrack,
        gaugeFill = colors.gaugeFill
    )

    fun withColors(next: ThemeColorTokens): ThemeSpec = copy(colors = next)

    fun withDensity(density: ThemeDensity): ThemeSpec = copy(spacing = spacing.copy(density = density))

    fun withGaugeStyle(style: GaugeStyle): ThemeSpec = copy(gauge = gauge.copy(style = style))

    /** WCAG 2.1 contrast ratio between two opaque colors (1.0 – 21.0). */
    fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Accessibility problems a theme editor should surface. Critical readings must stay
     * readable in a moving car, so body text needs 4.5:1 and large numerals 3:1.
     */
    fun accessibilityWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        val body = contrastRatio(colors.textPrimary, colors.background)
        if (body < 4.5f) warnings += "Primary text contrast %.2f:1 is below 4.5:1".format(Locale.US, body)
        val secondary = contrastRatio(colors.textSecondary, colors.background)
        if (secondary < 4.5f) warnings += "Secondary text contrast %.2f:1 is below 4.5:1".format(Locale.US, secondary)
        val gauge = contrastRatio(colors.gaugeFill, colors.surface)
        if (gauge < 3f) warnings += "Gauge fill contrast %.2f:1 is below 3:1".format(Locale.US, gauge)
        val critical = contrastRatio(colors.danger, colors.surface)
        if (critical < 3f) warnings += "Critical color contrast %.2f:1 is below 3:1".format(Locale.US, critical)
        return warnings
    }

    companion object {
        /** Text color that stays readable on top of [fill]. */
        fun readableOn(fill: Color): Color =
            if (fill.luminance() > 0.5f) Color(0xFF0A0F14) else Color(0xFFFFFFFF)
    }
}

data class ThemedMotion(val spec: ThemeMotion, val reduceMotion: Boolean) {
    /** Motion duration honoring the reduce-motion setting: 0 ms means snap. */
    fun duration(baseMs: Int): Int = if (reduceMotion) 0 else baseMs.coerceIn(0, 5_000)
}

sealed interface ThemeParseResult {
    data class Success(val spec: ThemeSpec) : ThemeParseResult
    data class Failure(val errors: List<String>) : ThemeParseResult
}

/**
 * Strict parser/serializer for the versioned theme JSON schema.
 *
 * Security rules, enforced for every imported theme:
 *  - no remote or embedded URLs (http, https, data, file, javascript)
 *  - no arbitrary layout injection: only known widget ids, bounded grids
 *  - bounded document size and string lengths
 *  - every color/number validated and clamped to a sane range
 * Invalid documents are rejected wholesale; callers keep the previous theme and log.
 */
object ThemeJson {
    const val MAX_CHARS = 64 * 1024

    private val FORBIDDEN_TOKENS = listOf("http://", "https://", "javascript:", "file://", "data:")
    private val ID_REGEX = Regex("^[a-z0-9][a-z0-9_-]{1,40}$")
    private val PATTERN_REGEX = Regex("^[a-z0-9-]{1,24}$")
    private val EASINGS = setOf("linear", "ease", "ease-in", "ease-out", "ease-in-out", "spring")

    private val TOP_KEYS = setOf("version", "id", "name", "description", "mode", "extends", "tokens", "widgetPresets")
    private val GROUP_KEYS = setOf(
        "color", "typography", "shape", "spacing", "elevation",
        "motion", "iconography", "background", "gauge", "layout"
    )

    fun parse(raw: String?, base: ThemeSpec? = null): ThemeParseResult {
        if (raw.isNullOrBlank()) return ThemeParseResult.Failure(listOf("Theme document is empty"))
        if (raw.length > MAX_CHARS) return ThemeParseResult.Failure(listOf("Theme exceeds ${MAX_CHARS / 1024} KB"))

        val lowered = raw.lowercase(Locale.US)
        FORBIDDEN_TOKENS.firstOrNull { lowered.contains(it) }?.let {
            return ThemeParseResult.Failure(listOf("Remote or embedded content is not allowed ($it)"))
        }

        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return ThemeParseResult.Failure(listOf("Malformed JSON: ${e.message ?: "parse error"}"))
        }

        val errors = mutableListOf<String>()
        root.keys().forEach { key ->
            if (key !in TOP_KEYS) errors += "Unknown field '$key'"
        }

        val version = root.optString("version", ThemeSchemaVersion).trim()
        if (version !in setOf("1.0", "1.1", "2.0")) errors += "Unsupported schema version '$version'"

        val id = root.optString("id", "").trim()
        if (!ID_REGEX.matches(id)) errors += "Invalid theme id (use a-z, 0-9 and dashes)"

        val name = root.optString("name", "").trim()
        if (name.isBlank() || name.length > 60) errors += "Theme name must be 1-60 characters"

        val description = root.optString("description", "").trim().take(240)
        val mode = when (root.optString("mode", "dark").lowercase(Locale.US)) {
            "light" -> ThemeMode.LIGHT
            "system" -> ThemeMode.SYSTEM
            "dark" -> ThemeMode.DARK
            else -> {
                errors += "mode must be light, dark or system"
                ThemeMode.DARK
            }
        }
        val extendsId = root.optString("extends", "").takeIf { it.isNotBlank() }
        if (extendsId != null && !ID_REGEX.matches(extendsId)) errors += "Invalid extends id"

        val tokens = root.optJSONObject("tokens")
        if (tokens == null && base == null) errors += "Missing tokens"
        tokens?.keys()?.forEach { key ->
            if (key !in GROUP_KEYS) errors += "Unknown token group '$key'"
        }

        val colors = parseColors(tokens?.optJSONObject("color"), base?.colors, errors)
        val typography = parseTypography(tokens?.optJSONObject("typography"), base?.typography, errors)
        val shapes = parseShapes(tokens?.optJSONObject("shape"), base?.shapes, errors)
        val spacing = parseSpacing(tokens?.optJSONObject("spacing"), base?.spacing, errors)
        val motion = parseMotion(tokens?.optJSONObject("motion"), base?.motion, errors)
        val elevation = parseElevation(tokens?.optJSONObject("elevation"), base?.elevation, errors)
        val iconography = parseIconography(tokens?.optJSONObject("iconography"), base?.iconography, errors)
        val background = parseBackground(tokens?.optJSONObject("background"), base?.background, errors)
        val gauge = parseGauge(tokens?.optJSONObject("gauge"), base?.gauge, errors)
        val layout = parseLayout(
            tokens?.optJSONObject("layout"),
            root.optJSONArray("widgetPresets"),
            base?.layout,
            errors
        )

        if (errors.isNotEmpty()) return ThemeParseResult.Failure(errors)

        val spec = ThemeSpec(
            id = id,
            name = name,
            description = description,
            version = version,
            mode = mode,
            baseId = extendsId,
            colors = colors,
            typography = typography,
            shapes = shapes,
            spacing = spacing,
            motion = motion,
            elevation = elevation,
            iconography = iconography,
            background = background,
            gauge = gauge,
            layout = layout
        )
        // A theme that cannot be read in daylight or at night is rejected: text must
        // reach at least 3:1 against the background for the driver to read it safely.
        if (spec.contrastRatio(spec.colors.textPrimary, spec.colors.background) < 3f) {
            return ThemeParseResult.Failure(listOf("Primary text contrast is too low to read safely"))
        }
        return ThemeParseResult.Success(spec)
    }

    /** Parses either a single theme object or `{ "themes": [ ... ] }`. */
    fun parseAll(raw: String?, baseLookup: (String) -> ThemeSpec? = { null }): List<ThemeParseResult> {
        if (raw.isNullOrBlank()) return listOf(ThemeParseResult.Failure(listOf("Theme document is empty")))
        if (raw.length > MAX_CHARS * 8) return listOf(ThemeParseResult.Failure(listOf("Theme bundle is too large")))
        val array: JSONArray = try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("[")) {
                // Also accept a bare array, which is what the import store writes.
                JSONArray(trimmed)
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("themes") ?: JSONArray().put(root)
            }
        } catch (e: Exception) {
            return listOf(ThemeParseResult.Failure(listOf("Malformed JSON: ${e.message ?: "parse error"}")))
        }
        val results = mutableListOf<ThemeParseResult>()
        for (i in 0 until array.length()) {
            val element = array.optJSONObject(i)
                ?: array.optString(i, null)?.let { text ->
                    try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        null
                    }
                }
            if (element == null) {
                results += ThemeParseResult.Failure(listOf("Theme #$i is not an object"))
                continue
            }
            val extendsId = element.optString("extends", "").takeIf { it.isNotBlank() }
            results += parse(element.toString(), extendsId?.let(baseLookup))
        }
        return results
    }

    /** Serializes a theme using the same schema so export/import round-trips losslessly. */
    fun export(spec: ThemeSpec, pretty: Boolean = true): String {
        val root = JSONObject()
        root.put("version", spec.version)
        root.put("id", spec.id)
        root.put("name", spec.name)
        if (spec.description.isNotBlank()) root.put("description", spec.description)
        root.put("mode", spec.mode.name.lowercase(Locale.US))
        spec.baseId?.let { root.put("extends", it) }

        val tokens = JSONObject()
        val color = JSONObject()
        color.put("background", spec.colors.background.toThemeHex())
        color.put("surface", spec.colors.surface.toThemeHex())
        color.put("surfaceVariant", spec.colors.surfaceVariant.toThemeHex())
        color.put("primary", spec.colors.primary.toThemeHex())
        color.put("secondary", spec.colors.secondary.toThemeHex())
        color.put("accent", spec.colors.accent.toThemeHex())
        color.put("warning", spec.colors.warning.toThemeHex())
        color.put("danger", spec.colors.danger.toThemeHex())
        color.put("success", spec.colors.success.toThemeHex())
        color.put("textPrimary", spec.colors.textPrimary.toThemeHex())
        color.put("textSecondary", spec.colors.textSecondary.toThemeHex())
        color.put("outline", spec.colors.outline.toThemeHex())
        color.put("gaugeTrack", spec.colors.gaugeTrack.toThemeHex())
        color.put("gaugeFill", spec.colors.gaugeFill.toThemeHex())
        color.put("chart", JSONArray(spec.colors.chart.map { it.toThemeHex() }))
        tokens.put("color", color)

        val typography = JSONObject()
        typography.put("fontFamily", spec.typography.fontFamily.toFamilyId())
        typography.put("numericFont", spec.typography.numericFontFamily.toFamilyId())
        typography.put("displayLarge", spec.typography.displayLargeSp)
        typography.put("displayMedium", spec.typography.displayMediumSp)
        typography.put("headlineLarge", spec.typography.headlineLargeSp)
        typography.put("titleLarge", spec.typography.titleLargeSp)
        typography.put("titleMedium", spec.typography.titleMediumSp)
        typography.put("bodyLarge", spec.typography.bodyLargeSp)
        typography.put("bodyMedium", spec.typography.bodyMediumSp)
        typography.put("labelLarge", spec.typography.labelLargeSp)
        typography.put("labelMedium", spec.typography.labelMediumSp)
        typography.put("weightRegular", spec.typography.weightRegular)
        typography.put("weightMedium", spec.typography.weightMedium)
        typography.put("weightBold", spec.typography.weightBold)
        typography.put("letterSpacing", spec.typography.letterSpacingEm)
        tokens.put("typography", typography)

        tokens.put("shape", JSONObject()
            .put("cornerRadius", spec.shapes.cornerDp)
            .put("cardCornerRadius", spec.shapes.cardCornerDp)
            .put("borderWidth", spec.shapes.borderDp)
            .put("gaugeStroke", spec.shapes.gaugeStrokeDp))
        tokens.put("spacing", JSONObject()
            .put("density", spec.spacing.density.id)
            .put("itemGap", spec.spacing.itemGapDp)
            .put("cardPadding", spec.spacing.cardPaddingDp)
            .put("safeMargin", spec.spacing.safeMarginDp))
        tokens.put("motion", JSONObject()
            .put("fast", spec.motion.fastMs)
            .put("normal", spec.motion.normalMs)
            .put("slow", spec.motion.slowMs)
            .put("easing", spec.motion.easing))
        tokens.put("elevation", JSONObject()
            .put("cardElevation", spec.elevation.cardElevationDp)
            .put("glowAlpha", spec.elevation.glowAlpha))
        tokens.put("iconography", JSONObject()
            .put("style", spec.iconography.style.id)
            .put("strokeWidth", spec.iconography.strokeDp))
        tokens.put("background", JSONObject()
            .put("style", spec.background.style.id)
            .put("base", spec.background.base.toThemeHex())
            .put("accent", spec.background.accent.toThemeHex())
            .put("gradientAngle", spec.background.gradientAngle)
            .put("pattern", spec.background.pattern))
        tokens.put("gauge", JSONObject()
            .put("style", spec.gauge.style.id)
            .put("needle", spec.gauge.needle)
            .put("tickCount", spec.gauge.tickCount)
            .put("warnFraction", spec.gauge.warnFraction)
            .put("criticalFraction", spec.gauge.criticalFraction))
        val layout = JSONObject()
            .put("launcherRows", spec.layout.launcherRows)
            .put("launcherCols", spec.layout.launcherCols)
            .put("widgetMinWidth", spec.layout.widgetMinWidthDp)
            .put("widgetMinHeight", spec.layout.widgetMinHeightDp)
        tokens.put("layout", layout)
        root.put("tokens", tokens)
        root.put("widgetPresets", JSONArray(spec.layout.widgetPresets.map { board -> JSONArray(board) }))

        return if (pretty) root.toString(2) else root.toString()
    }

    // ---------------------------------------------------------------- parsing helpers

    private fun parseColors(obj: JSONObject?, base: ThemeColorTokens?, errors: MutableList<String>): ThemeColorTokens {
        val fallback = base ?: ThemeDefaults.colors
        if (obj == null) return fallback
        checkKeys(obj, COLOR_KEYS, "color", errors)
        fun color(key: String, default: Color): Color {
            if (!obj.has(key)) return default
            val parsed = parseThemeColor(obj.optString(key, ""))
            if (parsed == null) {
                errors += "color.$key must be #RRGGBB or #AARRGGBB"
                return default
            }
            return parsed
        }
        val chart = mutableListOf<Color>()
        if (obj.has("chart")) {
            val array = obj.optJSONArray("chart")
            if (array == null || array.length() !in 1..8) {
                errors += "color.chart must be an array of 1-8 colors"
            } else {
                for (i in 0 until array.length()) {
                    val parsed = parseThemeColor(array.optString(i, ""))
                    if (parsed == null) errors += "color.chart[$i] is not a valid color" else chart += parsed
                }
            }
        }
        if (chart.isEmpty()) chart += fallback.chart
        return ThemeColorTokens(
            background = color("background", fallback.background),
            surface = color("surface", fallback.surface),
            surfaceVariant = color("surfaceVariant", fallback.surfaceVariant),
            primary = color("primary", fallback.primary),
            secondary = color("secondary", fallback.secondary),
            accent = color("accent", fallback.accent),
            warning = color("warning", fallback.warning),
            danger = color("danger", fallback.danger),
            success = color("success", fallback.success),
            textPrimary = color("textPrimary", fallback.textPrimary),
            textSecondary = color("textSecondary", fallback.textSecondary),
            outline = color("outline", fallback.outline),
            gaugeTrack = color("gaugeTrack", fallback.gaugeTrack),
            gaugeFill = color("gaugeFill", fallback.gaugeFill),
            chart = chart
        )
    }

    private fun parseTypography(obj: JSONObject?, base: ThemeTypography?, errors: MutableList<String>): ThemeTypography {
        val fallback = base ?: ThemeDefaults.typography
        if (obj == null) return fallback
        checkKeys(obj, TYPOGRAPHY_KEYS, "typography", errors)
        val family = familyFromId(obj.optString("fontFamily", ""), fallback.fontFamily)
        val numeric = familyFromId(obj.optString("numericFont", ""), fallback.numericFontFamily)
        return ThemeTypography(
            fontFamily = family,
            numericFontFamily = numeric,
            displayLargeSp = size(obj, "displayLarge", fallback.displayLargeSp, "typography", errors),
            displayMediumSp = size(obj, "displayMedium", fallback.displayMediumSp, "typography", errors),
            headlineLargeSp = size(obj, "headlineLarge", fallback.headlineLargeSp, "typography", errors),
            titleLargeSp = size(obj, "titleLarge", fallback.titleLargeSp, "typography", errors),
            titleMediumSp = size(obj, "titleMedium", fallback.titleMediumSp, "typography", errors),
            bodyLargeSp = size(obj, "bodyLarge", fallback.bodyLargeSp, "typography", errors),
            bodyMediumSp = size(obj, "bodyMedium", fallback.bodyMediumSp, "typography", errors),
            labelLargeSp = size(obj, "labelLarge", fallback.labelLargeSp, "typography", errors),
            labelMediumSp = size(obj, "labelMedium", fallback.labelMediumSp, "typography", errors),
            weightRegular = weight(obj, "weightRegular", fallback.weightRegular, "typography", errors),
            weightMedium = weight(obj, "weightMedium", fallback.weightMedium, "typography", errors),
            weightBold = weight(obj, "weightBold", fallback.weightBold, "typography", errors),
            letterSpacingEm = ranged(obj, "letterSpacing", fallback.letterSpacingEm, -0.1f, 0.2f, "typography", errors)
        )
    }

    private fun parseShapes(obj: JSONObject?, base: ThemeShapes?, errors: MutableList<String>): ThemeShapes {
        val fallback = base ?: ThemeDefaults.shapes
        if (obj == null) return fallback
        checkKeys(obj, SHAPE_KEYS, "shape", errors)
        return ThemeShapes(
            cornerDp = ranged(obj, "cornerRadius", fallback.cornerDp, 0f, 48f, "shape", errors),
            cardCornerDp = ranged(obj, "cardCornerRadius", fallback.cardCornerDp, 0f, 48f, "shape", errors),
            borderDp = ranged(obj, "borderWidth", fallback.borderDp, 0f, 8f, "shape", errors),
            gaugeStrokeDp = ranged(obj, "gaugeStroke", fallback.gaugeStrokeDp, 0.5f, 16f, "shape", errors)
        )
    }

    private fun parseSpacing(obj: JSONObject?, base: ThemeSpacing?, errors: MutableList<String>): ThemeSpacing {
        val fallback = base ?: ThemeDefaults.spacing
        if (obj == null) return fallback
        checkKeys(obj, SPACING_KEYS, "spacing", errors)
        val densityRaw = obj.optString("density", fallback.density.id)
        val density = ThemeDensity.entries.firstOrNull { it.id == densityRaw.lowercase(Locale.US) }
        if (density == null) errors += "spacing.density must be compact, comfortable or spacious"
        return ThemeSpacing(
            density = density ?: fallback.density,
            itemGapDp = ranged(obj, "itemGap", fallback.itemGapDp, 0f, 96f, "spacing", errors),
            cardPaddingDp = ranged(obj, "cardPadding", fallback.cardPaddingDp, 0f, 96f, "spacing", errors),
            safeMarginDp = ranged(obj, "safeMargin", fallback.safeMarginDp, 0f, 96f, "spacing", errors)
        )
    }

    private fun parseMotion(obj: JSONObject?, base: ThemeMotion?, errors: MutableList<String>): ThemeMotion {
        val fallback = base ?: ThemeDefaults.motion
        if (obj == null) return fallback
        checkKeys(obj, MOTION_KEYS, "motion", errors)
        val easing = obj.optString("easing", fallback.easing).lowercase(Locale.US)
        if (easing !in EASINGS) errors += "motion.easing must be one of ${EASINGS.joinToString()}"
        return ThemeMotion(
            fastMs = intRanged(obj, "fast", fallback.fastMs, 0, 2_000, "motion", errors),
            normalMs = intRanged(obj, "normal", fallback.normalMs, 0, 2_000, "motion", errors),
            slowMs = intRanged(obj, "slow", fallback.slowMs, 0, 2_000, "motion", errors),
            easing = if (easing in EASINGS) easing else fallback.easing
        )
    }

    private fun parseElevation(obj: JSONObject?, base: ThemeElevation?, errors: MutableList<String>): ThemeElevation {
        val fallback = base ?: ThemeDefaults.elevation
        if (obj == null) return fallback
        checkKeys(obj, ELEVATION_KEYS, "elevation", errors)
        return ThemeElevation(
            cardElevationDp = ranged(obj, "cardElevation", fallback.cardElevationDp, 0f, 24f, "elevation", errors),
            glowAlpha = ranged(obj, "glowAlpha", fallback.glowAlpha, 0f, 1f, "elevation", errors)
        )
    }

    private fun parseIconography(obj: JSONObject?, base: ThemeIconography?, errors: MutableList<String>): ThemeIconography {
        val fallback = base ?: ThemeDefaults.iconography
        if (obj == null) return fallback
        checkKeys(obj, ICON_KEYS, "iconography", errors)
        val styleRaw = obj.optString("style", fallback.style.id)
        val style = IconStyle.entries.firstOrNull { it.id == styleRaw.lowercase(Locale.US) }
        if (style == null) errors += "iconography.style must be filled or outlined"
        return ThemeIconography(
            style = style ?: fallback.style,
            strokeDp = ranged(obj, "strokeWidth", fallback.strokeDp, 1f, 8f, "iconography", errors)
        )
    }

    private fun parseBackground(obj: JSONObject?, base: ThemeBackground?, errors: MutableList<String>): ThemeBackground {
        val fallback = base ?: ThemeDefaults.background
        if (obj == null) return fallback
        checkKeys(obj, BACKGROUND_KEYS, "background", errors)
        val styleRaw = obj.optString("style", fallback.style.id)
        val style = BackgroundStyle.entries.firstOrNull { it.id == styleRaw.lowercase(Locale.US) }
        if (style == null) errors += "background.style must be solid, gradient or pattern"
        val pattern = obj.optString("pattern", fallback.pattern).lowercase(Locale.US)
        if (!PATTERN_REGEX.matches(pattern)) errors += "background.pattern must be a bundled pattern name"
        return ThemeBackground(
            style = style ?: fallback.style,
            base = parseThemeColor(obj.optString("base", "")) ?: fallback.base,
            accent = parseThemeColor(obj.optString("accent", "")) ?: fallback.accent,
            gradientAngle = intRanged(obj, "gradientAngle", fallback.gradientAngle, 0, 360, "background", errors),
            pattern = if (PATTERN_REGEX.matches(pattern)) pattern else fallback.pattern
        )
    }

    private fun parseGauge(obj: JSONObject?, base: ThemeGauge?, errors: MutableList<String>): ThemeGauge {
        val fallback = base ?: ThemeDefaults.gauge
        if (obj == null) return fallback
        checkKeys(obj, GAUGE_KEYS, "gauge", errors)
        val styleRaw = obj.optString("style", fallback.style.id)
        val style = GaugeStyle.entries.firstOrNull { it.id == styleRaw.lowercase(Locale.US) }
        if (style == null) errors += "gauge.style must be radial, bar, digital, arc or minimal"
        val needle = obj.optString("needle", fallback.needle).lowercase(Locale.US)
        if (needle !in setOf("tapered", "line", "dot")) errors += "gauge.needle must be tapered, line or dot"
        val warn = ranged(obj, "warnFraction", fallback.warnFraction, 0f, 1f, "gauge", errors)
        val critical = ranged(obj, "criticalFraction", fallback.criticalFraction, 0f, 1f, "gauge", errors)
        if (warn >= critical) errors += "gauge.warnFraction must be below criticalFraction"
        return ThemeGauge(
            style = style ?: fallback.style,
            needle = if (needle in setOf("tapered", "line", "dot")) needle else fallback.needle,
            tickCount = intRanged(obj, "tickCount", fallback.tickCount, 0, 60, "gauge", errors),
            warnFraction = warn,
            criticalFraction = critical
        )
    }

    private fun parseLayout(
        obj: JSONObject?,
        widgetPresets: JSONArray?,
        base: ThemeLayout?,
        errors: MutableList<String>
    ): ThemeLayout {
        val fallback = base ?: ThemeDefaults.layout
        val rows = if (obj?.has("launcherRows") == true)
            intRanged(obj, "launcherRows", fallback.launcherRows, 1, 8, "layout", errors) else fallback.launcherRows
        val cols = if (obj?.has("launcherCols") == true)
            intRanged(obj, "launcherCols", fallback.launcherCols, 1, 8, "layout", errors) else fallback.launcherCols
        obj?.let { checkKeys(it, LAYOUT_KEYS, "layout", errors) }

        val presets = mutableListOf<List<String>>()
        val source = widgetPresets ?: obj?.optJSONArray("widgetPresets")
        if (source != null) {
            if (source.length() > 6) errors += "At most 6 widget preset boards are allowed"
            for (i in 0 until minOf(source.length(), 6)) {
                val board = source.optJSONArray(i)
                if (board == null) {
                    errors += "widgetPresets[$i] must be an array of widget ids"
                    continue
                }
                val ids = mutableListOf<String>()
                for (j in 0 until minOf(board.length(), 12)) {
                    val widgetId = board.optString(j, "")
                    if (!ID_REGEX.matches(widgetId)) errors += "Invalid widget id in widgetPresets[$i][$j]"
                    else ids += widgetId
                }
                if (ids.isNotEmpty()) presets += ids
            }
        }
        if (presets.isEmpty()) presets += fallback.widgetPresets
        return ThemeLayout(
            launcherRows = rows,
            launcherCols = cols,
            widgetMinWidthDp = if (obj?.has("widgetMinWidth") == true)
                intRanged(obj, "widgetMinWidth", fallback.widgetMinWidthDp, 96, 400, "layout", errors) else fallback.widgetMinWidthDp,
            widgetMinHeightDp = if (obj?.has("widgetMinHeight") == true)
                intRanged(obj, "widgetMinHeight", fallback.widgetMinHeightDp, 64, 320, "layout", errors) else fallback.widgetMinHeightDp,
            widgetPresets = presets
        )
    }

    private fun checkKeys(obj: JSONObject, allowed: Set<String>, path: String, errors: MutableList<String>) {
        obj.keys().forEach { key -> if (key !in allowed) errors += "Unknown field '$path.$key'" }
    }

    private fun size(obj: JSONObject, key: String, default: Float, path: String, errors: MutableList<String>): Float =
        if (obj.has(key)) ranged(obj, key, default, 8f, 96f, path, errors) else default

    private fun ranged(
        obj: JSONObject, key: String, default: Float, min: Float, max: Float, path: String, errors: MutableList<String>
    ): Float {
        if (!obj.has(key)) return default
        val value = obj.optDouble(key, Double.NaN)
        if (value.isNaN() || value < min || value > max) {
            errors += "$path.$key must be a number between $min and $max"
            return default
        }
        return value.toFloat()
    }

    private fun intRanged(
        obj: JSONObject, key: String, default: Int, min: Int, max: Int, path: String, errors: MutableList<String>
    ): Int {
        if (!obj.has(key)) return default
        val value = obj.optInt(key, Int.MIN_VALUE)
        if (value == Int.MIN_VALUE || value < min || value > max) {
            errors += "$path.$key must be an integer between $min and $max"
            return default
        }
        return value
    }

    private fun weight(obj: JSONObject, key: String, default: Int, path: String, errors: MutableList<String>): Int {
        if (!obj.has(key)) return default
        val value = obj.optInt(key, -1)
        if (value !in 100..900) {
            errors += "$path.$key must be a font weight between 100 and 900"
            return default
        }
        return value
    }

    private fun familyFromId(raw: String, default: FontFamily): FontFamily = when (raw.lowercase(Locale.US)) {
        "", "system", "sans", "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace", "mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> default
    }

    private fun FontFamily.toFamilyId(): String = when (this) {
        FontFamily.Serif -> "serif"
        FontFamily.Monospace -> "monospace"
        FontFamily.Cursive -> "cursive"
        else -> "sans"
    }

    private val COLOR_KEYS = setOf(
        "background", "surface", "surfaceVariant", "primary", "secondary", "accent",
        "warning", "danger", "success", "textPrimary", "textSecondary", "outline",
        "gaugeTrack", "gaugeFill", "chart"
    )
    private val TYPOGRAPHY_KEYS = setOf(
        "fontFamily", "numericFont", "displayLarge", "displayMedium", "headlineLarge",
        "titleLarge", "titleMedium", "bodyLarge", "bodyMedium", "labelLarge", "labelMedium",
        "weightRegular", "weightMedium", "weightBold", "letterSpacing"
    )
    private val SHAPE_KEYS = setOf("cornerRadius", "cardCornerRadius", "borderWidth", "gaugeStroke")
    private val SPACING_KEYS = setOf("density", "itemGap", "cardPadding", "safeMargin")
    private val MOTION_KEYS = setOf("fast", "normal", "slow", "easing")
    private val ELEVATION_KEYS = setOf("cardElevation", "glowAlpha")
    private val ICON_KEYS = setOf("style", "strokeWidth")
    private val BACKGROUND_KEYS = setOf("style", "base", "accent", "gradientAngle", "pattern")
    private val GAUGE_KEYS = setOf("style", "needle", "tickCount", "warnFraction", "criticalFraction")
    private val LAYOUT_KEYS = setOf(
        "launcherRows", "launcherCols", "widgetMinWidth", "widgetMinHeight", "widgetPresets"
    )
}

/** Parses `#RRGGBB` or `#AARRGGBB`; returns null for anything else. */
fun parseThemeColor(raw: String?): Color? {
    val text = raw?.trim() ?: return null
    if (!text.startsWith("#")) return null
    val hex = text.substring(1)
    if (hex.length != 6 && hex.length != 8) return null
    if (hex.isEmpty() || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return try {
        val value = hex.toLong(16)
        if (hex.length == 6) Color(0xFF000000L or value) else Color(value)
    } catch (_: Exception) {
        null
    }
}

/** `#AARRGGBB` representation used by theme JSON and import/export. */
fun Color.toThemeHex(): String = String.format(Locale.US, "#%08X", toArgb())

/** Neutral fallback tokens: also used to lift the legacy color-only presets into full themes. */
object ThemeDefaults {
    val colors = ThemeColorTokens(
        background = Color(0xFF101417),
        surface = Color(0xFF1B2228),
        surfaceVariant = Color(0xFF2A353E),
        primary = Color(0xFFF0F4F6),
        secondary = Color(0xFF92D7BC),
        accent = Color(0xFF8BD8E7),
        warning = Color(0xFFEFC681),
        danger = Color(0xFFFF8D97),
        success = Color(0xFF9BE7A0),
        textPrimary = Color(0xFFF0F4F6),
        textSecondary = Color(0xFFAFBEC8),
        outline = Color(0xFF5B6B76),
        gaugeTrack = Color(0xFF2A353E),
        gaugeFill = Color(0xFF8BD8E7),
        chart = listOf(Color(0xFF8BD8E7), Color(0xFF92D7BC), Color(0xFFEFC681))
    )

    val typography = ThemeTypography(
        fontFamily = FontFamily.SansSerif,
        numericFontFamily = FontFamily.Monospace,
        displayLargeSp = 40f,
        displayMediumSp = 32f,
        headlineLargeSp = 24f,
        titleLargeSp = 22f,
        titleMediumSp = 18f,
        bodyLargeSp = 16f,
        bodyMediumSp = 14f,
        labelLargeSp = 14f,
        labelMediumSp = 12f,
        weightRegular = 400,
        weightMedium = 500,
        weightBold = 700,
        letterSpacingEm = 0.01f
    )

    val shapes = ThemeShapes(cornerDp = 12f, cardCornerDp = 16f, borderDp = 1f, gaugeStrokeDp = 3f)
    val spacing = ThemeSpacing(ThemeDensity.COMFORTABLE, itemGapDp = 12f, cardPaddingDp = 14f, safeMarginDp = 16f)
    val motion = ThemeMotion(fastMs = 120, normalMs = 220, slowMs = 360, easing = "ease-out")
    val elevation = ThemeElevation(cardElevationDp = 2f, glowAlpha = 0.15f)
    val iconography = ThemeIconography(IconStyle.OUTLINED, strokeDp = 2f)
    val background = ThemeBackground(
        BackgroundStyle.SOLID, base = Color(0xFF101417), accent = Color(0xFF8BD8E7),
        gradientAngle = 0, pattern = "none"
    )
    val gauge = ThemeGauge(GaugeStyle.DIGITAL, needle = "tapered", tickCount = 24, warnFraction = 0.7f, criticalFraction = 0.88f)
    val layout = ThemeLayout(
        launcherRows = 2, launcherCols = 3, widgetMinWidthDp = 150, widgetMinHeightDp = 96,
        widgetPresets = listOf(
            listOf("speed", "rpm", "coolant", "voltage", "map", "clock")
        )
    )
}

/** Compose typography/shapes derived from the token values. */
object ThemeComposeMapping {
    fun typography(tokens: ThemeTypography): Typography {
        fun style(size: Float, weight: Int = tokens.weightRegular) =
            TextStyle(
                fontFamily = tokens.fontFamily,
                fontWeight = FontWeight(weight),
                fontSize = size.sp,
                lineHeight = (size * 1.3f).sp,
                letterSpacing = (tokens.letterSpacingEm * size).sp
            )
        return Typography(
            displayLarge = style(tokens.displayLargeSp, tokens.weightBold),
            displayMedium = style(tokens.displayMediumSp, tokens.weightMedium),
            headlineLarge = style(tokens.headlineLargeSp, tokens.weightMedium),
            titleLarge = style(tokens.titleLargeSp, tokens.weightMedium),
            titleMedium = style(tokens.titleMediumSp, tokens.weightMedium),
            bodyLarge = style(tokens.bodyLargeSp),
            bodyMedium = style(tokens.bodyMediumSp),
            labelLarge = style(tokens.labelLargeSp, tokens.weightMedium),
            labelMedium = style(tokens.labelMediumSp, tokens.weightMedium)
        )
    }

    fun shapes(tokens: ThemeShapes): Shapes = Shapes(
        extraSmall = RoundedCornerShape(tokens.cornerDp.dp),
        small = RoundedCornerShape(tokens.cornerDp.dp),
        medium = RoundedCornerShape(tokens.cardCornerDp.dp),
        large = RoundedCornerShape((tokens.cardCornerDp + 4f).dp),
        extraLarge = RoundedCornerShape((tokens.cardCornerDp + 8f).dp)
    )
}
