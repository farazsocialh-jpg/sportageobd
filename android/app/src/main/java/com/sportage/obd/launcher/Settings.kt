package com.sportage.obd.launcher

/**
 * Launcher cockpit configuration and driver profiles.
 *
 * Everything in this file is deliberately free of Android types: the encoding is plain text, so the
 * same code serialises to `SharedPreferences` and to the backup file, and it can be unit tested with
 * a fake store instead of an emulator.
 */

/** The six cockpit pages. Ids are stable: they are the indices the UI navigates by. */
enum class PageId(val index: Int, val label: String, val shortLabel: String) {
    HOME(0, "Home", "Home"),
    GAUGES(1, "Gauges", "Gauges"),
    // The dock is only 52-64dp wide, so long labels get a short form there.
    DIAGNOSTICS(2, "Diagnostics", "Diag"),
    TRIP(3, "Trip", "Trip"),
    MEDIA(4, "Media", "Media"),
    SETTINGS(5, "Setup", "Setup");

    companion object {
        fun fromIndex(index: Int): PageId? = entries.firstOrNull { it.index == index }
        fun fromName(name: String): PageId? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class Units(val label: String) { KMH("km/h"), MPH("mph") }

/** An app pinned to the dock and the home overview strip. */
data class PinnedApp(val packageName: String, val activityName: String, val label: String)

/**
 * @param hiddenPages pages removed from the dock and swipe navigation.
 * @param defaultPage page the cockpit opens on after boot/relaunch.
 * @param drawerColumns app drawer grid width, clamped to 3..6.
 * @param drawerIconDp app drawer icon size in dp, clamped to 40..80.
 * @param pinnedApps at most [MAX_PINS] apps; extra entries are dropped rather than silently kept.
 */
data class LauncherSettings(
    val hiddenPages: Set<PageId> = emptySet(),
    val defaultPage: PageId = PageId.HOME,
    val drawerColumns: Int = 5,
    val drawerIconDp: Int = 56,
    val pinnedApps: List<PinnedApp> = emptyList(),
    val driveModeEnabled: Boolean = true,
    val driveModeEnterKmh: Int = 25,
    val autoPauseEnabled: Boolean = false,
    val autoPauseMinutes: Int = 10,
    val units: Units = Units.KMH
) {
    /**
     * Exit threshold derived from the entry threshold, so the state machine always has a hysteresis
     * band and a driver cannot configure a value where entry and exit are the same speed.
     */
    val driveModeExitKmh: Int
        get() = (driveModeEnterKmh * 0.32f).toInt().coerceIn(1, (driveModeEnterKmh - 1).coerceAtLeast(1))

    /** Pages the driver kept visible, in cockpit order. Never empty: Home cannot be hidden. */
    val visiblePages: List<PageId>
        get() = PageId.entries.filter { it !in hiddenPages || it == PageId.HOME }

    fun isVisible(page: PageId): Boolean = page !in hiddenPages || page == PageId.HOME

    /** Applies the range rules so a corrupted store or a hand-edited backup cannot hurt the UI. */
    fun sanitized(): LauncherSettings = copy(
        hiddenPages = hiddenPages.filter { it != PageId.HOME }.toSet(),
        defaultPage = if (isVisible(defaultPage)) defaultPage else PageId.HOME,
        drawerColumns = drawerColumns.coerceIn(3, 6),
        drawerIconDp = drawerIconDp.coerceIn(40, 80),
        pinnedApps = pinnedApps.distinctBy { it.packageName + "/" + it.activityName }.take(MAX_PINS),
        driveModeEnterKmh = driveModeEnterKmh.coerceIn(5, 120),
        autoPauseMinutes = autoPauseMinutes.coerceIn(1, 120)
    )

    companion object {
        const val MAX_PINS = 4
        val DEFAULTS = LauncherSettings()
    }
}

/**
 * A named cockpit preset. Profiles exist because a head unit is often shared: one driver wants km/h
 * with the map dominant, another uses mph and hides the diagnostics pages.
 */
data class DriverProfile(
    val name: String,
    val units: Units = Units.KMH,
    val driveModeEnterKmh: Int = 25,
    val hiddenPages: Set<PageId> = emptySet(),
    val defaultPage: PageId = PageId.HOME
) {
    fun applyTo(settings: LauncherSettings): LauncherSettings = settings.copy(
        units = units,
        driveModeEnterKmh = driveModeEnterKmh,
        hiddenPages = hiddenPages,
        defaultPage = if (hiddenPages.contains(defaultPage) && defaultPage != PageId.HOME) PageId.HOME else defaultPage
    ).sanitized()

    companion object {
        fun fromSettings(name: String, settings: LauncherSettings): DriverProfile = DriverProfile(
            name = name,
            units = settings.units,
            driveModeEnterKmh = settings.driveModeEnterKmh,
            hiddenPages = settings.hiddenPages,
            defaultPage = settings.defaultPage
        )
    }
}

/** Everything a backup file carries. Trips are separate CSV files and stay device-local. */
data class SettingsSnapshot(
    val settings: LauncherSettings,
    val activeProfile: String,
    val profiles: List<DriverProfile>
)

/** The seam the launcher uses to read and write preferences. Implemented over SharedPreferences. */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
}

/**
 * Loads and persists [LauncherSettings] and profiles.
 *
 * Storage format is the same versioned text used by the backup file, which means a restored backup
 * cannot exercise a code path the normal load does not already cover.
 */
class SettingsRepository(private val store: KeyValueStore) {

