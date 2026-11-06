package com.sportage.obd

import com.sportage.obd.launcher.DriverProfile
import com.sportage.obd.launcher.KeyValueStore
import com.sportage.obd.launcher.LauncherSettings
import com.sportage.obd.launcher.PageId
import com.sportage.obd.launcher.PinnedApp
import com.sportage.obd.launcher.SettingsRepository
import com.sportage.obd.launcher.SettingsSnapshot
import com.sportage.obd.launcher.SnapshotCodec
import com.sportage.obd.launcher.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory store: the whole point of the KeyValueStore seam is that tests need no Android. */
private class FakeStore : KeyValueStore {
    val values = HashMap<String, String>()
    override fun getString(key: String, default: String?): String? = values[key] ?: default
    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

class LauncherSettingsTest {

    @Test
    fun `every page has a dock-sized short label`() {
        PageId.entries.forEach { page ->
            assertTrue(page.shortLabel.isNotBlank())
            assertTrue(page.shortLabel.length <= 6)
        }
        assertEquals("Diag", PageId.DIAGNOSTICS.shortLabel)
    }

    @Test
    fun `home cannot be hidden and stays first`() {
        val settings = LauncherSettings(hiddenPages = setOf(PageId.HOME, PageId.MEDIA)).sanitized()

        assertTrue(settings.isVisible(PageId.HOME))
        assertFalse(settings.isVisible(PageId.MEDIA))
        assertEquals(PageId.HOME, settings.visiblePages.first())
        assertEquals(5, settings.visiblePages.size)
    }

    @Test
    fun `default page falls back to home when it gets hidden`() {
        val settings = LauncherSettings(
            hiddenPages = setOf(PageId.TRIP),
            defaultPage = PageId.TRIP
        ).sanitized()

        assertEquals(PageId.HOME, settings.defaultPage)
    }

    @Test
    fun `drawer and automation values are clamped to usable ranges`() {
        val settings = LauncherSettings(
            drawerColumns = 42,
            drawerIconDp = 4,
            driveModeEnterKmh = 400,
            autoPauseMinutes = 0
        ).sanitized()

        assertEquals(6, settings.drawerColumns)
        assertEquals(40, settings.drawerIconDp)
        assertEquals(120, settings.driveModeEnterKmh)
        assertEquals(1, settings.autoPauseMinutes)
    }

    @Test
    fun `exit threshold always sits below the entry threshold`() {
        listOf(5, 25, 60, 120).forEach { enter ->
            val exit = LauncherSettings(driveModeEnterKmh = enter).sanitized().driveModeExitKmh
            assertTrue("enter=$enter exit=$exit", exit < enter)
            assertTrue(exit >= 1)
        }
    }

    @Test
    fun `pins are capped and de-duplicated`() {
        val pins = (1..8).map { PinnedApp("com.app$it", "Main", "App $it") } +
            PinnedApp("com.app1", "Main", "App 1")
        val settings = LauncherSettings(pinnedApps = pins).sanitized()

        assertEquals(LauncherSettings.MAX_PINS, settings.pinnedApps.size)
    }

    @Test
    fun `a profile applies units and layout without touching automation switches`() {
        val base = LauncherSettings(autoPauseEnabled = true, autoPauseMinutes = 30)
        val profile = DriverProfile(
            name = "Nadia",
            units = Units.MPH,
            driveModeEnterKmh = 40,
            hiddenPages = setOf(PageId.MEDIA),
            defaultPage = PageId.GAUGES
        )

        val applied = profile.applyTo(base)

        assertEquals(Units.MPH, applied.units)
        assertEquals(40, applied.driveModeEnterKmh)
        assertFalse(applied.isVisible(PageId.MEDIA))
        assertEquals(PageId.GAUGES, applied.defaultPage)
        assertTrue(applied.autoPauseEnabled)
        assertEquals(30, applied.autoPauseMinutes)
    }

    @Test
    fun `a profile whose default page is hidden falls back to home`() {
        val applied = DriverProfile(
            name = "Minimal",
            hiddenPages = setOf(PageId.GAUGES),
            defaultPage = PageId.GAUGES
        ).applyTo(LauncherSettings())

        assertEquals(PageId.HOME, applied.defaultPage)
    }
}

class SnapshotCodecTest {

    @Test
    fun `settings survive a round trip`() {
        val snapshot = SettingsSnapshot(
            settings = LauncherSettings(
                hiddenPages = setOf(PageId.TRIP, PageId.SETTINGS),
                defaultPage = PageId.GAUGES,
                drawerColumns = 4,
                drawerIconDp = 64,
                pinnedApps = listOf(PinnedApp("com.spotify.music", "Main", "Spotify")),
                driveModeEnabled = false,
                driveModeEnterKmh = 35,
                autoPauseEnabled = true,
                autoPauseMinutes = 15,
                units = Units.MPH
            ),
            activeProfile = "Nadia",
            profiles = listOf(DriverProfile("Nadia", Units.MPH, 35, setOf(PageId.MEDIA), PageId.HOME))
        )

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))!!

