package com.sportage.obd.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.concurrent.TimeUnit

/**
 * Native MapLibre GL hardware-accelerated 3D vector map surface for Kia Sportage cockpit.
 *
 * Implements Module 1 Heading-Up rotation:
 * When vehicle is navigating, tilts the camera to 35.0° and sets camera bearing to match vehicle heading,
 * delivering an authentic in-dash automotive navigation cockpit experience.
 */
class LibreTrackerView(
    context: Context,
    private val lifecycle: Lifecycle,
    theme: MapTheme,
    private val onReady: () -> Unit,
    private val onFailure: () -> Unit
) : FrameLayout(context) {

    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var fix: LatLng? = null
    private var bearing: Float = 0f
    private var currentZoom: Double = 15.5
    private var isHeadingUp: Boolean = true
    private var lastRecenter = -1
    private var observer: LifecycleEventObserver? = null
    private var started = false
    private var resumed = false
    private var released = false
    private var styleLoaded = false
    private val watchdog = Handler(Looper.getMainLooper())
    private var waitingForTiles = false
    private val renderTimeout = Runnable {
        if (!released && waitingForTiles) onFailure()
    }

    init {
        try {
            MapLibre.getInstance(context.applicationContext)
            HttpRequestUtil.setOkHttpClient(httpClient)
            // TextureView respects Compose clipping and can reveal the fallback underneath.
            val view = MapView(
                context,
                MapLibreMapOptions.createFromAttributes(context)
                    .textureMode(true)
                    .attributionEnabled(true)
                    .logoEnabled(true)
            )
            mapView = view
            addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.onCreate(null)
            observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_RESUME -> resume()
                    Lifecycle.Event.ON_PAUSE -> pause()
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_DESTROY -> release()
                    else -> Unit
                }
            }.also { lifecycle.addObserver(it) }

            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()

            view.addOnDidFailLoadingMapListener { onFailure() }
            view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                if (styleLoaded && !released) {
                    if (fully) {
                        waitingForTiles = false
                        watchdog.removeCallbacks(renderTimeout)
                        onReady()
                    } else if (!waitingForTiles) {
                        waitingForTiles = true
                        watchdog.postDelayed(renderTimeout, 18_000)
                    }
                }
            }

            view.getMapAsync { ready ->
                if (released) return@getMapAsync
                map = ready
                ready.uiSettings.isRotateGesturesEnabled = false
                ready.uiSettings.isTiltGesturesEnabled = false
                ready.setStyle(Style.Builder().fromUri(requireNotNull(theme.url))) { style ->
                    if (released) return@setStyle
                    // Prefer English street labels
                    val english = Expression.raw(MapLabels.ENGLISH)
                    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
                        val field = layer.textField
                        val value = if (field.isExpression) field.expression.toString() else field.value?.toString().orEmpty()
                        if (MapLabels.shouldLocalize(value)) layer.setProperties(textField(english))
                    }
                    style.addSource(GeoJsonSource("sportage-gps"))
                    style.addImage("sportage-gps-arrow", arrowIcon())
                    style.addLayer(
                        SymbolLayer("sportage-gps-dot", "sportage-gps").withProperties(
                            iconImage("sportage-gps-arrow"),
                            iconAllowOverlap(true),
                            iconIgnorePlacement(true),
                            iconSize(1.15f),
                            iconRotate(Expression.get("bearing"))
                        )
                    )
                    styleLoaded = true
                    fix?.let { point ->
                        updateMarker(point)
                        center(point, currentZoom, bearing, isHeadingUp)
                    }
                }
            }
        } catch (_: Exception) {
            onFailure()
        } catch (_: LinkageError) {
            onFailure()
        }
    }

    /**
     * Updates GPS coordinates and re-aligns 3D perspective camera with heading.
     *
     * @param latitude Current latitude.
     * @param longitude Current longitude.
     * @param zoom Map zoom level (default 16).
     * @param headingBearing Current vehicle course / compass azimuth.
     * @param headingUp When true, rotates camera to heading bearing and tilts to 35.0°.
     */
    fun updateFix(
        latitude: Double,
        longitude: Double,
        zoom: Int = 16,
        headingBearing: Float = 0f,
        headingUp: Boolean = true
    ) {
        if (released || !TrackerMapPolicy.valid(latitude, longitude)) return
        val next = LatLng(latitude, longitude)
        val targetZoom = zoom.toDouble().coerceIn(3.0, 18.0)
        if (next != fix || headingBearing != bearing || targetZoom != currentZoom || headingUp != isHeadingUp) {
            fix = next
            bearing = headingBearing
            currentZoom = targetZoom
            isHeadingUp = headingUp
            if (styleLoaded) {
                updateMarker(next)
                center(next, currentZoom, bearing, isHeadingUp)
            }
        }
    }

    /**
     * Legacy signature overload for compatibility with existing recenter callers.
     */
    fun updateFix(latitude: Double, longitude: Double, recenter: Int, headingBearing: Float = 0f) {
        lastRecenter = recenter
        updateFix(latitude, longitude, zoom = 16, headingBearing = headingBearing, headingUp = true)
    }

    /**
     * Applies camera positioning with 35.0° perspective tilt and heading bearing rotation.
     */
    private fun center(point: LatLng, zoom: Double, bearing: Float, headingUp: Boolean) {
        val targetBearing = if (headingUp) bearing.toDouble() else 0.0
        val targetTilt = if (headingUp) 35.0 else 0.0

        val cameraPosition = CameraPosition.Builder()
            .target(point)
            .zoom(zoom)
            .bearing(targetBearing)
            .tilt(targetTilt)
            .build()

        map?.cameraPosition = cameraPosition
    }

    private fun updateMarker(point: LatLng) {
        map?.style?.getSourceAs<GeoJsonSource>("sportage-gps")?.setGeoJson(
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))
                .also { it.addNumberProperty("bearing", bearing.toDouble()) }
        )
    }

    /** Solid triangular arrow pointing up (north) with a light outline. */
    private fun arrowIcon(): Bitmap {
        val size = 72
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0088BE.toInt() }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val tip = PointF(size * 0.5f, size * 0.10f)
        val left = PointF(size * 0.20f, size * 0.86f)
        val right = PointF(size * 0.80f, size * 0.86f)
        val tail = PointF(size * 0.5f, size * 0.70f)
        val path = android.graphics.Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(right.x, right.y)
            lineTo(tail.x, tail.y)
            lineTo(left.x, left.y)
            close()
        }
        canvas.drawPath(path, fill)
        canvas.drawPath(path, outline)
        return bmp
    }

    private fun start() {
        if (!started && !released) {
            mapView?.onStart()
            started = true
        }
    }

    private fun resume() {
        start()
        if (!resumed && !released) {
            mapView?.onResume()
            resumed = true
        }
    }

    private fun pause() {
        if (resumed) {
            mapView?.onPause()
            resumed = false
        }
    }

    private fun stop() {
        pause()
        if (started) {
            mapView?.onStop()
            started = false
        }
    }

    fun release() {
        if (released) return
        observer?.let { lifecycle.removeObserver(it) }
        watchdog.removeCallbacksAndMessages(null)
        runCatching { stop() }
        released = true
        runCatching { mapView?.onDestroy() }
        mapView = null
        map = null
        removeAllViews()
    }

    companion object {
        private val httpClient by lazy {
            OkHttpClient.Builder()
                .dispatcher(Dispatcher().apply { maxRequestsPerHost = 4 })
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "SportageOBD/1.2.0 (Android; github.com/farazsocialh-jpg/sportageobd)")
                            .build()
                    )
                }
                .build()
        }
    }
}
