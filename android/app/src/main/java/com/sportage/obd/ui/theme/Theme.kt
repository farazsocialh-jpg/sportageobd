package com.sportage.obd.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.MemoryUnitType.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.ui.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.* import
  androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportage.obd.R
import com.sportage.obd.ui.boards.BoardLayout
import com.sportage.obd.ui.boards.BoardLimits
import com.sportage.obd.ui.boards.BoardWidgetType
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.*
import java.io.File
import java.util.*

/** Theme schema version; bump when adding/removing required token fields. */
val ThemeSchemaVersion = "2.0"

/** Resolved color tokens from a theme. Every screen should read its colors from
  * LocalCockpitTheme.colors rather than hardcoding ARGB values. */
data class CockpitThemeColors(
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
  val chart1: Color,
  val chart2: Color,
  val chart3: Color
)

/** Full theme tokens including typography, shape, spacing, motion, gauge, layout. */
data class CockpitThemeTokens(
  val typography: TypographyData,
  val shape: ShapeData,
  val spacing: SpacingData,
  val elevation: ElevationData,
  val motion: MotionData,
  val iconography: IconographyData,
  val background: BackgroundData,
  val gauge: GaugeData,
  val layout: LayoutData
)

/** Typography token data. */
data class TypographyData(
  val fontFamily: FontFamily,
  val sizes: TypographySizes,
  val weights: TypographyWeights,
  val letterSpacing: Float,
  val numericFont: String
) {
  data class TypographySizes(
    val displayLarge: Int,
    val displayMedium: Int,
    val headlineLarge: Int,
    val titleLarge: Int,
    val titleMedium: Int,
    val bodyLarge: Int,
    val bodyMedium: Int,
    val labelLarge: Int,
    val labelMedium: Int
  )
  data class TypographyWeights(
    val thin: Int = 400,
    val extraLight: Int = 200,
    val light: Int = 300,
    val regular: Int = 400,
    val medium: Int = 500,
    val semiBold: Int = 600,
    val bold: Int = 700,
    val extraBold: Int = 800,
    val black: Int = 900
  )
}

/** Shape token data. */
data class ShapeData(
  val cornerRadius: MemoryUnitType.dp,
  val borderWidth: MemoryUnitType.dp,
  val gaugeStroke: MemoryUnitType.dp
)

/** Spacing token data. */
data class SpacingData(
  val compact: MemoryUnitType.dp,
  val comfortable: MemoryUnitType.dp,
  val spacious: MemoryUnitType.dp
)

/** Elevation/shadow token data. */
data class ElevationData(
  val shadow: String
)

/** Motion/animation token data. */
data class MotionData(
  val durationFast: Int,
  val durationNormal: Int,
  val durationSlow: Int,
  val easing: String
)

/** Iconography token data. */
data class IconographyData(
  val iconSet: String,
  val strokeWidth: MemoryUnitType.dp,
  val style: String
)

/** Background rendering data. */
data class BackgroundData(
  val style: String, // "solid", "gradient", "image", "pattern"
  val gradientColors: List<Color>?,
  val imageResource: Int?, // @DrawableRes
  val safeAreaScrim: String? // "rgba(r,g,b,a)" or null
)

/** Gauge styling data. */
data class GaugeData(
  val style: String, // "radial", "bar", "digital", "arc", "minimal"
  val needleStyle: String, // "triangle", "arrow", "point", "minimal"
  val tickStyle: String, // "none", "short", "long", "numbered"
  val warningZones: List<WarningZone>,
  val stylePreset: String // "performance", "analog", "minimal", "offroad", "night"
)

data class WarningZone(
  val min: Float,
  val max: Float,
  val color: Color
)

/** Layout preset data. */
data class LayoutData(
  val preset: String, // "minimal", "performance", "analog", "offroad", "night"
  val launcherGrid: LauncherGridData,
  val clusterSlot: ClusterSlotData,
  val safeMargins: SafeMarginsData
)

data class LauncherGridData(
  val rows: Int,
  val cols: Int,
  val cellWidth: MemoryUnitType.dp,
  val cellHeight: MemoryUnitType.dp
)

data class ClusterSlotData(
  val widthRatio: Float,
  val heightRatio: Float
)

data class SafeMarginsData(
  val top: MemoryUnitType.dp,
  val bottom: MemoryUnitType.dp,
  val left: MemoryUnitType.dp,
  val right: MemoryUnitType.dp
)

