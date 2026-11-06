package com.sportage.obd.launcher

import kotlin.math.min

/**
 * Decides when the cockpit should switch into Drive Mode.
 *
 * Two thresholds and two hold times, because a single speed test flickers: a car crawling in traffic
 * would drop in and out of Drive Mode every few seconds. Entry needs a sustained [enterKmh], exit
 * needs the vehicle to be below [exitKmh] for much longer, and the gap between the thresholds is the
 * hysteresis band.
 */
class DriveModeController(
    enterKmh: Int = 25,
    exitKmh: Int = 8,
    private val enterHoldMs: Long = 3_000L,
    private val exitHoldMs: Long = 12_000L
) {
    var enterKmh: Int = enterKmh
        private set
    var exitKmh: Int = exitKmh
        private set

    var isActive: Boolean = false
        private set

    private var aboveSinceMs: Long? = null
    private var belowSinceMs: Long? = null

    /** Exit must stay strictly below entry, otherwise the state machine has no hysteresis band. */
    fun configure(enter: Int, exit: Int) {
        enterKmh = enter.coerceIn(5, 120)
        exitKmh = exit.coerceIn(1, enterKmh - 1)
    }

    /**
     * @param speedKmh null while the adapter is offline; the current mode is then kept, so a brief
     *                 Bluetooth dropout at motorway speed does not reveal the full cockpit.
     * @param allowAutoEntry false when the driver switched automation off. Manual entry and the
     *                 automatic exit-after-parking still work; only the speed-triggered entry is
     *                 suppressed, instead of tearing Drive Mode down on the next sample.
     * @return whether Drive Mode is active after this sample.
     */
    fun update(speedKmh: Int?, nowMs: Long, allowAutoEntry: Boolean = true): Boolean {
        val speed = speedKmh ?: return isActive
        if (isActive) {
            if (speed <= exitKmh) {
                val since = belowSinceMs ?: nowMs.also { belowSinceMs = it }
                if (nowMs - since >= exitHoldMs) {
                    isActive = false
                    belowSinceMs = null
                    aboveSinceMs = null
                }
            } else {
                belowSinceMs = null
            }
        } else {
            if (!allowAutoEntry) {
                aboveSinceMs = null
                return isActive
            }
            if (speed >= enterKmh) {
                val since = aboveSinceMs ?: nowMs.also { aboveSinceMs = it }
                if (nowMs - since >= enterHoldMs) {
                    isActive = true
                    aboveSinceMs = null
                    belowSinceMs = null
                }
            } else {
                aboveSinceMs = null
            }
        }
        return isActive
    }

    /** Driver tapped the Drive Mode button. Parking still exits automatically. */
    fun enterManually() {
        isActive = true
        aboveSinceMs = null
        belowSinceMs = null
    }

    fun exitManually() {
        isActive = false
        aboveSinceMs = null
        belowSinceMs = null
    }
}

/**
 * Parks the OBD link when the car has been stationary long enough.
 *
 * An idling engine still answers 30 requests a second, which on a head unit costs battery and heat
 * for data nobody is looking at. The rule only ever *pauses*: the driver's manual pause state is
 * never overridden by an automatic resume.
 */
class AutoPauseController(parkMinutes: Int = 10) {

    var parkMinutes: Int = parkMinutes
        private set

    private var stationarySinceMs: Long? = null
    private var triggered = false

    fun configure(minutes: Int) {
        parkMinutes = minutes.coerceIn(1, 120)
    }

    /**
     * @param speedKmh null when unknown (no link): treated as "not parked", because pausing on a
     *                 guess would silently stop a healthy session.
     * @return true when the vehicle has been parked for [parkMinutes] and polling should pause.
     */
    fun update(speedKmh: Int?, nowMs: Long): Boolean {
        if (speedKmh == null || speedKmh > PARKED_KMH) {
            stationarySinceMs = null
            triggered = false
            return false
        }
        val since = stationarySinceMs ?: nowMs.also { stationarySinceMs = it }
        if (!triggered && nowMs - since >= parkMinutes * 60_000L) {
            triggered = true
        }
        return triggered
    }

    fun reset() {
        stationarySinceMs = null
        triggered = false
    }

    companion object {
        /** Below this the ECU reports a standstill or a crawl; both count as parked. */
        const val PARKED_KMH = 3
    }
}

/**
 * Small helper for the night-dimming rule: the existing manual toggle gains an optional schedule.
 * Returns the level the screen should use, or null when dimming is off.
 */
object NightDim {
    fun levelFor(
        autoDim: Boolean,
        configuredLevel: Float,
        hourOfDay: Int,
        startHour: Int = 19,
        endHour: Int = 7
    ): Float? {
        if (!autoDim) return null
        val inWindow = if (startHour <= endHour) {
            hourOfDay in startHour until endHour
        } else {
            hourOfDay >= startHour || hourOfDay < endHour
        }
        return if (inWindow) configuredLevel.coerceIn(0.1f, 0.85f) else min(1f, configuredLevel + 0.25f)
    }
}
