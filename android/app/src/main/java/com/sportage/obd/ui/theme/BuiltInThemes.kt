package com.sportage.obd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/**
 * Built-in themes. These are authored as Kotlin values (not resources) so the JVM unit
 * tests and the preset picker can use them without an Android context, and so a missing
 * or corrupt resource can never take the app down. `res/raw/builtin_themes.json` mirrors
 * these values for documentation and import/export examples; a unit test keeps them in sync.
 */
object BuiltInThemes {

    // ------------------------------------------------------------------ minimal / clean

    val minimalDaily = ThemeSpec(
        id = "minimal-daily",
        name = "Minimal Daily",
        description = "Light, uncluttered daily-driver cockpit. Soft surfaces, compact numerals and minimal motion.",
        version = ThemeSchemaVersion,
        mode = ThemeMode.LIGHT,
        baseId = null,
        colors = ThemeColorTokens(
            background = Color(0xFFF4F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE7ECF0),
            primary = Color(0xFF1B2430),
            secondary = Color(0xFF52606D),
            accent = Color(0xFF0F766E),
            warning = Color(0xFFB45309),
            danger = Color(0xFFBE123C),
            success = Color(0xFF15803D),
            textPrimary = Color(0xFF141A21),
            textSecondary = Color(0xFF52606D),
            outline = Color(0xFFCBD5DB),
            gaugeTrack = Color(0xFFE2E8EC),
            gaugeFill = Color(0xFF0F766E),
            chart = listOf(Color(0xFF0F766E), Color(0xFF3B82F6), Color(0xFFB45309))
        ),
        typography = ThemeDefaults.typography.copy(
            fontFamily = FontFamily.SansSerif,
            numericFontFamily = FontFamily.SansSerif,
            displayLargeSp = 38f,
            displayMediumSp = 30f,
            headlineLargeSp = 23f,
            titleLargeSp = 20f,
            weightRegular = 400,
            weightMedium = 500,
            weightBold = 600,
            letterSpacingEm = 0.005f
        ),
        shapes = ThemeShapes(cornerDp = 14f, cardCornerDp = 20f, borderDp = 1f, gaugeStrokeDp = 6f),
        spacing = ThemeSpacing(ThemeDensity.SPACIOUS, itemGapDp = 18f, cardPaddingDp = 20f, safeMarginDp = 20f),
        motion = ThemeMotion(fastMs = 100, normalMs = 180, slowMs = 260, easing = "ease-out"),
        elevation = ThemeElevation(cardElevationDp = 3f, glowAlpha = 0f),
        iconography = ThemeIconography(IconStyle.OUTLINED, strokeDp = 1.5f),
        background = ThemeBackground(
            BackgroundStyle.SOLID, base = Color(0xFFF4F6F8), accent = Color(0xFF0F766E),
            gradientAngle = 0, pattern = "none"
        ),
        gauge = ThemeGauge(GaugeStyle.DIGITAL, needle = "line", tickCount = 12, warnFraction = 0.75f, criticalFraction = 0.9f),
        layout = ThemeLayout(
            launcherRows = 2, launcherCols = 3, widgetMinWidthDp = 160, widgetMinHeightDp = 104,
            widgetPresets = listOf(
                listOf("speed", "clock", "fuel", "voltage", "map", "connection")
            )
        )
    )

    // --------------------------------------------------------------------- cyber / neon