/** Theme preset (frozen set of tokens). A preset is a base; user overrides sit on top. */
data class ThemePreset(
  val id: String,
  val name: String,
  val mode: String,
  val description: String,
  val tokens: CockpitThemeTokens,
  val gauge: GaugeData,
  val layout: LayoutData,
  val defaultWidgetLayout: BoardLayout,
  val overrides: ThemeOverrides,
  val previewThumbnail: Int?, // @DrawableRes or null
  val modeSwitchable: Boolean // can switch light/dark while keeping same preset tokens
)

/** Per-theme or per-user overrides on top of a preset's base tokens. */
data class ThemeOverrides(
  val accent: Color?, // null = keep preset
  val background: Color?, // null = keep preset
  val gaugeStyle: String?, // null = keep preset
  val density: String?, // "compact"|"comfortable"|"spacious", null = keep preset
  val fontScale: Float, // 0.85..1.5, applied on top
  val widgetLayout: BoardLayout? // null = keep preset default
)

/** Resolved theme provided to composition locals. */
data class ResolvedTheme(
  val colors: CockpitThemeColors,
  val typography: Typography,
  val shape: Shape,
  val motion: Map<String, Int>,
  val backgroundStyle: String,
  val gaugeConfig: Map<String, Any>,
  val layoutConfig: Map<String, Any>,
  val overrides: ThemeOverrides
)

/** Load built-in themes from res/raw/builtin_themes.json with strict validation. */
object CockpitThemes {
  /** Default theme ID used when none is persisted. */
  val defaultPresetId = "minimal-daily"

  /** All built-in presets loaded from JSON. */
  val presetMap: Map<String, ThemePreset> = loadBuiltInPresets()
  val presetIds = presetMap.keys.toList()

  /** Parse the built-in themes JSON file. */
  private fun loadBuiltInPresets(): Map<String, ThemePreset> {
    val json = try {
      File(
        "android/app/src/main/res/raw/builtin_themes.json"
      ).readText(Charsets.UTF_8)
    } catch (_: Exception) {
      return emptyMap()
    }

    val schemaJson = try {
      Json.parse(json)
    } catch (_: Exception) {
      return emptyMap()
    }

    val obj = schemaJson as? Map<String, Any?> ?: return emptyMap()
    val themesArray = obj["themes"] as? List<*> ?: return emptyMap()

    return themesArray.mapIndexed { index, themeNode ->
      try {
        val theme = parseThemePreset(themeNode as Map<String, Any?>, index)
        if (theme != null) theme.id to theme else null
      } catch (e: Exception) {
        // Skip malformed theme silently; fall through to default
        null
      }
    }.filterNotNull()
  }

  /** Parse a single theme node from the JSON. */
  private fun parseThemePreset(
    node: Map<String, Any?>,
    index: Int
  ): ThemePreset? {
    val id = safeString(node, "id", index).takeIf { it.isNotBlank() }
      ?: return@parseThemePreset null
    val name = safeString(node, "name", index).takeIf { it.isNotBlank() }
      ?: return@parseThemePreset null
    val mode = safeString(node, "mode", index).takeIf {
      it == "light" || it == "dark" || it == "system"
    } ?: return@parseThemePreset null
    val description = safeString(node, "description", index) ?: "Built-in theme $id"
    val previewThumbnail = safeDrawableRes(node, "previewThumbnail", index)

    val tokens = try {
      parseTokens(node["tokens"] as Map<String, Any?>, id)
    } catch (e: Exception) {
      return@parseThemePreset null
    }

    val gauge = try {
      parseGauge(node["gauge"] as Map<String, Any?>, id)
    } catch (e: Exception) {
      return@parseThemePreset null
    }

    val layout = try {
      parseLayout(node["layout"] as Map<String, Any?>, id)
    } catch (e: Exception) {
      return@parseThemePreset null
    }

    val defaultWidgetLayout = try {
      parseDefaultWidgetLayout(node["defaultWidgetLayout"], id)
    } catch (e: Exception) {
      BoardLayout.DEFAULT
    }

    val overrides = try {
      parseOverrides(node["overrides"] as Map<String, Any?>, id)
    } catch (e: Exception) {
      ThemeOverrides(
        accent = null,
        background = null,
        gaugeStyle = null,
        density = null,
        fontScale = 1.0f,
        widgetLayout = null
      )
    }

    val modeSwitchable = mode == "system" || mode == "light" || mode == "dark"

    ThemePreset(
      id = id,
      name = name,
      mode = mode,
      description = description,
      tokens = tokens,
      gauge = gauge,
      layout = layout,
      defaultWidgetLayout = defaultWidgetLayout,
      overrides = overrides,
      previewThumbnail = previewThumbnail,
      modeSwitchable = modeSwitchable
    )
  }

