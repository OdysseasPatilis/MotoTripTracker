package com.odys.mototriptracker.domain

import android.location.Location
import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import com.odys.mototriptracker.data.trip.TripRepository
import io.objectbox.Box
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TripManager(
    private val speedFilter: SpeedFilter,
    private val stopDetector: StopDetector,
    private val tripRepository: TripRepository
) {
    private var currentTripId: Long = 0L
    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats.asStateFlow()

    private var lastLocation: Location? = null
    private var isTracking = false
    private var lastLocationTime: Long = 0L
    private var lastSpeedMps: Float = 0f

    val speedSmoother = SpeedSmoother()
    private var elevationSmoother = ElevationSmoother() // Made this a variable so we can reset it

    var sessionMaxSpeedKmh = 0

    fun startTrip() {
        isTracking = true
        lastLocation = null
        lastLocationTime = 0L
        lastSpeedMps = 0f
        sessionMaxSpeedKmh = 0

        // Reset the smoother for the new ride!
        elevationSmoother = ElevationSmoother()

        val startTimeMs = System.currentTimeMillis()
        _tripStats.value = TripStats(tripStartTime = startTimeMs)

        // Let the repo secure it on disk immediately
        currentTripId = tripRepository.startNewTrip(startTimeMs)
    }

    fun onLocationUpdate(location: Location) {
        if (!isTracking) return

        // 1. Accuracy Check
        if (!speedFilter.isValid(location)) return

        // 2. Extract Data
        val currentSpeedMps = speedFilter.getProcessedSpeed(location)
        val currentSpeedKmh = currentSpeedMps * 3.6f
        //val currentSpeedKmh = speedSmoother.getSmoothedSpeedKmh(currentSpeedMps).toFloat()
        val currentTime = location.time

        val rawSpeedKmh = (location.speed * 3.6f).toInt()
        if (rawSpeedKmh > sessionMaxSpeedKmh) {
            sessionMaxSpeedKmh = rawSpeedKmh
        }
        // 3. Pre-calculate Deltas (Outside the atomic update for speed)
        var elevationDelta = 0.0
        var distanceDelta = 0f
        var gForce = 0f

        lastLocation?.let { prev ->
            if (location.hasAltitude()) {
                elevationDelta = elevationSmoother.calculateGain(location.altitude)
            }
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
            var newMoving = currentStats.movingTime
            var newStopped = currentStats.stoppedTime

            stopDetector.updateTimes(currentTime) { movingDeltaMs, stoppedDeltaMs ->
                newMoving += (movingDeltaMs / 1000L)
                newStopped += (stoppedDeltaMs / 1000L)
                speedSmoother.reset()
            }

            val movingHours = newMoving / 3600f
            val totalKm = (currentStats.distanceMeters + distanceDelta) / 1000f
            val newAvgSpeed = if (movingHours > 0) totalKm / movingHours else 0f
            // Live Average Speed (Distance in km / Moving hours)
            /*var newAvgSpeed = calculateAverageSpeed(newMoving, currentStats.distanceMeters + distanceDelta)
            if (newAvgSpeed > sessionMaxSpeedKmh) {
                newAvgSpeed = sessionMaxSpeedKmh.toFloat()
            }*/
            // Smoothing the G-Force for the UI
            val smoothedG = (currentStats.currentGForce * 0.8f) + (gForce * 0.2f)

            currentStats.copy(
                speed = currentSpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = currentStats.distanceMeters + distanceDelta,
                maxSpeed = maxOf(currentStats.maxSpeed, sessionMaxSpeedKmh.toFloat()),
                avgSpeed = newAvgSpeed,
                totalElevationGain = (currentStats.totalElevationGain + elevationDelta).toFloat(),
                currentGForce = smoothedG,
                maxGForce = maxOf(currentStats.maxGForce, kotlin.math.abs(smoothedG))
            )
        }

        lastSpeedMps = currentSpeedMps
        lastLocationTime = currentTime
        lastLocation = location

        // Let the Repository handle the ObjectBox saving!
        tripRepository.addRoutePointAndUpdateStats(
            tripId = currentTripId,
            lat = location.latitude,
            lng = location.longitude,
            alt = location.altitude,
            speedMps = currentSpeedMps,
            timeMs = currentTime,
            runningStats = _tripStats.value
        )
    }

    fun stopTrip() {
        if (!isTracking) return
        isTracking = false
        speedSmoother.reset()

        val endTimeMs = System.currentTimeMillis()

        // 1. Flush the final silent stop time
        _tripStats.update { currentStats ->
            var finalMoving = currentStats.movingTime
            var finalStopped = currentStats.stoppedTime

            stopDetector.updateTimes(endTimeMs) { movingDeltaMs, stoppedDeltaMs ->
                finalMoving += (movingDeltaMs / 1000L)
                finalStopped += (stoppedDeltaMs / 1000L)
            }

            val movingHours = finalMoving / 3600f
            val totalKm = currentStats.distanceMeters / 1000f
            val finalAvgSpeed = if (movingHours > 0) totalKm / movingHours else 0f

            currentStats.copy(
                movingTime = finalMoving,
                stoppedTime = finalStopped,
                avgSpeed = finalAvgSpeed
            )
        }

        // 2. Tell the Repo to compress the Map string and seal the file!
        tripRepository.saveTrip(currentTripId, _tripStats.value)
    }

    fun calculateAverageSpeed(totalDistanceMeters: Long, movingTimeMillis: Float): Float {
        // 1. THE SHIELD: Don't calculate average speed until they have actually ridden a bit.
        // If they haven't moved at least 50 meters or ridden for 10 seconds, return 0.
        if (totalDistanceMeters < 50f || movingTimeMillis < 10_000L) {
            return 0f
        }

        // 2. Convert milliseconds to hours
        val hours = movingTimeMillis / 3600000.0 // 1000ms * 60s * 60m

        // 3. Convert meters to kilometers
        val kilometers = totalDistanceMeters / 1000.0

        // 4. Calculate Average Speed (Distance / Time)
        val averageSpeedKmh = kilometers / hours

        return averageSpeedKmh.toFloat()

    }
}