    val cyberNeon = ThemeSpec(
        id = "cyber-neon",
        name = "Cyber Neon",
        description = "High-contrast performance cockpit with neon accents, monospace numerals and fast glowing motion.",
        version = ThemeSchemaVersion,
        mode = ThemeMode.DARK,
        baseId = null,
        colors = ThemeColorTokens(
            background = Color(0xFF05070C),
            surface = Color(0xFF0C1220),
            surfaceVariant = Color(0xFF16233A),
            primary = Color(0xFFE8FBFF),
            secondary = Color(0xFFFF4FD8),
            accent = Color(0xFF22D3EE),
            warning = Color(0xFFFFC14D),
            danger = Color(0xFFFF4D6D),
            success = Color(0xFF4DFFB8),
            textPrimary = Color(0xFFE8FBFF),
            textSecondary = Color(0xFF8FA9C4),
            outline = Color(0xFF2A3F5C),
            gaugeTrack = Color(0xFF12203A),
            gaugeFill = Color(0xFF22D3EE),
            chart = listOf(Color(0xFF22D3EE), Color(0xFFFF4FD8), Color(0xFF4DFFB8))
        ),
        typography = ThemeDefaults.typography.copy(
            fontFamily = FontFamily.SansSerif,
            numericFontFamily = FontFamily.Monospace,
            displayLargeSp = 44f,
            displayMediumSp = 34f,
            headlineLargeSp = 25f,
            titleLargeSp = 22f,
            weightRegular = 400,
            weightMedium = 600,
            weightBold = 800,
            letterSpacingEm = 0.04f
        ),
        shapes = ThemeShapes(cornerDp = 4f, cardCornerDp = 8f, borderDp = 1f, gaugeStrokeDp = 10f),
        spacing = ThemeSpacing(ThemeDensity.COMPACT, itemGapDp = 8f, cardPaddingDp = 10f, safeMarginDp = 12f),
        motion = ThemeMotion(fastMs = 80, normalMs = 140, slowMs = 220, easing = "linear"),
        elevation = ThemeElevation(cardElevationDp = 0f, glowAlpha = 0.55f),
        iconography = ThemeIconography(IconStyle.OUTLINED, strokeDp = 1f),
        background = ThemeBackground(
            BackgroundStyle.GRADIENT, base = Color(0xFF05070C), accent = Color(0xFF0E2A3A),
            gradientAngle = 135, pattern = "grid"
        ),
        gauge = ThemeGauge(GaugeStyle.ARC, needle = "line", tickCount = 32, warnFraction = 0.72f, criticalFraction = 0.88f),
        layout = ThemeLayout(
            launcherRows = 3, launcherCols = 4, widgetMinWidthDp = 132, widgetMinHeightDp = 88,
            widgetPresets = listOf(
                listOf("rpm", "speed", "rpm_graph", "speed_graph", "coolant", "voltage", "gear")
            )
        )
    )

    // ------------------------------------------------------------------- classic / analog

    val classicAnalog = ThemeSpec(
        id = "classic-analog",
        name = "Classic Analog",
        description = "Warm, analog-inspired cluster: serif labels, radial dials and unhurried motion.",
        version = ThemeSchemaVersion,
        mode = ThemeMode.DARK,
        baseId = null,
        colors = ThemeColorTokens(
            background = Color(0xFF17130F),
            surface = Color(0xFF221C16),
            surfaceVariant = Color(0xFF322920),
            primary = Color(0xFFF3E7D4),
            secondary = Color(0xFFC9A46A),
            accent = Color(0xFFE0B36A),
            warning = Color(0xFFD98A3D),
            danger = Color(0xFFC4513E),
            success = Color(0xFF8FA76A),
            textPrimary = Color(0xFFF5EAD8),
            textSecondary = Color(0xFFC4B49A),
            outline = Color(0xFF5A4A38),
            gaugeTrack = Color(0xFF3A2F24),
            gaugeFill = Color(0xFFE0B36A),
            chart = listOf(Color(0xFFE0B36A), Color(0xFFC4513E), Color(0xFF8FA76A))
        ),
        typography = ThemeDefaults.typography.copy(
            fontFamily = FontFamily.Serif,
            numericFontFamily = FontFamily.Monospace,
            displayLargeSp = 36f,
            displayMediumSp = 28f,
            headlineLargeSp = 22f,
            titleLargeSp = 20f,
            weightRegular = 400,
            weightMedium = 500,
            weightBold = 700,
            letterSpacingEm = 0.03f
        ),
        shapes = ThemeShapes(cornerDp = 18f, cardCornerDp = 28f, borderDp = 2f, gaugeStrokeDp = 4f),
        spacing = ThemeSpacing(ThemeDensity.COMFORTABLE, itemGapDp = 14f, cardPaddingDp = 18f, safeMarginDp = 16f),
        motion = ThemeMotion(fastMs = 240, normalMs = 420, slowMs = 700, easing = "ease-in-out"),
        elevation = ThemeElevation(cardElevationDp = 6f, glowAlpha = 0.18f),
        iconography = ThemeIconography(IconStyle.FILLED, strokeDp = 2.5f),
        background = ThemeBackground(
            BackgroundStyle.PATTERN, base = Color(0xFF17130F), accent = Color(0xFF2A2119),
            gradientAngle = 0, pattern = "leather"
        ),
        gauge = ThemeGauge(GaugeStyle.RADIAL, needle = "tapered", tickCount = 60, warnFraction = 0.75f, criticalFraction = 0.9f),
        layout = ThemeLayout(
            launcherRows = 2, launcherCols = 3, widgetMinWidthDp = 170, widgetMinHeightDp = 120,
            widgetPresets = listOf(
                listOf("rpm", "speed", "coolant", "fuel", "voltage", "clock")
            )
        )
    )

    // -------------------------------------------------------------------- off-road / rugged