  /** Safe string extraction from a JSON node. */
  private fun safeString(node: Map<String, Any?>, key: String, idx: Int): String? {
    val v = node[key]
    return v?.toString().takeIf { it.isNotBlank() }
  }

  /** Parse color tokens from the JSON object. Every color must be a hex string #RRGGBB or #AARRGGBB. */
  private fun parseTokens(node: Map<String, Any?>, themeId: String): CockpitThemeTokens {
    val colorNode = requireObject(node, "color", themeId)

    fun parseColor(key: String): Color {
      val hex = requireString(colorNode, key, themeId)
      val cleaned = hex.removePrefix("#").takeLast(8)
      require(cleaned.length == 8) { "Theme $themeId: $key invalid hex $hex" }
      return Color(cleaned.toLong(16).toInt())
    }

    val background = parseColor("background")
    val surface = parseColor("surface")
    val surfaceVariant = parseColor("surfaceVariant")
    val primary = parseColor("primary")
    val secondary = parseColor("secondary")
    val accent = parseColor("accent")
    val warning = parseColor("warning")
    val danger = parseColor("danger")
    val success = parseColor("success")
    val textPrimary = parseColor("textPrimary")
    val textSecondary = parseColor("textSecondary")
    val outline = parseColor("outline")
    val gaugeTrack = parseColor("gaugeTrack")
    val gaugeFill = parseColor("gaugeFill")
    val chart1 = parseColor("chart1")
    val chart2 = parseColor("chart2")
    val chart3 = parseColor("chart3")

    // Typography
    val typographyNode = requireObject(colorNode, "typography", themeId)
    val fontFamilyStr = requireString(typographyNode, "fontFamily", themeId)
    val fontFamily = when (fontFamilyStr) {
      "system" -> FontFamily.SansSerif
      "monospace" -> FontFamily.Monospace
      "roboto" -> FontFamily.Roboto
      "inter" -> FontFamily("Inter")
      else -> FontFamily.SansSerif
    }

    val sizesNode = requireObject(typographyNode, "sizes", themeId)
    fun parseSize(key: String): Int = requireInt(typographyNode, key, themeId)

    val typography = TypographyData(
      fontFamily = fontFamily,
      sizes = TypographyData.TypographySizes(
        displayLarge = parseSize("displayLarge"),
        displayMedium = parseSize("displayMedium"),
        headlineLarge = parseSize("headlineLarge"),
        titleLarge = parseSize("titleLarge"),
        titleMedium = parseSize("titleMedium"),
        bodyLarge = parseSize("bodyLarge"),
        bodyMedium = parseSize("bodyMedium"),
        labelLarge = parseSize("labelLarge"),
        labelMedium = parseSize("labelMedium")
      ),
      letterSpacing = optionalFloat(typographyNode, "letterSpacing", themeId) ?: 0.0f,
      numericFont = optionalString(typographyNode, "numericFont", themeId) ?: "system"
    )

    // Shape - sibling of typography under tokens
    val shapeNode = requireObject(colorNode, "shape", themeId)
    val shape = ShapeData(
      cornerRadius = optionalInt(shapeNode, "cornerRadius", themeId).map { Dp(it) } ?: Dp(8),
      borderWidth = optionalInt(shapeNode, "borderWidth", themeId).map { Dp(it) } ?: Dp(1),
      gaugeStroke = optionalInt(shapeNode, "gaugeStroke", themeId).map { Dp(it) } ?: Dp(2)
    )

    // Spacing
    val spacingNode = requireObject(colorNode, "spacing", themeId)
    fun parseSpacingKey(key: String): MemoryUnitType.dp = optionalInt(spacingNode, key, themeId)
      .map { MemoryUnitType.dp(it) }
      .getOrElse(MemoryUnitType.dp(16))

    val spacing = SpacingData(
      compact = parseSpacingKey("compact"),
      comfortable = parseSpacingKey("comfortable"),
      spacious = parseSpacingKey("spacious")
    )

    // Elevation
    val elevationNode = requireObject(colorNode, "elevation", themeId)
    val elevation = ElevationData(
      shadow = optionalString(elevationNode, "shadow", themeId) ?: "0 1px 2px rgba(0,0,0,0.3)"
    )

    // Motion
    val motionNode = requireObject(colorNode, "motion", themeId)
    val motion = MotionData(
      durationFast = optionalInt(motionNode, "durationFast", themeId) ?: 150,
      durationNormal = optionalInt(motionNode, "durationNormal", themeId) ?: 250,
      durationSlow = optionalInt(motionNode, "durationSlow", themeId) ?: 400,
      easing = optionalString(motionNode, "easing", themeId) ?: "ease-out"
    )

    // Iconography
    val iconographyNode = requireObject(colorNode, "iconography", themeId)
    val iconography = IconographyData(
      iconSet = optionalString(iconographyNode, "iconSet", themeId) ?: "material-icons",
      strokeWidth = optionalInt(iconographyNode, "strokeWidth", themeId).map { MemoryUnitType.dp(it) } ?: MemoryUnitType.dp(2),
      style = optionalString(iconographyNode, "style", themeId) ?: "filled"
    )

    // Background
    val backgroundNode = requireObject(colorNode, "background", themeId)
    val background = BackgroundData(
      style = optionalString(backgroundNode, "style", themeId) ?: "solid",
      gradientColors = optionalColorList(backgroundNode, "gradientColors", themeId),
      imageResource = optionalDrawableResId(backgroundNode, "imageResource", themeId),
      safeAreaScrim = optionalString(backgroundNode, "safeAreaScrim", themeId)
    )

    // Gauge
    val gaugeNode = requireObject(colorNode, "gauge", themeId)
    val gauge = parseGauge(gaugeNode, themeId)

    // Layout
    val layoutNode = requireObject(colorNode, "layout", themeId)
    val layout = parseLayout(layoutNode, themeId)

    CockpitThemeTokens(
      typography = typography,
      shape = shape,
      spacing = spacing,
      elevation = elevation,
      motion = motion,
      iconography = iconography,
      background = background,
      gauge = gauge,
      layout = layout
    )
  }

