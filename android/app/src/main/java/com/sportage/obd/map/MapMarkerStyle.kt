package com.sportage.obd.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** How the vehicle position is drawn. Shared by the GL and the offline Mapsforge renderer. */
enum class MapMarkerStyle(val title: String) {
    DOT("Blue dot"),
    ARROW("Arrow"),
    CAR("3D car");

    companion object {
        /** Direction-of-travel arrow stays the default, matching the long-standing map look. */
        fun saved(value: String?): MapMarkerStyle = entries.find { it.name == value } ?: ARROW
    }
}

/**
 * Marker artwork is generated instead of shipped as an asset, so the three styles stay visually
 * consistent in both engines and pick up the map's blue.
 *
 * @param screenDegrees clockwise angle the icon must point at on screen, e.g. the driving
 * direction on a north-up map, or 0 while the map itself is rotated course-up.
 */
object MarkerIcons {
    fun create(style: MapMarkerStyle, sizePx: Int, screenDegrees: Float = 0f): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Canvas rotates clockwise for positive degrees, which is how map bearings run.
        if (screenDegrees != 0f) canvas.rotate(screenDegrees, size / 2f, size / 2f)
        when (style) {
            MapMarkerStyle.DOT -> drawDot(canvas, size)
            MapMarkerStyle.ARROW -> drawArrow(canvas, size)
            MapMarkerStyle.CAR -> drawCar(canvas, size)
        }
        return bmp
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int, width: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

    /** Classic blue position dot: filled disc with a white ring and a soft top highlight. */
    private fun drawDot(canvas: Canvas, size: Int) {
        val s = size.toFloat()
        val center = s * 0.5f
        canvas.drawCircle(center, center, s * 0.40f, paint(0x33000000))
        canvas.drawCircle(center, center, s * 0.36f, paint(0xFFF7FBFF.toInt()))
        canvas.drawCircle(center, center, s * 0.29f, paint(0xFF0088BE.toInt()))
        canvas.drawCircle(center - s * 0.07f, center - s * 0.09f, s * 0.09f, paint(0x55FFFFFF))
    }

    /** Solid triangular arrow pointing up with a light outline; rotated by the caller. */
    private fun drawArrow(canvas: Canvas, size: Int) {
        val s = size.toFloat()
        val path = Path().apply {
            moveTo(s * 0.50f, s * 0.10f)
            lineTo(s * 0.80f, s * 0.86f)
            lineTo(s * 0.50f, s * 0.70f)
            lineTo(s * 0.20f, s * 0.86f)
            close()
        }
        canvas.drawPath(path, paint(0xFF0088BE.toInt()))
        canvas.drawPath(path, stroke(android.graphics.Color.WHITE, s * 0.07f))
    }

    /**
     * Top-down car drawn nose-up: lit hood, dark glass cabin, shaded trunk, wheels tucked under
     * the body and a drop shadow. Flat colours only (no shaders) so every renderer agrees.
     */
    private fun drawCar(canvas: Canvas, size: Int) {
        val s = size.toFloat()
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) =
            canvas.drawRoundRect(RectF(s * l, s * t, s * r, s * b), s * radius, s * radius, paint(color))

        // Drop shadow keeps the car readable over light and dark map styles.
        canvas.drawOval(RectF(s * 0.22f, s * 0.16f, s * 0.78f, s * 0.96f), paint(0x33000000))

        // Wheels first: the body covers their inner half, so they peek out like real tyres.
        rect(0.17f, 0.24f, 0.29f, 0.40f, 0.03f, 0xFF20242A.toInt())
        rect(0.17f, 0.60f, 0.29f, 0.76f, 0.03f, 0xFF20242A.toInt())
        rect(0.71f, 0.24f, 0.83f, 0.40f, 0.03f, 0xFF20242A.toInt())
        rect(0.71f, 0.60f, 0.83f, 0.76f, 0.03f, 0xFF20242A.toInt())

        // Car silhouette: narrow rounded nose, wide body, squared-off tail.
        val body = Path().apply {
            moveTo(s * 0.31f, s * 0.925f)
            lineTo(s * 0.255f, s * 0.80f)
            lineTo(s * 0.245f, s * 0.34f)
            quadTo(s * 0.25f, s * 0.18f, s * 0.375f, s * 0.125f)
            lineTo(s * 0.625f, s * 0.125f)
            quadTo(s * 0.75f, s * 0.18f, s * 0.755f, s * 0.34f)
            lineTo(s * 0.745f, s * 0.80f)
            lineTo(s * 0.69f, s * 0.925f)
            quadTo(s * 0.655f, s * 0.955f, s * 0.50f, s * 0.955f)
            quadTo(s * 0.345f, s * 0.955f, s * 0.31f, s * 0.925f)
            close()
        }
        canvas.drawPath(body, paint(0xFF1D8FCB.toInt()))

        // Panels are clipped to the silhouette so nothing spills past the bodywork.
        canvas.save()
        canvas.clipPath(body)
        // Hood catches the light, trunk sits in shadow: the suggestion of 3D depth.
        rect(0.24f, 0.12f, 0.76f, 0.31f, 0.07f, 0xFF57BCEA.toInt())
        rect(0.24f, 0.72f, 0.76f, 0.96f, 0.06f, 0xFF1577AC.toInt())
        // Cabin glass with a windscreen reflection strip.
        rect(0.30f, 0.34f, 0.70f, 0.68f, 0.09f, 0xFF16303D.toInt())
        rect(0.31f, 0.36f, 0.69f, 0.42f, 0.02f, 0xFF4B86A6.toInt())
        rect(0.32f, 0.57f, 0.68f, 0.64f, 0.03f, 0xFF2E6480.toInt())
        // Headlights and tail lights complete the front/back distinction.
        rect(0.30f, 0.145f, 0.42f, 0.20f, 0.02f, 0xFFFFF4C2.toInt())
        rect(0.58f, 0.145f, 0.70f, 0.20f, 0.02f, 0xFFFFF4C2.toInt())
        rect(0.30f, 0.86f, 0.42f, 0.92f, 0.02f, 0xFFFF4A3D.toInt())
        rect(0.58f, 0.86f, 0.70f, 0.92f, 0.02f, 0xFFFF4A3D.toInt())
        canvas.restore()
        canvas.drawPath(body, stroke(android.graphics.Color.WHITE, s * 0.025f))
    }
}
