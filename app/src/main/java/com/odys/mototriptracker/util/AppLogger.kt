package com.odys.mototriptracker.util

import android.util.Log
import com.odys.mototriptracker.domain.TripStats

/**
 * Unified logging for MotoTripTracker.
 *
 * Filter in Logcat with tag prefix `MotoTrip` or a category, e.g.:
 *   adb logcat -s 'MotoTrip/Trip:*' 'MotoTrip/Location:*' 'MotoTrip/SpeedLimit:*'
 */
object AppLogger {
    private const val TAG_PREFIX = "MotoTrip"

    object Category {
        const val APP = "App"
        const val LOCATION = "Location"
        const val TRIP = "Trip"
        const val PERSISTENCE = "Persistence"
        const val SPEED_LIMIT = "SpeedLimit"
        const val WAYPOINT = "Waypoint"
        const val SENSORS = "Sensors"
        const val SERVICE = "Service"
        const val UI = "UI"
    }

    fun v(category: String, message: String) = Log.v(tag(category), message)
    fun d(category: String, message: String) = Log.d(tag(category), message)
    fun i(category: String, message: String) = Log.i(tag(category), message)
    fun w(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag(category), message, throwable)
        else Log.w(tag(category), message)
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag(category), message, throwable)
        else Log.e(tag(category), message)
    }

    fun coordinate(lat: Double, lon: Double): String =
        String.format("%.5f,%.5f", lat, lon)

    fun tripSummary(stats: TripStats): String = String.format(
        "dist=%.2fkm speed=%.0f avg=%.0f max=%.0f moving=%ds stopped=%ds elev=%.0fm maxG=%.2f latG=%.2f corners=%d gps=%s limit=%s",
        stats.distanceKm,
        stats.speed,
        stats.avgSpeed,
        stats.maxSpeed,
        stats.movingTime,
        stats.stoppedTime,
        stats.totalElevationGain,
        stats.maxGForce,
        stats.maxLateralGForce,
        stats.cornerCount,
        stats.gpsQuality.name,
        stats.roadSpeedLimitKmh?.toString() ?: "-"
    )

    private fun tag(category: String): String = "$TAG_PREFIX/$category"
}

/**
 * Prevents log floods from 1 Hz GPS / sensor streams.
 */
object LogThrottle {
    private val lastLoggedMs = mutableMapOf<String, Long>()

    @Synchronized
    fun shouldLog(key: String, intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = lastLoggedMs[key]
        if (last != null && now - last < intervalMs) return false
        lastLoggedMs[key] = now
        return true
    }

    @Synchronized
    fun reset(key: String) {
        lastLoggedMs.remove(key)
    }

    @Synchronized
    fun resetAll() {
        lastLoggedMs.clear()
    }
}
