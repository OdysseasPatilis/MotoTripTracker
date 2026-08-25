package com.odys.mototriptracker.domain

import android.location.Location
import com.odys.mototriptracker.data.trip.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripManager @Inject constructor(
    private val speedFilter: SpeedFilter,
    private val stopDetector: StopDetector,
    private val tripRepository: TripRepository,
    private val gForceTracker: GForceTracker
) {
    private var currentTripId: Long = 0L
    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats.asStateFlow()

    private val _sessionState = MutableStateFlow(RideSessionState())
    val sessionState: StateFlow<RideSessionState> = _sessionState.asStateFlow()

    private var lastLocation: Location? = null
    private var isTracking = false
    private var isPaused = false
    private var lastLocationTime: Long = 0L
    private var lastSpeedMps: Float = 0f

    val speedSmoother = SpeedSmoother()
    private var elevationSmoother = ElevationSmoother()

    var sessionMaxSpeedKmh = 0

    fun startTrip() {
        isTracking = true
        isPaused = false
        lastLocation = null
        lastLocationTime = 0L
        lastSpeedMps = 0f
        sessionMaxSpeedKmh = 0
        stopDetector.reset()

        elevationSmoother = ElevationSmoother()
        gForceTracker.startTracking(resetSession = true)

        val startTimeMs = System.currentTimeMillis()
        _tripStats.value = TripStats(tripStartTime = startTimeMs)
        currentTripId = tripRepository.startNewTrip(startTimeMs)
        publishSession()
    }

    fun updateRoadSpeedLimit(kmh: Int) {
        if (!isTracking || isPaused) return
        if (_tripStats.value.roadSpeedLimitKmh == kmh) return

        _tripStats.update { it.copy(roadSpeedLimitKmh = kmh) }
        publishSession()
    }

    fun pauseTrip() {
        if (!isTracking || isPaused) return

        isPaused = true
        gForceTracker.stopTracking()
        stopDetector.reset()
        // Avoid a teleport jump across the pause gap when GPS resumes.
        lastLocation = null
        lastLocationTime = 0L
        lastSpeedMps = 0f

        _tripStats.update { it.copy(speed = 0f, currentGForce = 0f) }
        publishSession()
    }

    fun resumeTrip() {
        if (!isTracking || !isPaused) return

        isPaused = false
        stopDetector.reset()
        lastLocation = null
        lastLocationTime = 0L
        gForceTracker.startTracking(resetSession = false)
        publishSession()
    }

    fun onLocationUpdate(location: Location) {
        if (!isTracking || isPaused) return

        // 1. Accuracy Check
        if (!speedFilter.isValid(location)) return

        // 2. Extract Data
        val currentSpeedMps = speedFilter.getProcessedSpeed(location)
        val currentSpeedKmh = currentSpeedMps * 3.6f
        val currentTime = location.time

        val rawSpeedKmh = (location.speed * 3.6f).toInt()
        if (rawSpeedKmh > sessionMaxSpeedKmh) {
            sessionMaxSpeedKmh = rawSpeedKmh
        }

        var elevationDelta = 0.0
        var distanceDelta = 0f

        lastLocation?.let { prev ->
            if (location.hasAltitude()) {
                elevationDelta = elevationSmoother.calculateGain(location.altitude)
            }
            if (currentSpeedMps > 0.1f) {
                distanceDelta = prev.distanceTo(location)
            }
        }

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

            val hardwareCurrentG = gForceTracker.currentGForce
            val hardwareMaxG = maxOf(currentStats.maxGForce, gForceTracker.maxSessionGForce)

            currentStats.copy(
                speed = currentSpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = currentStats.distanceMeters + distanceDelta,
                maxSpeed = maxOf(currentStats.maxSpeed, sessionMaxSpeedKmh.toFloat()),
                avgSpeed = newAvgSpeed,
                totalElevationGain = (currentStats.totalElevationGain + elevationDelta).toFloat(),
                currentGForce = hardwareCurrentG,
                maxGForce = hardwareMaxG
            )
        }

        lastSpeedMps = currentSpeedMps
        lastLocationTime = currentTime
        lastLocation = location

        tripRepository.addRoutePointAndUpdateStats(
            tripId = currentTripId,
            lat = location.latitude,
            lng = location.longitude,
            alt = location.altitude,
            speedMps = currentSpeedMps,
            timeMs = currentTime,
            runningStats = _tripStats.value
        )
        publishSession()
    }

    fun stopTrip() {
        if (!isTracking) return
        isTracking = false
        isPaused = false
        speedSmoother.reset()
        gForceTracker.stopTracking()
        val endTimeMs = System.currentTimeMillis()

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
                speed = 0f,
                currentGForce = 0f,
                movingTime = finalMoving,
                stoppedTime = finalStopped,
                avgSpeed = finalAvgSpeed
            )
        }

        tripRepository.saveTrip(currentTripId, _tripStats.value)
        stopDetector.reset()
        publishSession()
    }

    private fun publishSession() {
        _sessionState.value = RideSessionState(
            stats = _tripStats.value,
            isActive = isTracking,
            isPaused = isPaused
        )
    }
}