        assertEquals(snapshot.settings, decoded.settings)
        assertEquals("Nadia", decoded.activeProfile)
        assertEquals(1, decoded.profiles.size)
        assertEquals(snapshot.profiles.first(), decoded.profiles.first())
    }

    @Test
    fun `user text containing delimiters and newlines does not corrupt the document`() {
        val tricky = "Line|One\nLine\\Two"
        val snapshot = SettingsSnapshot(
            settings = LauncherSettings(pinnedApps = listOf(PinnedApp("com.x", "Main", tricky))),
            activeProfile = tricky,
            profiles = listOf(DriverProfile(tricky))
        )

        val decoded = SnapshotCodec.decode(SnapshotCodec.encode(snapshot))!!

        assertEquals(tricky, decoded.settings.pinnedApps.first().label)
        assertEquals(tricky, decoded.activeProfile)
        assertEquals(tricky, decoded.profiles.first().name)
    }

    @Test
    fun `foreign or future documents are rejected`() {
        assertNull(SnapshotCodec.decode(null))
        assertNull(SnapshotCodec.decode(""))
        assertNull(SnapshotCodec.decode("{\"json\":true}"))
        assertNull(SnapshotCodec.decode("SPORTAGE-COCKPIT 99\nunits=MPH"))
    }

    @Test
    fun `malformed lines are skipped instead of failing the whole restore`() {
        val text = """
            SPORTAGE-COCKPIT 1
            units=MPH
            pin=broken
            profile=too|few|fields
            columns=not-a-number
        """.trimIndent()

        val decoded = SnapshotCodec.decode(text)!!

        assertEquals(Units.MPH, decoded.settings.units)
        assertTrue(decoded.settings.pinnedApps.isEmpty())
        assertTrue(decoded.profiles.isEmpty())
        assertEquals(5, decoded.settings.drawerColumns)
    }
}

class SettingsRepositoryTest {

    @Test
    fun `settings round trip through the store`() {
        val store = FakeStore()
        val repo = SettingsRepository(store)

        repo.save(LauncherSettings(drawerColumns = 3, units = Units.MPH))

        assertEquals(3, repo.load().drawerColumns)
        assertEquals(Units.MPH, repo.load().units)
    }

    @Test
    fun `profiles are upserted by name and survive a settings save`() {
        val repo = SettingsRepository(FakeStore())

        repo.upsertProfile(DriverProfile("Nadia", Units.MPH))
        repo.upsertProfile(DriverProfile("Nadia", Units.KMH))
        repo.upsertProfile(DriverProfile("Sam"))
        repo.save(LauncherSettings(drawerIconDp = 72))

        val profiles = repo.profiles()
        assertEquals(2, profiles.size)
        assertEquals(Units.KMH, profiles.first { it.name == "Nadia" }.units)
        assertEquals(72, repo.load().drawerIconDp)
    }

    @Test
    fun `deleting a profile only removes that profile`() {
        val repo = SettingsRepository(FakeStore())
        repo.upsertProfile(DriverProfile("A"))
        repo.upsertProfile(DriverProfile("B"))

        val remaining = repo.deleteProfile("A")

        assertEquals(listOf("B"), remaining.map { it.name })
        assertEquals(listOf("B"), repo.profiles().map { it.name })
    }

    @Test
    fun `a bad backup leaves the current settings untouched`() {
        val repo = SettingsRepository(FakeStore())
        repo.save(LauncherSettings(drawerColumns = 6))

        assertFalse(repo.restore("this is not a backup"))
        assertEquals(6, repo.load().drawerColumns)
    }

    @Test
    fun `backup then restore reproduces the cockpit`() {
        val source = SettingsRepository(FakeStore())
        source.save(LauncherSettings(drawerColumns = 3, hiddenPages = setOf(PageId.MEDIA), units = Units.MPH))
        source.upsertProfile(DriverProfile("Nadia", Units.MPH))
        val backup = source.backup()

        val target = SettingsRepository(FakeStore())
        assertTrue(target.restore(backup))

        assertEquals(3, target.load().drawerColumns)
        assertEquals(Units.MPH, target.load().units)
        assertEquals(setOf(PageId.MEDIA), target.load().hiddenPages)
        assertEquals(listOf("Nadia"), target.profiles().map { it.name })
    }
}