    fun load(): LauncherSettings =
        SnapshotCodec.decode(store.getString(KEY_SETTINGS, null))?.settings ?: LauncherSettings.DEFAULTS

    fun save(settings: LauncherSettings) {
        val current = SnapshotCodec.decode(store.getString(KEY_SETTINGS, null))
        val snapshot = SettingsSnapshot(
            settings = settings.sanitized(),
            activeProfile = current?.activeProfile ?: DEFAULT_PROFILE,
            profiles = current?.profiles ?: emptyList()
        )
        store.putString(KEY_SETTINGS, SnapshotCodec.encode(snapshot))
    }

    fun profiles(): List<DriverProfile> =
        SnapshotCodec.decode(store.getString(KEY_SETTINGS, null))?.profiles ?: emptyList()

    fun activeProfile(): String =
        SnapshotCodec.decode(store.getString(KEY_SETTINGS, null))?.activeProfile ?: DEFAULT_PROFILE

    /** Saves or replaces a profile by name and returns the resulting profile list. */
    fun upsertProfile(profile: DriverProfile): List<DriverProfile> {
        val trimmed = profile.copy(name = profile.name.trim().ifEmpty { DEFAULT_PROFILE })
        val existing = profiles().filterNot { it.name.equals(trimmed.name, ignoreCase = true) }
        return writeProfiles(existing + trimmed)
    }

    fun deleteProfile(name: String): List<DriverProfile> =
        writeProfiles(profiles().filterNot { it.name.equals(name, ignoreCase = true) })

    fun setActiveProfile(name: String, settings: LauncherSettings) {
        val snapshot = SettingsSnapshot(settings.sanitized(), name, profiles())
        store.putString(KEY_SETTINGS, SnapshotCodec.encode(snapshot))
    }

    private fun writeProfiles(profiles: List<DriverProfile>): List<DriverProfile> {
        val snapshot = SettingsSnapshot(load(), activeProfile(), profiles)
        store.putString(KEY_SETTINGS, SnapshotCodec.encode(snapshot))
        return profiles
    }

    /** The text written to a `.sportage-settings` file. */
    fun backup(): String = SnapshotCodec.encode(
        SettingsSnapshot(load(), activeProfile(), profiles())
    )

    /**
     * @return true when [text] was a valid backup and has been persisted. Invalid input is rejected
     *         without touching the current settings.
     */
    fun restore(text: String): Boolean {
        val decoded = SnapshotCodec.decode(text) ?: return false
        store.putString(KEY_SETTINGS, SnapshotCodec.encode(decoded))
        return true
    }

    companion object {
        const val KEY_SETTINGS = "launcher.settings.v1"
        const val DEFAULT_PROFILE = "Default"
    }
}

/** Versioned text codec shared by preferences and the backup file. */
object SnapshotCodec {

    private const val HEADER = "SPORTAGE-COCKPIT"
    private const val VERSION = 1

