package com.sportage.obd

import com.sportage.obd.trips.TripRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {

    @Test
    fun `integrates distance with the trapezoid rule`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(0, 0)
        recorder.onSample(36, 1_000)
        recorder.onSample(72, 2_000)

        val progress = recorder.progress(2_000)

        // 0->36 km/h for 1 s = 0.005 km, 36->72 for 1 s = 0.015 km.
        assertEquals(0.02, progress.distanceKm, 0.0001)
        assertEquals(72, progress.maxSpeedKmh)
    }

    @Test
    fun `a long gap is capped so a sleeping phone cannot invent kilometres`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(100, 0)
        recorder.onSample(100, 60_000)

        // 60 s at 100 km/h would be 1.67 km; the 5 s cap allows only 0.139 km.
        assertEquals(0.1389, recorder.progress(60_000).distanceKm, 0.001)
    }

    @Test
    fun `zero to hundred is timed from a standstill`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(0, 0)
        recorder.onSample(40, 3_000)
        recorder.onSample(100, 10_000)

        assertEquals(10.0, recorder.progress(10_000).zeroToHundredSeconds!!, 0.01)
    }

    @Test
    fun `an implausibly slow run to 100 is discarded`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(0, 0)
        recorder.onSample(100, 120_000) // two minutes = traffic, not acceleration

        assertNull(recorder.progress(120_000).zeroToHundredSeconds)
    }

    @Test
    fun `finish returns a summary and resets the recorder`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recordSamples(recorder)
        val summary = recorder.finish(20_000)

        assertNotNull(summary)
        assertEquals(0L, summary!!.startedAtMs)
        assertEquals(20_000L, summary.endedAtMs)
        assertEquals(20L, summary.durationSeconds)
        assertTrue(summary.distanceKm > 0.0)
        assertEquals(100, summary.maxSpeedKmh)
        assertFalse(recorder.isActive)
        assertNull(recorder.finish(25_000))
    }

    @Test
    fun `trips too short to be real are dropped`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(5, 1_000)

        assertNull(recorder.finish(2_000))
    }

    @Test
    fun `fuel consumption integrates the instantaneous rate`() {
        val recorder = TripRecorder()
        recorder.start(0)
        for (second in 0..10) {
            recorder.onSample(50, second * 1_000L, fuelRateLph = 10f)
        }
        val summary = recorder.finish(10_000)!!

        // 10 s at 10 l/h = 0.0278 l.
        assertEquals(0.0278, summary.fuelLitres!!, 0.0005)
    }

    @Test
    fun `idling lowers the eco score`() {
        val idling = TripRecorder()
        idling.start(0)
        for (second in 0..120) {
            idling.onSample(0, second * 1_000L)
        }

        val driving = TripRecorder()
        driving.start(0)
        for (second in 0..120) {
            driving.onSample(60, second * 1_000L)
        }

        assertTrue(idling.progress(120_000).ecoScore < driving.progress(120_000).ecoScore)
        assertEquals(100, driving.progress(120_000).ecoScore)
    }

    @Test
    fun `harsh acceleration is penalised`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(20, 0)
        recorder.onSample(45, 1_000) // +25 km/h in one second
        recorder.onSample(70, 2_000)
        for (second in 3..60) {
            recorder.onSample(70, second * 1_000L)
        }

        assertTrue(recorder.progress(60_000).ecoScore < 100)
    }

    @Test
    fun `samples with no speed still advance the clock`() {
        val recorder = TripRecorder()
        recorder.start(0)
        recorder.onSample(null, 1_000)
        recorder.onSample(null, 5_000)

        val progress = recorder.progress(5_000)

        assertEquals(5L, progress.elapsedSeconds)
        assertEquals(0.0, progress.distanceKm, 0.0001)
        assertEquals(0, progress.maxSpeedKmh)
    }

    @Test
    fun `a backwards clock jump does not corrupt the trip`() {
        val recorder = TripRecorder()
        recorder.start(10_000)
        recorder.onSample(50, 10_000)
        recorder.onSample(60, 9_000) // clock stepped back

        assertEquals(0.0, recorder.progress(10_000).distanceKm, 0.0001)
    }

    private fun recordSamples(recorder: TripRecorder) {
        recorder.onSample(0, 0)
        recorder.onSample(50, 5_000)
        recorder.onSample(100, 10_000)
        recorder.onSample(80, 15_000)
        recorder.onSample(60, 20_000)
    }
}
