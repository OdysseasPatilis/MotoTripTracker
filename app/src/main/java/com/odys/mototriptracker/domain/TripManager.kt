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

    fun startTrip() {
        isTracking = true
        lastLocation = null
        _tripStats.value = TripStats(
            tripStartTime = System.currentTimeMillis()
        )
    }

    fun onLocationUpdate(location: Location) {
        if (!isTracking) return

        // 1. Run it through the new SpeedFilter's bouncer
        if (!speedFilter.isValid(location)) {
            // Accuracy is too poor, throw this location away entirely
            return
        }

        // 2. Extract the cleaned speed using the new method (returns meters/second)
        val currentSpeedMps = speedFilter.getProcessedSpeed(location)
        val currentSpeedKmh = currentSpeedMps * 3.6f

        // 3. Calculate distance (only if we are actually moving)
        var addedDistanceMeters = 0f
        lastLocation?.let { prevLoc ->
            if (currentSpeedMps > 0f) {
                addedDistanceMeters = prevLoc.distanceTo(location)
            }
        }

        // 4. Thread-safe atomic update
        _tripStats.update { currentStats ->

            var newMoving = currentStats.movingTime
            var newStopped = currentStats.stoppedTime

            // 5. Let the new StopDetector handle the time math internally
            stopDetector.updateTimes(currentSpeedMps, location.time) { movingDeltaMs, stoppedDeltaMs ->
                // Assuming movingTime/stoppedTime are stored as Long (seconds) in your TripStats
                newMoving += (movingDeltaMs / 1000L)
                newStopped += (stoppedDeltaMs / 1000L)
            }

            val newDistance = currentStats.distanceMeters + addedDistanceMeters
            val newMaxSpeed = maxOf(currentStats.maxSpeed, currentSpeedKmh)

            currentStats.copy(
                speed = currentSpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = newDistance,
                maxSpeed = newMaxSpeed
            )
        }

        // 6. Store data for the next update calculation
        lastLocation = location
    }

    fun stopTrip() {
        isTracking = false
    }
}