    fun encode(snapshot: SettingsSnapshot): String {
        val s = snapshot.settings.sanitized()
        val lines = mutableListOf(
            "$HEADER $VERSION",
            "active=${escape(snapshot.activeProfile)}",
            "units=${s.units.name}",
            "hidden=${s.hiddenPages.joinToString(",") { it.name }}",
            "default=${s.defaultPage.name}",
            "columns=${s.drawerColumns}",
            "icon=${s.drawerIconDp}",
            "driveMode=${s.driveModeEnabled}",
            "driveEnter=${s.driveModeEnterKmh}",
            "autoPause=${s.autoPauseEnabled}",
            "autoPauseMinutes=${s.autoPauseMinutes}"
        )
        s.pinnedApps.forEach { pin ->
            lines += "pin=${escape(pin.packageName)}|${escape(pin.activityName)}|${escape(pin.label)}"
        }
        snapshot.profiles.forEach { profile ->
            lines += "profile=" + listOf(
                escape(profile.name),
                profile.units.name,
                profile.driveModeEnterKmh.toString(),
                profile.hiddenPages.joinToString(",") { it.name },
                profile.defaultPage.name
            ).joinToString("|")
        }
        return lines.joinToString("\n")
    }

    /**
     * @return the decoded snapshot, or null when [text] is not a backup this version understands.
     *         A malformed line is skipped rather than discarding the whole document: losing one pin
     *         is better than a failed restore.
     */
    fun decode(text: String?): SettingsSnapshot? {
        if (text.isNullOrBlank()) return null
        val lines = text.lines().filter { it.isNotBlank() }
        val header = lines.firstOrNull() ?: return null
        if (!header.startsWith(HEADER)) return null
        val version = header.removePrefix(HEADER).trim().toIntOrNull() ?: return null
        if (version > VERSION) return null

        val values = HashMap<String, String>()
        val pins = mutableListOf<PinnedApp>()
        val profiles = mutableListOf<DriverProfile>()

        lines.drop(1).forEach { line ->
            val key = line.substringBefore('=', "")
            val value = line.substringAfter('=', "")
            when (key) {
                "pin" -> {
                    val parts = value.split('|')
                    if (parts.size >= 3) {
                        pins += PinnedApp(unescape(parts[0]), unescape(parts[1]), unescape(parts[2]))
                    }
                }
                "profile" -> {
                    val parts = value.split('|')
                    if (parts.size >= 5) {
                        profiles += DriverProfile(
                            name = unescape(parts[0]),
                            units = runCatching { Units.valueOf(parts[1]) }.getOrDefault(Units.KMH),
                            driveModeEnterKmh = parts[2].toIntOrNull() ?: 25,
                            hiddenPages = parsePages(parts[3]),
                            defaultPage = PageId.fromName(parts[4]) ?: PageId.HOME
                        )
                    }
                }
                else -> if (key.isNotEmpty()) values[key] = value
            }
        }

        val hidden = parsePages(values["hidden"])
        val settings = LauncherSettings(
            hiddenPages = hidden,
            defaultPage = PageId.fromName(values["default"] ?: "") ?: PageId.HOME,
            drawerColumns = values["columns"]?.toIntOrNull() ?: 5,
            drawerIconDp = values["icon"]?.toIntOrNull() ?: 56,
            pinnedApps = pins,
            driveModeEnabled = values["driveMode"]?.toBooleanStrictOrNull() ?: true,
            driveModeEnterKmh = values["driveEnter"]?.toIntOrNull() ?: 25,
            autoPauseEnabled = values["autoPause"]?.toBooleanStrictOrNull() ?: false,
            autoPauseMinutes = values["autoPauseMinutes"]?.toIntOrNull() ?: 10,
            units = runCatching { Units.valueOf(values["units"] ?: "KMH") }.getOrDefault(Units.KMH)
        ).sanitized()

        return SettingsSnapshot(
            settings = settings,
            activeProfile = unescape(values["active"] ?: SettingsRepository.DEFAULT_PROFILE),
            profiles = profiles.distinctBy { it.name.lowercase() }
        )
    }

    private fun parsePages(raw: String?): Set<PageId> =
        raw.orEmpty().split(',').mapNotNull { PageId.fromName(it.trim()) }.toSet()

    /** Keeps the delimiters out of user text: labels can contain anything the user types. */
    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")

    fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (value[index + 1]) {
                    'p' -> out.append('|')
                    'n' -> out.append('\n')
                    '\\' -> out.append('\\')
                    else -> out.append(value[index + 1])
                }
                index += 2
                continue
            }
            out.append(char)
            index++
        }
        return out.toString()
    }
}
