package com.odys.mototriptracker.domain

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

class TripManager(
    private val speedFilter: SpeedFilter,
    private val stopDetector: StopDetector
) {

    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats.asStateFlow()

    private var lastLocation: Location? = null
    private var isTracking = false
    private var lastLocationTime: Long = 0L
    private var lastSpeedMps: Float = 0f
    fun startTrip() {
        isTracking = true
        lastLocation = null
        lastLocationTime = 0L
        lastSpeedMps = 0f
        _tripStats.value = TripStats(
            tripStartTime = System.currentTimeMillis()
        )
    }

    fun onLocationUpdate(location: Location) {
        if (!isTracking) return

        // 1. Accuracy Check
        if (!speedFilter.isValid(location)) return

        // 2. Extract Data
        val currentSpeedMps = speedFilter.getProcessedSpeed(location)
        val currentSpeedKmh = currentSpeedMps * 3.6f
        val currentTime = location.time

        // 3. Pre-calculate Deltas (Outside the atomic update for speed)
        var elevationDelta = 0.0
        var distanceDelta = 0f
        var gForce = 0f

        lastLocation?.let { prev ->
            // Elevation Gain (0.5m threshold to filter noise)
            if (location.hasAltitude() && prev.hasAltitude()) {
                val diff = location.altitude - prev.altitude
                if (diff > 0.5) elevationDelta = diff
            }

            // Distance (Only if moving to prevent GPS drift)
            if (currentSpeedMps > 0.1f) {
                distanceDelta = prev.distanceTo(location)
            }
        }

        // G-Force Calculation
        if (lastLocationTime > 0 && lastSpeedMps >= 0) {
            val deltaV = currentSpeedMps - lastSpeedMps
            val deltaT = (currentTime - lastLocationTime) / 1000f
            if (deltaT > 0.1f) { // Ensure a minimum time gap to avoid division by near-zero
                gForce = (deltaV / deltaT) / 9.81f
            }
        }

        // 4. Thread-safe atomic update
        _tripStats.update { currentStats ->

            // Time logic (Moving vs Stopped)
            var newMoving = currentStats.movingTime
            var newStopped = currentStats.stoppedTime
            stopDetector.updateTimes(currentSpeedMps, currentTime) { movingDeltaMs, stoppedDeltaMs ->
                newMoving += (movingDeltaMs / 1000L)
                newStopped += (stoppedDeltaMs / 1000L)
            }

            // Live Average Speed (Distance in km / Moving hours)
            val movingHours = newMoving / 3600f
            val totalKm = (currentStats.distanceMeters + distanceDelta) / 1000f
            val newAvgSpeed = if (movingHours > 0) totalKm / movingHours else 0f

            // Smoothing the G-Force for the UI
            val smoothedG = (currentStats.currentGForce * 0.8f) + (gForce * 0.2f)

            currentStats.copy(
                speed = currentSpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = currentStats.distanceMeters + distanceDelta,
                maxSpeed = maxOf(currentStats.maxSpeed, currentSpeedKmh),
                avgSpeed = newAvgSpeed, // Keep this updated for the UI
                totalElevationGain = (currentStats.totalElevationGain + elevationDelta).toFloat(),
                currentGForce = smoothedG,
                maxGForce = maxOf(currentStats.maxGForce, kotlin.math.abs(smoothedG))
            )
        }

        // 5. Update references for next iteration
        lastSpeedMps = currentSpeedMps
        lastLocationTime = currentTime
        lastLocation = location
    }

    fun stopTrip() {
        isTracking = false
    }
}