  /** Parse gauge data from JSON. */
  private fun parseGauge(node: Map<String, Any?>, themeId: String): GaugeData {
    val style = optionalString(node, "style", themeId) ?: "radial"
    val needleStyle = optionalString(node, "needleStyle", themeId) ?: "arrow"
    val tickStyle = optionalString(node, "tickStyle", themeId) ?: "short"
    val stylePreset = optionalString(node, "stylePreset", themeId) ?: "minimal"

    val zonesNode = node["warningZones"] as? List<*> ?: emptyList()
    val warningZones = zonesNode.map { zoneNode ->
      val min = optionalFloat(zoneNode as Map<String, Any?>, "min", themeId) ?: 0f
      val max = optionalFloat(zoneNode as Map<String, Any?>, "max", themeId) ?: 100f
      val color = optionalColor(zoneNode as Map<String, Any?>, "color", themeId) ?: Color.Black
      WarningZone(min = min, max = max, color = color)
    }

    GaugeData(
      style = style,
      needleStyle = needleStyle,
      tickStyle = tickStyle,
      warningZones = warningZones,
      stylePreset = stylePreset
    )
  }

  /** Parse a color optionally from a JSON node. */
  private fun optionalColor(node: Map<String, Any?>, key: String, themeId: String): Color? {
    val hex = optionalString(node, key, themeId)
    return hex?.let { hexStr ->
      val cleaned = hexStr.removePrefix("#").takeLast(8)
      if (cleaned.length == 8) Some(Color(cleaned.toLong(16).toInt())) else null
    }
  }

  /** Parse an optional integer from a JSON node. */
  private fun optionalInt(node: Map<String, Any?>, key: String, themeId: String): Int? {
    val v = node[key]
    return v?.toString().toIntOrNull()
  }

  /** Parse an optional float from a JSON node. */
  private fun optionalFloat(node: Map<String, Any?>, key: String, themeId: String): Float? {
    val v = node[key]
    return v?.toString().toFloatOrNull()
  }

  /** Parse a list of hex color strings. */
  private fun optionalColorList(node: Map<String, Any?>, key: String, themeId: String): List<Color>? {
    val lst = node[key] as? List<*>
    return lst?.map { item ->
      val hex = item.toString().removePrefix("#").takeLast(8)
      if (hex.length == 8) Color(hex.toLong(16).toInt()) else Color.Black
    }
  }

