package com.sportage.obd.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Process-wide registry of user-imported themes. Themes are validated on the way in
 * (see [ThemeJson]); anything invalid is dropped with a log line, never partially applied.
 */
object ImportedThemes {
    private val registry = LinkedHashMap<String, ThemeSpec>()

    @Synchronized
    fun replaceAll(specs: List<ThemeSpec>) {
        registry.clear()
        specs.forEach { registry[it.id] = it }
        BuiltInThemes.invalidateCache()
    }

    @Synchronized
    fun all(): List<ThemeSpec> = registry.values.toList()

    @Synchronized
    fun byId(id: String?): ThemeSpec? = if (id == null) null else registry[id]

    @Synchronized
    fun add(spec: ThemeSpec) {
        registry[spec.id] = spec
        BuiltInThemes.invalidateCache()
    }

    @Synchronized
    fun remove(id: String) {
        registry.remove(id)
        BuiltInThemes.invalidateCache()
    }

    /** Imported themes presented in the same shape as the built-in presets. */
    fun presets(): List<CockpitThemePreset> =
        all().map { CockpitThemePreset(it.id, it.name, it.toCockpitColors(), it.description) }
}

/** Persists imported themes in the existing UI preferences file, no new dependency. */
class ImportedThemeStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sportage_obd_ui", Context.MODE_PRIVATE)

    /** Loads and validates every stored theme; invalid documents are skipped. */
    fun load(): List<ThemeSpec> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val accepted = mutableListOf<ThemeSpec>()
        val lookup: (String) -> ThemeSpec? = { id ->
            accepted.firstOrNull { it.id == id } ?: BuiltInThemes.byId(id) ?: ImportedThemes.byId(id)
        }
        ThemeJson.parseAll(raw, lookup).forEachIndexed { index, result ->
            when (result) {
                is ThemeParseResult.Success -> accepted += result.spec
                is ThemeParseResult.Failure ->
                    Log.w("ImportedThemes", "Skipping imported theme #$index: ${result.errors.joinToString("; ")}")
            }
        }
        return accepted
    }

    fun save(specs: List<ThemeSpec>) {
        val array = org.json.JSONArray(specs.map { org.json.JSONObject(ThemeJson.export(it, pretty = false)) })
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun add(spec: ThemeSpec): Boolean {
        val current = load().filterNot { it.id == spec.id }
        if (current.size >= MAX_IMPORTED && current.none { it.id == spec.id }) {
            Log.w("ImportedThemes", "Import limit reached; keeping existing themes")
            return false
        }
        val next = current + spec
        save(next)
        ImportedThemes.replaceAll(next)
        return true
    }

    fun remove(id: String) {
        val next = load().filterNot { it.id == id }
        save(next)
        ImportedThemes.replaceAll(next)
    }

    companion object {
        const val KEY = "imported_themes_v2"
        const val MAX_IMPORTED = 20
    }
}