    val offroadRugged = ThemeSpec(
        id = "offroad-rugged",
        name = "Off-Road Rugged",
        description = "Maximum-legibility utility theme: thick borders, big bold numerals and a topographic background.",
        version = ThemeSchemaVersion,
        mode = ThemeMode.DARK,
        baseId = null,
        colors = ThemeColorTokens(
            background = Color(0xFF101408),
            surface = Color(0xFF1B2110),
            surfaceVariant = Color(0xFF2A331A),
            primary = Color(0xFFF4FFE6),
            secondary = Color(0xFFB6D26A),
            accent = Color(0xFFA8E05F),
            warning = Color(0xFFFFB020),
            danger = Color(0xFFFF5340),
            success = Color(0xFF7BE07B),
            textPrimary = Color(0xFFFBFFF2),
            textSecondary = Color(0xFFBECC9C),
            outline = Color(0xFF4C5A30),
            gaugeTrack = Color(0xFF2A331A),
            gaugeFill = Color(0xFFA8E05F),
            chart = listOf(Color(0xFFA8E05F), Color(0xFFFFB020), Color(0xFF7BE07B))
        ),
        typography = ThemeDefaults.typography.copy(
            fontFamily = FontFamily.SansSerif,
            numericFontFamily = FontFamily.Monospace,
            displayLargeSp = 46f,
            displayMediumSp = 36f,
            headlineLargeSp = 26f,
            titleLargeSp = 23f,
            weightRegular = 500,
            weightMedium = 700,
            weightBold = 900,
            letterSpacingEm = 0.02f
        ),
        shapes = ThemeShapes(cornerDp = 2f, cardCornerDp = 6f, borderDp = 2f, gaugeStrokeDp = 8f),
        spacing = ThemeSpacing(ThemeDensity.SPACIOUS, itemGapDp = 16f, cardPaddingDp = 16f, safeMarginDp = 24f),
        motion = ThemeMotion(fastMs = 90, normalMs = 150, slowMs = 240, easing = "ease"),
        elevation = ThemeElevation(cardElevationDp = 0f, glowAlpha = 0.1f),
        iconography = ThemeIconography(IconStyle.FILLED, strokeDp = 3f),
        background = ThemeBackground(
            BackgroundStyle.PATTERN, base = Color(0xFF101408), accent = Color(0xFF243014),
            gradientAngle = 0, pattern = "topo"
        ),
        gauge = ThemeGauge(GaugeStyle.BAR, needle = "dot", tickCount = 16, warnFraction = 0.68f, criticalFraction = 0.85f),
        layout = ThemeLayout(
            launcherRows = 2, launcherCols = 2, widgetMinWidthDp = 180, widgetMinHeightDp = 120,
            widgetPresets = listOf(
                listOf("speed", "rpm", "coolant", "voltage", "gps", "map"),
                listOf("trip_distance", "trip_duration", "connection")
            )
        )
    )

    // ------------------------------------------------------------------- night / low-glare

    val nightLowGlare = ThemeSpec(
        id = "night-lowglare",
        name = "Night Low-Glare",
        description = "Dim, warm long-distance theme that protects night vision with low-glare amber tones.",
        version = ThemeSchemaVersion,
        mode = ThemeMode.DARK,
        baseId = null,
        colors = ThemeColorTokens(
            background = Color(0xFF070607),
            surface = Color(0xFF100D0C),
            surfaceVariant = Color(0xFF1C1715),
            primary = Color(0xFFE8C9A0),
            secondary = Color(0xFFB98A5A),
            accent = Color(0xFFC98A3F),
            warning = Color(0xFFD6952F),
            danger = Color(0xFFC25B3A),
            success = Color(0xFF9B9B5A),
            textPrimary = Color(0xFFD9B98F),
            textSecondary = Color(0xFF9C8265),
            outline = Color(0xFF3A3028),
            gaugeTrack = Color(0xFF241D19),
            gaugeFill = Color(0xFFC98A3F),
            chart = listOf(Color(0xFFC98A3F), Color(0xFFC25B3A), Color(0xFF9B9B5A))
        ),
        typography = ThemeDefaults.typography.copy(
            fontFamily = FontFamily.SansSerif,
            numericFontFamily = FontFamily.Monospace,
            displayLargeSp = 40f,
            displayMediumSp = 32f,
            headlineLargeSp = 24f,
            titleLargeSp = 21f,
            weightRegular = 400,
            weightMedium = 500,
            weightBold = 700,
            letterSpacingEm = 0.02f
        ),
        shapes = ThemeShapes(cornerDp = 10f, cardCornerDp = 14f, borderDp = 1f, gaugeStrokeDp = 3f),
        spacing = ThemeSpacing(ThemeDensity.COMPACT, itemGapDp = 10f, cardPaddingDp = 12f, safeMarginDp = 14f),
        motion = ThemeMotion(fastMs = 200, normalMs = 320, slowMs = 480, easing = "ease-in-out"),
        elevation = ThemeElevation(cardElevationDp = 0f, glowAlpha = 0.05f),
        iconography = ThemeIconography(IconStyle.OUTLINED, strokeDp = 1.5f),
        background = ThemeBackground(
            BackgroundStyle.SOLID, base = Color(0xFF070607), accent = Color(0xFF1C1715),
            gradientAngle = 0, pattern = "none"
        ),
        gauge = ThemeGauge(GaugeStyle.MINIMAL, needle = "line", tickCount = 8, warnFraction = 0.8f, criticalFraction = 0.92f),
        layout = ThemeLayout(
            launcherRows = 2, launcherCols = 3, widgetMinWidthDp = 160, widgetMinHeightDp = 96,
            widgetPresets = listOf(
                listOf("speed", "rpm", "clock", "fuel", "connection", "voltage")
            )
        )
    )