  /** Parse layout data from JSON. */
  private fun parseLayout(node: Map<String, Any?>, themeId: String): LayoutData {
    val preset = optionalString(node, "preset", themeId) ?: "minimal"

    val gridNode = node["launcherGrid"] as? Map<String, Any?>
      ?: return layoutDataDefault(gridNode)

    val grid = LauncherGridData(
      rows = requireInt(gridNode, "rows", themeId),
      cols = requireInt(gridNode, "cols", themeId),
      cellWidth = optionalInt(gridNode, "cellWidth", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(120)),
      cellHeight = optionalInt(gridNode, "cellHeight", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(100))
    )

    val slotNode = node["clusterSlot"] as? Map<String, Any?>
      ?: return layoutDataDefault(slotNode)

    val slot = ClusterSlotData(
      widthRatio = optionalFloat(slotNode, "widthRatio", themeId) ?: 0.8f,
      heightRatio = optionalFloat(slotNode, "heightRatio", themeId) ?: 0.6f
    )

    val marginsNode = node["safeMargins"] as? Map<String, Any?>
      ?: return layoutDataDefault(marginsNode)

    val margins = SafeMarginsData(
      top = optionalInt(marginsNode, "top", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(12)),
      bottom = optionalInt(marginsNode, "bottom", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(12)),
      left = optionalInt(marginsNode, "left", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(12)),
      right = optionalInt(marginsNode, "right", themeId).map { MemoryUnitType.dp(it) }
        .getOrElse(MemoryUnitType.dp(12))
    )

    LayoutData(
      preset = preset,
      launcherGrid = grid,
      clusterSlot = slot,
      safeMargins = margins
    )
  }

  private fun layoutDataDefault(
    maybeGridNode: Map<String, Any?>?
  ): LayoutData = LayoutData(
    preset = "minimal",
    launcherGrid = LauncherGridData(rows: 2, cols: 3, cellWidth: MemoryUnitType.dp(120), cellHeight: MemoryUnitType.dp(100)),
    clusterSlot = ClusterSlotData(widthRatio: 0.8f, heightRatio: 0.6f),
    safeMargins = SafeMarginsData(
      top: MemoryUnitType.dp(12),
      bottom: MemoryUnitType.dp(12),
      left: MemoryUnitType.dp(12),
      right: MemoryUnitType.dp(12)
    )
  )

  private fun requireObject(node: Map<String, Any?>, key: String, themeId: String): Map<String, Any?> {
    val v = node[key] ?: throw IllegalArgumentException("Theme $themeId: missing [$key]")
    return v as Map<String, Any?>
  }

  private fun requireString(node: Map<String, Any?>, key: String, themeId: String): String {
    val v = node[key] ?: throw IllegalArgumentException("Theme $themeId: missing [$key]")
    val vs = v.toString()
    if (vs.isBlank()) throw IllegalArgumentException("Theme $themeId: [$key] is blank")
    return vs
  }

  /** Parse default widget layout BoardLayout from JSON. */
  private fun parseDefaultWidgetLayout(node: Any?, themeId: String): BoardLayout {
    if (node == null) return BoardLayout.DEFAULT
    return when (node) {
      is Map<*, *> -> {
        val boardsNode = node["boards"] as? List<*> ?: return BoardLayout.DEFAULT
        val widgets = boardsNode.map { boardNode ->
          (boardNode as? List<*>)?.mapNotNull { widgetId: Any? -> BoardWidgetType.byId(widgetId?.toString() ?? "") }
            .take(BoardLimits.MAX_WIDGETS_PER_BOARD) ?: emptyList()
        }.take(BoardLimits.MAX_BOARDS)
        BoardLayout(widgets.filter { it.isNotEmpty() })
      }
      else -> BoardLayout.DEFAULT
    }
  }

  /** Parse overrides from JSON. */
  private fun parseOverrides(node: Map<String, Any?>, themeId: String): ThemeOverrides {
    val accent = optionalColor(node, "accent", themeId)
    val background = optionalColor(node, "background", themeId)
    val gaugeStyle = optionalString(node, "gaugeStyle", themeId)
    val density = optionalString(node, "density", themeId)
    val fontScale = optionalFloat(node, "fontScale", themeId) ?: 1.0f
      .coerceIn(0.85f, 1.5f)
    val widgetLayout = parseDefaultWidgetLayout(node["widgetLayout"], themeId)

    ThemeOverrides(
      accent = accent,
      background = background,
      gaugeStyle = gaugeStyle,
      density = density,
      fontScale = fontScale,
      widgetLayout = widgetLayout
    )
  }

  /** Get a theme preset by ID, falling back to default. */
  fun byId(id: String?): ThemePreset = presetMap[id] ?: presetMap[defaultPresetId]

