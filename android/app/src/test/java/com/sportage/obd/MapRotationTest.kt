package com.sportage.obd

import android.app.Activity
import android.view.View
import com.sportage.obd.map.MapMarkerStyle
import com.sportage.obd.map.MarkerIcons
import com.sportage.obd.map.NativeTrackerMap
import com.sportage.obd.map.TrackerMapPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.view.MapView
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Course-up (heading-up) rotation of the native Mapsforge map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapRotationTest {
    private val doha = LatLong(25.2854, 51.5310)

    private fun mapFor(view: NativeTrackerMap): MapView = view.getChildAt(0) as MapView

    @Test fun headingEastPutsNorthOnTheLeftOfTheScreen() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = NativeTrackerMap(activity, File("src/main/assets/${TrackerMapPolicy.MAP_ASSET}"))
        try {
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 360, 360)

            view.updateFix(doha.latitude, doha.longitude, 0, bearing = 90f, speedKmh = 50f, headingUp = true)
            val map = mapFor(view)
            assertEquals("Mapsforge rotation runs counter-clockwise", -90f, map.mapRotation.degrees, 0.5f)

            // With the car heading east, north sits to the driver's left, i.e. left on screen.
            val north = LatLong(doha.latitude + 0.004, doha.longitude)
            val projected = map.mapViewProjection.toPixels(north, true)
            assertTrue(
                "North must project left of the 180px centre, was ${projected.x}",
                projected.x < 180.0
            )
        } finally {
            view.release()
            controller.pause().stop().destroy()
        }
    }

    @Test fun stationaryOrNorthUpKeepsTheMapUpright() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val view = NativeTrackerMap(activity, File("src/main/assets/${TrackerMapPolicy.MAP_ASSET}"))
        try {
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 360, 360)
            val map = mapFor(view)

            // Compass headings below 3 km/h must not spin the map while parked.
            view.updateFix(doha.latitude, doha.longitude, 0, bearing = 120f, speedKmh = 1f, headingUp = true)
            assertEquals(0f, map.mapRotation.degrees, 0.5f)

            // Explicit north-up wins even at speed, and rotation returns to zero again.
            view.updateFix(doha.latitude, doha.longitude, 0, bearing = 90f, speedKmh = 50f, headingUp = true)
            assertEquals(-90f, map.mapRotation.degrees, 0.5f)
            view.updateFix(doha.latitude, doha.longitude, 0, bearing = 90f, speedKmh = 50f, headingUp = false)
            assertEquals(0f, map.mapRotation.degrees, 0.5f)
        } finally {
            view.release()
            controller.pause().stop().destroy()
        }
    }

    @Test fun markerStyleDefaultsToArrowAndIgnoresUnknownValues() {
        assertEquals(MapMarkerStyle.ARROW, MapMarkerStyle.saved(null))
        assertEquals(MapMarkerStyle.ARROW, MapMarkerStyle.saved("SPACESHIP"))
        assertEquals(MapMarkerStyle.CAR, MapMarkerStyle.saved("CAR"))
    }

    @Test fun vehicleMarkersAreDrawnAndPointWhereTheCarFaces() {
        MapMarkerStyle.entries.forEach { style ->
            val icon = MarkerIcons.create(style, 96)
            assertEquals("${style.name} icon size", 96, icon.width)
            assertEquals("${style.name} icon size", 96, icon.height)
            assertTrue("${style.name} must draw something", icon.getPixel(48, 48) != 0)
        }

        // The car is drawn nose-up, so a 90° screen angle must swing the nose to the right.
        val upright = MarkerIcons.create(MapMarkerStyle.CAR, 96)
        val turned = MarkerIcons.create(MapMarkerStyle.CAR, 96, 90f)
        assertEquals("nothing should sit right of the upright car", 0, upright.getPixel(84, 48))
        assertTrue("the turned car's nose must reach the right edge", turned.getPixel(82, 48) != 0)
    }
}