    val all: List<ThemeSpec> = listOf(minimalDaily, cyberNeon, classicAnalog, offroadRugged, nightLowGlare)

    private val cache = HashMap<String, ThemeSpec>()

    /** Drops the resolution cache after the imported theme set changes. */
    @Synchronized
    fun invalidateCache() = cache.clear()

    fun byId(id: String?): ThemeSpec? = all.firstOrNull { it.id == id }

    /** Full spec for any preset id; legacy color-only presets get neutral tokens. */
    fun specFor(preset: CockpitThemePreset): ThemeSpec = byId(preset.id) ?: fromLegacyColors(preset)

    fun fromLegacyColors(preset: CockpitThemePreset): ThemeSpec {
        val c = preset.colors
        return ThemeSpec(
            id = preset.id,
            name = preset.title,
            description = preset.description,
            version = ThemeSchemaVersion,
            mode = if (c.isDark) ThemeMode.DARK else ThemeMode.LIGHT,
            baseId = null,
            colors = ThemeColorTokens(
                background = c.background,
                surface = c.surface,
                surfaceVariant = c.surfaceElevated,
                primary = c.textPrimary,
                secondary = c.secondary,
                accent = c.accent,
                warning = c.warning,
                danger = c.critical,
                success = Color(0xFF9BE7A0),
                textPrimary = c.textPrimary,
                textSecondary = c.textSecondary,
                outline = c.textSecondary.copy(alpha = 0.5f),
                gaugeTrack = c.surfaceElevated,
                gaugeFill = c.accent,
                chart = listOf(c.accent, c.secondary, c.warning)
            ),
            typography = ThemeDefaults.typography,
            shapes = ThemeDefaults.shapes,
            spacing = ThemeDefaults.spacing,
            motion = ThemeDefaults.motion,
            elevation = ThemeDefaults.elevation,
            iconography = ThemeDefaults.iconography,
            background = ThemeDefaults.background.copy(base = c.background, accent = c.accent),
            gauge = ThemeDefaults.gauge.copy(warnFraction = 0.7f, criticalFraction = 0.88f),
            layout = ThemeDefaults.layout
        )
    }

    /**
     * Resolves theme id + user overrides into a complete spec. Results are cached because
     * [SportageObdTheme] recomposes on every OBD frame and must not re-allocate tokens.
     */
    @Synchronized
    fun resolved(
        themeId: String?,
        accentOverride: String?,
        densityOverride: String?,
        gaugeStyleOverride: String?
    ): ThemeSpec {
        val key = "${themeId.orEmpty()}|${accentOverride.orEmpty()}|${densityOverride.orEmpty()}|${gaugeStyleOverride.orEmpty()}"
        cache[key]?.let { return it }
        var spec = ImportedThemes.byId(themeId) ?: specFor(CockpitThemes.byId(themeId))
        CockpitThemes.parseAccent(accentOverride)?.let { accent ->
            spec = spec.withColors(spec.colors.copy(accent = accent, gaugeFill = accent))
        }
        if (!densityOverride.isNullOrBlank()) spec = spec.withDensity(ThemeDensity.fromId(densityOverride))
        if (!gaugeStyleOverride.isNullOrBlank()) spec = spec.withGaugeStyle(GaugeStyle.fromId(gaugeStyleOverride))
        if (cache.size > 64) cache.clear()
        cache[key] = spec
        return spec
    }
}
