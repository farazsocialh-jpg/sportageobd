package com.sportage.obd

import androidx.compose.ui.graphics.Color
import com.sportage.obd.ui.theme.BuiltInThemes
import com.sportage.obd.ui.theme.CockpitThemes
import com.sportage.obd.ui.theme.GaugeStyle
import com.sportage.obd.ui.theme.ImportedThemeStore
import com.sportage.obd.ui.theme.ThemeDensity
import com.sportage.obd.ui.theme.ThemeJson
import com.sportage.obd.ui.theme.ThemeMode
import com.sportage.obd.ui.theme.ThemeParseResult
import com.sportage.obd.ui.theme.ThemeSchemaVersion
import com.sportage.obd.ui.theme.ThemeSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Theme schema: validation, inheritance, overrides, import/export, fallback and persistence. */
@RunWith(RobolectricTestRunner::class)
class ThemeSpecTest {

    private fun builtInJsonFile(): File {
        val candidates = listOf(
            File("src/main/res/raw/builtin_themes.json"),
            File("app/src/main/res/raw/builtin_themes.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("builtin_themes.json not found from ${File(".").absolutePath}")
    }

    @Test
    fun builtInThemesAreCompleteAndDistinct() {
        assertTrue("at least five built-in themes", BuiltInThemes.all.size >= 5)
        assertEquals(BuiltInThemes.all.size, BuiltInThemes.all.map { it.id }.toSet().size)
        BuiltInThemes.all.forEach { spec ->
            assertEquals(ThemeSchemaVersion, spec.version)
            assertTrue("${spec.id} needs a description", spec.description.isNotBlank())
            assertTrue("${spec.id} needs widget presets", spec.layout.widgetPresets.isNotEmpty())
            assertTrue("${spec.id} needs launcher bounds", spec.layout.launcherRows in 1..8 && spec.layout.launcherCols in 1..8)
            assertTrue("${spec.id} needs valid gauge fractions", spec.gauge.warnFraction < spec.gauge.criticalFraction)
        }
        // Not recolors: typography/shape/density/gauge differ across the set.
        val signatures = BuiltInThemes.all.map {
            "${it.typography.fontFamily}/${it.shapes.cardCornerDp}/${it.spacing.density.id}/${it.gauge.style.id}"
        }.toSet()
        assertEquals(BuiltInThemes.all.size, signatures.size)
        // Light and dark themes are both present.
        assertTrue(BuiltInThemes.all.any { it.mode == ThemeMode.LIGHT })
        assertTrue(BuiltInThemes.all.any { it.mode == ThemeMode.DARK })
    }

    @Test
    fun builtInJsonMirrorsKotlinThemesAndParses() {
        val raw = builtInJsonFile().readText()
        val results = ThemeJson.parseAll(raw) { BuiltInThemes.byId(it) }
        results.forEach { result ->
            assertTrue(
                "JSON theme failed validation: ${(result as? ThemeParseResult.Failure)?.errors}",
                result is ThemeParseResult.Success
            )
        }
        val ids = results.mapNotNull { (it as? ThemeParseResult.Success)?.spec?.id }.toSet()
        assertEquals(BuiltInThemes.all.map { it.id }.toSet(), ids)
    }

    @Test
    fun exportImportRoundTripIsLossless() {
        BuiltInThemes.all.forEach { spec ->
            val exported = ThemeJson.export(spec)
            val parsed = ThemeJson.parse(exported)
            assertTrue("round-trip failed for ${spec.id}: ${(parsed as? ThemeParseResult.Failure)?.errors}",
                parsed is ThemeParseResult.Success)
            assertEquals(spec, (parsed as ThemeParseResult.Success).spec)
        }
    }

    @Test
    fun inheritanceInheritsOmittedTokenGroups() {
        val base = BuiltInThemes.cyberNeon
        val child = """
            {
              "version": "2.0",
              "id": "cyber-variant",
              "name": "Cyber Variant",
              "mode": "dark",
              "extends": "cyber-neon",
              "tokens": { "color": { "accent": "#FF00FF00" } }
            }
        """.trimIndent()
        val parsed = ThemeJson.parse(child, base)
        assertTrue((parsed as? ThemeParseResult.Failure)?.errors.toString(), parsed is ThemeParseResult.Success)
        val spec = (parsed as ThemeParseResult.Success).spec
        assertEquals(Color(0xFF00FF00), spec.colors.accent)
        assertEquals(base.colors.background, spec.colors.background)
        assertEquals(base.typography, spec.typography)
        assertEquals(base.layout, spec.layout)
    }

    @Test
    fun rejectsRemoteContentAndMalformedDocuments() {
        val valid = ThemeJson.export(BuiltInThemes.minimalDaily, pretty = false)
        val cases = mapOf(
            "remote url" to valid.replace(
                "\"description\":\"Light, uncluttered daily-driver cockpit. Soft surfaces, compact numerals and minimal motion.\"",
                "\"description\":\"see https://evil.example\""
            ),
            "unknown field" to JSONObject(valid).put("evil", true).toString(),
            "bad color" to valid.replace("\"accent\":\"#FF0F766E\"", "\"accent\":\"red\""),
            "bad version" to valid.replace("\"version\":\"2.0\"", "\"version\":\"9.9\""),
            "bad widget id" to valid.replace("\"speed\"", "\"speed && rm -rf /\""),
            "oversized" to "x".repeat(ThemeJson.MAX_CHARS + 1),
            "not json" to "{ this is not json",
            "low contrast" to valid
                .replace("\"background\":\"#FFF4F6F8\"", "\"background\":\"#FF000000\"")
                .replace("\"textPrimary\":\"#FF141A21\"", "\"textPrimary\":\"#FF111111\"")
        )
        cases.forEach { (name, raw) ->
            assertTrue("$name should be rejected", ThemeJson.parse(raw) is ThemeParseResult.Failure)
        }
    }

    @Test
    fun unknownThemeIdFallsBackToDefault() {
        val fallback = BuiltInThemes.resolved(CockpitThemes.defaultId, null, null, null)
        assertEquals(fallback, BuiltInThemes.resolved("does-not-exist", null, null, null))
        assertEquals(fallback, BuiltInThemes.resolved(null, null, null, null))
    }

    @Test
    fun userOverridesApplyOnTopOfTheme() {
        val resolved = BuiltInThemes.resolved("cyber-neon", "FF3B82F6", "compact", "bar")
        assertEquals(Color(0xFF3B82F6), resolved.colors.accent)
        assertEquals(ThemeDensity.COMPACT, resolved.spacing.density)
        assertEquals(GaugeStyle.BAR, resolved.gauge.style)
        // Overrides never leak into unrelated tokens.
        assertEquals(BuiltInThemes.cyberNeon.colors.surface, resolved.colors.surface)
    }

    @Test
    fun legacyColorPresetLiftsToCompleteTheme() {
        val legacy = CockpitThemes.byId("midnight")
        val spec = BuiltInThemes.specFor(legacy)
        assertEquals("midnight", spec.id)
        assertNotNull(spec.typography)
        assertTrue(spec.layout.widgetPresets.isNotEmpty())
        assertEquals(ThemeMode.DARK, spec.mode)
        // Every preset in the picker resolves to a full spec.
        CockpitThemes.presets.forEach { preset ->
            val resolved = CockpitThemes.specFor(preset.id)
            assertTrue(resolved.id == preset.id)
        }
    }

    @Test
    fun accessibilityWarningsFlagUnreadableText() {
        val spec = BuiltInThemes.minimalDaily
        assertTrue(spec.accessibilityWarnings().isEmpty())
        val unreadable = spec.copy(colors = spec.colors.copy(textPrimary = spec.colors.background))
        // A theme whose text matches its background must be flagged.
        assertFalse(unreadable.accessibilityWarnings().isEmpty())
    }

    @Test
    fun importedThemesPersistAndReload() {
        val context = RuntimeEnvironment.getApplication()
        val store = ImportedThemeStore(context)
        store.save(emptyList())
        val custom = BuiltInThemes.minimalDaily.copy(id = "custom-driver", name = "Custom Driver")
        assertTrue(store.add(custom))
        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("custom-driver", loaded[0].id)
        assertEquals(custom.colors.accent, loaded[0].colors.accent)
        store.remove("custom-driver")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun invalidStoredThemeIsSkippedWithoutLosingValidOnes() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("sportage_obd_ui", android.content.Context.MODE_PRIVATE)
        val good = ThemeJson.export(BuiltInThemes.offroadRugged, pretty = false)
        prefs.edit().putString(
            ImportedThemeStore.KEY,
            org.json.JSONArray(listOf(good, "{\"version\":\"2.0\",\"id\":\"broken\"}")).toString()
        ).apply()
        val loaded = ImportedThemeStore(context).load()
        assertEquals(1, loaded.size)
        assertEquals("offroad-rugged", loaded[0].id)
    }

    @Test
    fun themeSpecRoundTripsThroughJsonObject() {
        val spec: ThemeSpec = BuiltInThemes.classicAnalog
        val round = ThemeJson.parse(ThemeJson.export(spec, pretty = false))
        assertEquals(spec, (round as ThemeParseResult.Success).spec)
    }
}
