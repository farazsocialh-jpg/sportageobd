package com.sportage.obd

import com.sportage.obd.trips.TripStore
import com.sportage.obd.trips.TripSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TripStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): TripStore = TripStore(TripStore.tripFile(folder.root))

    private val trip = TripSummary(
        startedAtMs = 1_700_000_000_000,
        endedAtMs = 1_700_001_800_000,
        distanceKm = 24.512,
        durationSeconds = 1800,
        movingSeconds = 1500,
        maxSpeedKmh = 112,
        avgSpeedKmh = 49,
        zeroToHundredSeconds = 9.4,
        ecoScore = 82,
        fuelLitres = 1.842,
        vin = "KNAPM81ABMA000000"
    )

    @Test
    fun `missing file loads as an empty history`() {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `append then load round-trips every field`() {
        val store = store()
        store.append(trip)

        val loaded = store.load()

        assertEquals(1, loaded.size)
        assertEquals(trip, loaded.first())
    }

    @Test
    fun `optional values survive the round trip as null`() {
        val store = store()
        store.append(trip.copy(zeroToHundredSeconds = null, fuelLitres = null, vin = null))

        val loaded = store.load().first()

        assertNull(loaded.zeroToHundredSeconds)
        assertNull(loaded.fuelLitres)
        assertNull(loaded.vin)
    }

    @Test
    fun `multiple trips keep insertion order`() {
        val store = store()
        store.append(trip)
        store.append(trip.copy(startedAtMs = trip.startedAtMs + 3_600_000))

        val loaded = store.load()

        assertEquals(2, loaded.size)
        assertTrue(loaded[0].startedAtMs < loaded[1].startedAtMs)
    }

    @Test
    fun `export contains a header and one row per trip`() {
        val store = store()
        val csv = store.exportCsv(listOf(trip, trip.copy(startedAtMs = trip.startedAtMs + 1)))

        val lines = csv.trim().lines()

        assertEquals(3, lines.size)
        assertEquals(TripStore.EXPORT_HEADER, lines[0])
        assertTrue(lines[1].startsWith("1700000000000,1700001800000,24.512"))
    }

    @Test
    fun `clearing keeps the header so the file stays parseable`() {
        val store = store()
        store.append(trip)

        store.clear()

        assertTrue(store.load().isEmpty())
        val content = File(folder.root, "trips.csv").readText()
        assertTrue(content.startsWith(TripStore.EXPORT_HEADER))
    }

    @Test
    fun `replaceAll rewrites the file`() {
        val store = store()
        store.append(trip)
        store.append(trip)

        store.replaceAll(listOf(trip.copy(distanceKm = 1.0)))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals(1.0, loaded.first().distanceKm, 0.0001)
    }

    @Test
    fun `corrupt rows are skipped instead of failing the whole history`() {
        val file = TripStore.tripFile(folder.root)
        file.parentFile?.mkdirs()
        file.writeText(
            TripStore.EXPORT_HEADER + "\n" +
                "not,a,trip,row\n" +
                "1700000000000,1700001800000,24.5,1800,1500,112,49,,82,,\n"
        )

        val loaded = store().load()

        assertEquals(1, loaded.size)
        assertEquals(24.5, loaded.first().distanceKm, 0.0001)
    }
}