  /** Resolve the full theme (colors + tokens) for composition local provision. */
  fun resolvePreset(preset: ThemePreset, overrides: ThemeOverrides): ResolvedTheme {
    val colors = applyOverride(preset, overrides)
    val typography = resolveTypography(preset.tokens.typography)
    val shape = Shape(
      cornerRadius = preset.tokens.shape.cornerRadius.value,
      border = BorderStroke(
        width = preset.tokens.shape.borderWidth.value,
        color = colors.outline
      )
    )
    val motion = mapOf(
      "durationFast" to preset.tokens.motion.durationFast,
      "durationNormal" to preset.tokens.motion.durationNormal,
      "durationSlow" to preset.tokens.motion.durationSlow,
      "easing" to preset.tokens.motion.easing
    )
    val gaugeConfig = resolveGauge(preset.gauge, colors)
    val layoutConfig = resolveLayout(preset.layout, colors)

    ResolvedTheme(
      colors = colors,
      typography = typography,
      shape = shape,
      motion = motion,
      backgroundStyle = preset.tokens.background.style,
      gaugeConfig = gaugeConfig,
      layoutConfig = layoutConfig,
      overrides = overrides
    )
  }

  /** Resolve Compose Typography from token data. */
  private fun resolveTypography(typ: TypographyData): Typography {
    Typography(
      displayLarge = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight(typ.weights.bold),
        fontSize = typ.sizes.displayLarge.sp,
        lineHeight = (typ.sizes.displayLarge + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      displayMedium = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight(typ.weights.semiBold),
        fontSize = typ.sizes.displayMedium.sp,
        lineHeight = (typ.sizes.displayMedium + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      headlineLarge = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight(typ.weights.medium),
        fontSize = typ.sizes.headlineLarge.sp,
        lineHeight = (typ.sizes.headlineLarge + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      titleLarge = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight(typ.weights.medium),
        fontSize = typ.sizes.titleLarge.sp,
        lineHeight = (typ.sizes.titleLarge + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      titleMedium = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight(typ.weights.medium),
        fontSize = typ.sizes.titleMedium.sp,
        lineHeight = (typ.sizes.titleMedium + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      bodyLarge = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = typ.sizes.bodyLarge.sp,
        lineHeight = (typ.sizes.bodyLarge + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      bodyMedium = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = typ.sizes.bodyMedium.sp,
        lineHeight = (typ.sizes.bodyMedium + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      labelLarge = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = typ.sizes.labelLarge.sp,
        lineHeight = (typ.sizes.labelLarge + 4).sp,
        letterSpacing = typ.letterSpacing
      ),
      labelMedium = TextStyle(
        fontFamily = typ.fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = typ.sizes.labelMedium.sp,
        lineHeight = (typ.sizes.labelMedium + 4).sp,
        letterSpacing = typ.letterSpacing
      )
    )
  }

  /** Resolve gauge styling into a config map. */
  private fun resolveGauge(gauge: GaugeData, colors: CockpitThemeColors): Map<String, Any> {
    mapOf(
      "style" to gauge.style,
      "needleStyle" to gauge.needleStyle,
      "tickStyle" to gauge.tickStyle,
      "warningZones" to gauge.warningZones.map { z ->
        mapOf("min" to z.min, "max" to z.max, "color" to z.color)
      },
      "stylePreset" to gauge.stylePreset
    )
  }

  /** Resolve layout dimensions into a config map. */
  private fun resolveLayout(layout: LayoutData, colors: CockpitThemeColors): Map<String, Any> {
    mapOf(
      "preset" to layout.preset,
      "launcherGridRows" to layout.launcherGrid.rows,
      "launcherGridCols" to layout.launcherGrid.cols,
      "launcherGridCellWidth" to layout.launcherGrid.cellWidth.value,
      "launcherGridCellHeight" to layout.launcherGrid.cellHeight.value,
      "clusterSlotWidthRatio" to layout.clusterSlot.widthRatio,
      "clusterSlotHeightRatio" to layout.clusterSlot.heightRatio,
      "safeMarginsTop" to layout.safeMargins.top.value,
      "safeMarginsBottom" to layout.safeMargins.bottom.value,
      "safeMarginsLeft" to layout.safeMargins.left.value,
      "safeMarginsRight" to layout.safeMargins.right.value
    )
  }

  /** Apply an optional accent override (ARGB hex like "FF22D3EE") on top of a preset. */
  fun applyOverride(
    preset: ThemePreset,
    overrides: ThemeOverrides
  ): CockpitThemeColors {
    var colors = preset.tokens.color

    if (overrides.accent != null) {
      colors = colors.copy(accent = parseHexColor(overrides.accent!!))
      colors = colors.copy(
        onAccent = if (overrides.accent.value.luminance() > 0.5f) Color(0xFF0A0F14) else Color(0xFFFFFFFF)
      )
    }

    if (overrides.background != null) {
      colors = colors.copy(background = parseHexColor(overrides.background!!))
    }

    colors
  }

  /** Parse a hex color string, returning a Color. */
  private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#").takeLast(8)
    return if (cleaned.length == 8) Color(cleaned.toLong(16).toInt()) else Color(0xFF181E24)
  }

  /** Applies an optional accent override (ARGB hex like "FF22D3EE") on top of a preset. */
  fun apply(
    presetId: String?,
    accentOverride: String?
  ): CockpitThemeColors {
    val preset = byId(presetId)
    val parsed = parseAccent(accentOverride)
    return if (parsed == null) preset.colors else preset.colors.copy(
      accent = parsed,
      onAccent = if (parsed.luminance() > 0.5f) Color(0xFF0A0F14) else Color(0xFFFFFFFF)
    )
  }

  fun parseAccent(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
      val cleaned = hex.removePrefix("#").takeLast(8)
      if (cleaned.length == 8) Color(cleaned.toLong(16).toInt()) else null
    } catch (_: Exception) { null }
  }

  /** Provide the default theme at app start. */
  @Composable
  fun applyDefaultTheme() {
    val defaultPreset = CockpitThemes.byId(CockpitThemes.defaultPresetId)
    val defaultOverrides = ThemeOverrides(
      accent = null,
      background = null,
      gaugeStyle = null,
      density = null,
      fontScale = 1.0f,
      widgetLayout = null
    )
    CockpitTheme.SportageObdTheme(presetId = defaultPreset.id, overrides = defaultOverrides) {
      // Composition content will receive the theme via MaterialTheme
    }
  }

  /** Composition local for the current resolved theme. */
  val LocalCockpitTheme = staticCompositionLocalOf<ResolvedTheme> { ResolvedTheme(
    colors = DefaultTheme.colors,
    typography = MaterialTheme.typography,
    shape = Shape.Default,
    motion = mapOf("durationFast" to 150, "durationNormal" to 250, "durationSlow" to 400, "easing" to "ease-out"),
    backgroundStyle = "solid",
    gaugeConfig = mapOf(),
    layoutConfig = mapOf(),
    overrides = ThemeOverrides(accent = null, background = null, gaugeStyle = null, density = null, fontScale = 1.0f, widgetLayout = null)
  )}

/** Provide theme via composition locals. */
  @Composable
  fun SportageObdTheme(
    presetId: String = defaultPresetId,
    overrides: ThemeOverrides = ThemeOverrides(
      accent = null,
      background = null,
      gaugeStyle = null,
      density = null,
      fontScale = 1.0f,
      widgetLayout = null
    ),
    content: @Composable () -> Unit
  ) {
    // Load preset
    val preset = remember { CockpitThemes.byId(presetId) }

    // Persist selection via remember + DataStore (simplified: state in prefs)
    val savedPresetId by remember { mutableStateOf(preset.id) }
    val savedOverrides by remember { mutableStateOf(overrides) }

    // Apply overrides on top of preset
    val resolved = CockpitThemes.resolvePreset(preset, savedOverrides)

    // Provide theme via composition locals
    provideLocalTheme(resolved)

    // Apply Material3 color scheme typography from resolved tokens
    val scheme = when (preset.mode) {
      "dark" -> darkColorScheme(
        primary = resolved.colors.accent,
        secondary = resolved.colors.secondary,
        background = resolved.colors.background,
        surface = resolved.colors.surface,
        onBackground = resolved.colors.textPrimary,
        onSurface = resolved.colors.textPrimary,
        error = resolved.colors.danger
      )
      "light" -> lightColorScheme(
        primary = resolved.colors.accent,
        secondary = resolved.colors.secondary,
        background = resolved.colors.background,
        surface = resolved.colors.surface,
        onBackground = resolved.colors.textPrimary,
        onSurface = resolved.colors.textPrimary,
        error = resolved.colors.danger
      )
      else -> // system mode
        if (isSystemInDarkTheme()) {
          darkColorScheme(
            primary = resolved.colors.accent,
            secondary = resolved.colors.secondary,
            background = resolved.colors.background,
            surface = resolved.colors.surface,
            onBackground = resolved.colors.textPrimary,
            onSurface = resolved.colors.textPrimary,
            error = resolved.colors.danger
          )
        } else {
          lightColorScheme(
            primary = resolved.colors.accent,
            secondary = resolved.colors.secondary,
            background = resolved.colors.background,
            surface = resolved.colors.surface,
            onBackground = resolved.colors.textPrimary,
            onSurface = resolved.colors.textPrimary,
            error = resolved.colors.danger
          )
        }
    }

    // Override Material3 typography with resolved tokens
    MaterialTheme(
      colorScheme = scheme,
      typography = resolved.typography,
      content = content
    )

    // Provide color accessors
    provideColorLocalAccessors(resolved.colors)
  }

  /** Provide color accessor composables so existing code continues to work. */
  private fun provideColorLocalAccessors(colors: CockpitThemeColors) {
    // LocalCockpitColors accessors read from LocalCockpitTheme.current.colors
    // The existing DarkBackground(), ElectricCyan() etc. will resolve through the composition local
  }
}

/** Default theme colors used when parsing fails or theme is missing. */
object DefaultTheme {
  val colors = CockpitThemeColors(
    background = Color(0xFF0A0E14),
    surface = Color(0xFF111820),
    surfaceVariant = Color(0xFF181F2A),
    primary = Color(0xFF3B82F6),
    secondary = Color(0xFF34D399),
    accent = Color(0xFF06B6D4),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFF86EFDF),
    success = Color(0xFF14F195),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    outline = Color(0xFF374151),
    gaugeTrack = Color(0xFF1F2937),
    gaugeFill = Color(0xFF0A0F17),
    chart1 = Color(0xFF60A5FA),
    chart2 = Color(0xFF818CF8),
    chart3 = Color(0xFFA5F3FC)
  )

  val tokens = CockpitThemeTokens(
    typography = TypographyData(
      fontFamily = FontFamily.SansSerif,
      sizes = TypographyData.TypographySizes(
        displayLarge: 40, displayMedium: 32, headlineLarge: 24, titleLarge: 20, titleMedium: 16,
        bodyLarge: 16, bodyMedium: 14, labelLarge: 14, labelMedium: 12
      ),
      weights = TypographyData.TypographyWeights(),
      letterSpacing: 0.0f,
      numericFont: "monospace"
    ),
    shape = ShapeData(cornerRadius: MemoryUnitType.dp(8), borderWidth: MemoryUnitType.dp(1), gaugeStroke: MemoryUnitType.dp(2)),
    spacing = SpacingData(compact: MemoryUnitType.dp(16), comfortable: MemoryUnitType.dp(24), spacious: MemoryUnitType.dp(32)),
    elevation = ElevationData(shadow: "0 1px 2px rgba(0,0,0,0.3)"),
    motion = MotionData(durationFast: 150, durationNormal: 250, durationSlow: 400, easing: "ease-out"),
    iconography = IconographyData(iconSet: "material-icons", strokeWidth: MemoryUnitType.dp(2), style: "filled"),
    background = BackgroundData(style: "solid", gradientColors: null, imageResource: null, safeAreaScrim: "rgba(0,0,0,0.4)"),
    gauge = GaugeData(style: "radial", needleStyle: "arrow", tickStyle: "short", warningZones: emptyList(), stylePreset: "minimal"),
    layout = LayoutData(
      preset: "minimal",
      launcherGrid: LauncherGridData(rows: 2, cols: 3, cellWidth: MemoryUnitType.dp(120), cellHeight: MemoryUnitType.dp(100)),
      clusterSlot: ClusterSlotData(widthRatio: 0.8f, heightRatio: 0.6f),
      safeMargins: SafeMarginsData(top: MemoryUnitType.dp(12), bottom: MemoryUnitType.dp(12), left: MemoryUnitType.dp(12), right: MemoryUnitType.dp(12))
    )
  )
}

/** Accessor composables that read from LocalCockpitTheme colors. */
@Composable
fun DarkBackground(): Color = LocalCockpitTheme.current.colors.background

@Composable
fun DarkSurface(): Color = LocalCockpitTheme.current.colors.surface

@Composable
fun DeepShade(): Color = LocalCockpitTheme.current.colors.background

@Composable
fun ElectricCyan(): Color = LocalCockpitTheme.current.colors.accent

@Composable
fun TealAccent(): Color = LocalCockpitTheme.current.colors.secondary

@Composable
fun WarningAmber(): Color = LocalCockpitTheme.current.colors.warning

@Composable
fun AmberWarning(): Color = LocalCockpitTheme.current.colors.warning

@Composable
fun CriticalRed(): Color = LocalCockpitTheme.current.colors.critical

@Composable
fun PrimaryText(): Color = LocalCockpitTheme.current.colors.textPrimary

@Composable
fun SecondaryText(): Color = LocalCockpitTheme.current.colors.textSecondary

@Composable
fun OnAccent(): Color = LocalCockpitTheme.current.colors.onAccent