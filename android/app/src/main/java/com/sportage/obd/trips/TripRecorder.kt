package com.sportage.obd.trips

/**
 * A completed drive. Times are epoch milliseconds so they survive timezone changes.
 *
 * The recorder is intentionally free of Android imports: the whole trip model is exercised by JVM
 * unit tests, which is the only way to keep distance/eco maths honest without a car.
 */
data class TripSummary(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceKm: Double,
    val durationSeconds: Long,
    val movingSeconds: Long,
    val maxSpeedKmh: Int,
    val avgSpeedKmh: Int,
    val zeroToHundredSeconds: Double? = null,
    val ecoScore: Int = 100,
    val fuelLitres: Double? = null,
    val vin: String? = null
) {
    val durationMinutes: Int get() = (durationSeconds / 60).toInt()
}

/** Live values the dashboard shows while a trip is running. */
data class TripProgress(
    val distanceKm: Double = 0.0,
    val elapsedSeconds: Long = 0,
    val maxSpeedKmh: Int = 0,
    val avgSpeedKmh: Int = 0,
    val zeroToHundredSeconds: Double? = null,
    val ecoScore: Int = 100
)

/**
 * Accumulates a trip from speed samples.
 *
 * Rules that keep the numbers defensible:
 * - distance is integrated with the trapezoid rule over *capped* sample gaps, so a phone that was
 *   asleep for ten minutes cannot invent kilometres;
 * - speed readings are ignored while the adapter is paused, but time still counts, because the trip
 *   really did last that long;
 * - the 0–100 km/h timer only starts from a genuine standstill and is discarded if the run takes
 *   longer than [MAX_ACCEL_SECONDS] (that was traffic, not acceleration).
 */
class TripRecorder(private val clock: () -> Long = System::currentTimeMillis) {

    // Explicit flag, not `startedAt > 0`: the caller supplies timestamps, and a trip that starts at
    // epoch 0 (unit tests, a device with a broken clock) must not silently restart on every sample.
    private var active = false
    private var startedAt = 0L
    private var lastSampleAt = 0L
    private var lastSpeed: Int? = null
    private var distanceKm = 0.0
    private var maxSpeed = 0
    private var movingSeconds = 0L
    private var fuelLitres = 0.0
    private var fuelSeen = false
    private var harshAccelerations = 0
    private var accelRunStartedAt: Long? = null
    private var zeroToHundred: Double? = null

    val isActive: Boolean get() = active

    fun start(atMs: Long = clock()) {
        active = true
        startedAt = atMs
        lastSampleAt = atMs
        lastSpeed = null
        distanceKm = 0.0
        maxSpeed = 0
        movingSeconds = 0L
        fuelLitres = 0.0
        fuelSeen = false
        harshAccelerations = 0
        accelRunStartedAt = null
        zeroToHundred = null
    }

    /**
     * Feeds one telemetry sample.
     *
     * @param speedKmh vehicle speed, or null when the adapter is paused/disconnected.
     * @param fuelRateLph instantaneous fuel consumption when the vehicle exposes it.
     */
    fun onSample(
        speedKmh: Int?,
        timestampMs: Long = clock(),
        fuelRateLph: Float? = null
    ): TripProgress {
        if (!isActive) start(timestampMs)
        if (timestampMs < lastSampleAt) return progress(timestampMs) // clock went backwards

        val rawGapSeconds = (timestampMs - lastSampleAt) / 1000.0
        val gapSeconds = rawGapSeconds.coerceIn(0.0, MAX_GAP_SECONDS)

        if (speedKmh != null && speedKmh >= 0) {
            if (gapSeconds > 0 && lastSpeed != null) {
                val average = (lastSpeed!! + speedKmh) / 2.0
                distanceKm += average * (gapSeconds / 3600.0)
            }
            if (speedKmh > 2) movingSeconds += gapSeconds.toLong()
            if (speedKmh > maxSpeed) maxSpeed = speedKmh

            if (fuelRateLph != null && fuelRateLph >= 0f && gapSeconds > 0) {
                fuelSeen = true
                fuelLitres += fuelRateLph * (gapSeconds / 3600.0)
            }

            // Harsh acceleration: > 12 km/h gained in at most 1.5 s.
            val previous = lastSpeed
            if (previous != null && rawGapSeconds in 0.1..1.5 && speedKmh - previous > 12) {
                harshAccelerations++
            }

            // 0-100 km/h run.
            if (speedKmh <= 2) {
                accelRunStartedAt = timestampMs
                if (zeroToHundred != null) zeroToHundred = null
            } else if (accelRunStartedAt != null && zeroToHundred == null && speedKmh >= 100) {
                val seconds = (timestampMs - accelRunStartedAt!!) / 1000.0
                if (seconds in 1.0..MAX_ACCEL_SECONDS) zeroToHundred = seconds
            }
        }

        lastSpeed = speedKmh
        lastSampleAt = timestampMs
        return progress(timestampMs)
    }

    fun progress(nowMs: Long = clock()): TripProgress {
        if (!isActive) return TripProgress()
        val elapsed = ((nowMs - startedAt) / 1000L).coerceAtLeast(0L)
        val avg = if (elapsed > 0) (distanceKm / (elapsed / 3600.0)).toInt() else 0
        return TripProgress(
            distanceKm = distanceKm,
            elapsedSeconds = elapsed,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = avg,
            zeroToHundredSeconds = zeroToHundred,
            ecoScore = ecoScore(elapsed)
        )
    }

    /** Ends the trip. Returns null when nothing worth keeping was recorded. */
    fun finish(atMs: Long = clock()): TripSummary? {
        if (!isActive) return null
        val summary = TripSummary(
            startedAtMs = startedAt,
            endedAtMs = atMs,
            distanceKm = round(distanceKm, 3),
            durationSeconds = ((atMs - startedAt) / 1000L).coerceAtLeast(0L),
            movingSeconds = movingSeconds,
            maxSpeedKmh = maxSpeed,
            avgSpeedKmh = progress(atMs).avgSpeedKmh,
            zeroToHundredSeconds = zeroToHundred?.let { round(it, 1) },
            ecoScore = ecoScore((atMs - startedAt) / 1000L),
            fuelLitres = if (fuelSeen) round(fuelLitres, 3) else null
        )
        active = false
        startedAt = 0L
        return if (summary.durationSeconds < 5 || summary.distanceKm < 0.01) null else summary
    }

    fun cancel() {
        active = false
        startedAt = 0L
    }

    private fun ecoScore(elapsedSeconds: Long): Int {
        if (elapsedSeconds < 30) return 100
        val idleFraction = (elapsedSeconds - movingSeconds).coerceAtLeast(0L).toDouble() / elapsedSeconds
        var score = 100.0
        score -= (idleFraction * 45.0).coerceAtMost(45.0)
        score -= (harshAccelerations * 4.0).coerceAtMost(20.0)
        if (maxSpeed > 130) score -= 5
        return score.toInt().coerceIn(0, 100)
    }

    private fun round(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return Math.round(value * factor) / factor
    }

    private companion object {
        const val MAX_GAP_SECONDS = 5.0
        const val MAX_ACCEL_SECONDS = 30.0
    }
}
