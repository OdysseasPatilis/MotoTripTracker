package com.odys.mototriptracker.domain

import android.location.Location
import com.odys.mototriptracker.data.trip.TripRepository
import com.odys.mototriptracker.util.AppLogger
import com.odys.mototriptracker.util.LogThrottle
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

    private val _routeCoordinates = MutableStateFlow<List<RouteCoordinate>>(emptyList())
    val routeCoordinates: StateFlow<List<RouteCoordinate>> = _routeCoordinates.asStateFlow()

    private var lastLocation: Location? = null
    private var isTracking = false
    private var isPaused = false

    private val speedSmoother = SpeedSmoother()
    private var elevationSmoother = ElevationSmoother()
    private val cornerDetector = CornerDetector()

    private var sessionMaxSpeedKmh = 0f

    fun startTrip() {
        isTracking = true
        isPaused = false
        lastLocation = null
        sessionMaxSpeedKmh = 0f
        _routeCoordinates.value = emptyList()
        stopDetector.reset()
        speedSmoother.reset()
        cornerDetector.reset()

        elevationSmoother = ElevationSmoother()
        gForceTracker.startTracking(resetSession = true)

        val startTimeMs = System.currentTimeMillis()
        _tripStats.value = TripStats(tripStartTime = startTimeMs)
        currentTripId = tripRepository.startNewTrip(startTimeMs)
        LogThrottle.resetAll()
        AppLogger.i(AppLogger.Category.TRIP, "Trip started id=$currentTripId")
        publishSession()
    }

    fun updateRoadSpeedLimit(kmh: Int) {
        if (!isTracking) return
        if (_tripStats.value.roadSpeedLimitKmh == kmh) return

        _tripStats.update { it.copy(roadSpeedLimitKmh = kmh) }
        AppLogger.i(AppLogger.Category.SPEED_LIMIT, "Road limit updated → $kmh km/h")
        publishSession()
    }

    fun pauseTrip() {
        if (!isTracking || isPaused) {
            AppLogger.d(
                AppLogger.Category.TRIP,
                "Pause ignored — tracking=$isTracking paused=$isPaused"
            )
            return
        }

        isPaused = true
        gForceTracker.stopTracking()
        stopDetector.reset()
        speedSmoother.reset()
        lastLocation = null

        _tripStats.update {
            it.copy(
                speed = 0f,
                currentGForce = 0f,
                currentLateralGForce = 0f
            )
        }
        AppLogger.i(AppLogger.Category.TRIP, "Trip paused ${AppLogger.tripSummary(_tripStats.value)}")
        publishSession()
    }

    fun resumeTrip() {
        if (!isTracking || !isPaused) {
            AppLogger.d(
                AppLogger.Category.TRIP,
                "Resume ignored — tracking=$isTracking paused=$isPaused"
            )
            return
        }

        isPaused = false
        stopDetector.reset()
        speedSmoother.reset()
        lastLocation = null
        gForceTracker.startTracking(resetSession = false)
        AppLogger.i(AppLogger.Category.TRIP, "Trip resumed ${AppLogger.tripSummary(_tripStats.value)}")
        publishSession()
    }

    fun onLocationUpdate(location: Location) {
        if (!isTracking) return

        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val gpsQuality = GpsQuality.fromAccuracyMeters(accuracy)

        // Keep the GPS signal indicator live while paused (iOS parity).
        if (isPaused) {
            _tripStats.update {
                it.copy(gpsAccuracyMeters = accuracy, gpsQuality = gpsQuality)
            }
            publishSession()
            return
        }

        if (!speedFilter.isValid(location)) {
            _tripStats.update {
                it.copy(gpsAccuracyMeters = accuracy, gpsQuality = gpsQuality)
            }
            publishSession()
            if (LogThrottle.shouldLog("trip.invalidGPS", 15_000L)) {
                AppLogger.d(
                    AppLogger.Category.TRIP,
                    "GPS rejected accuracy=${location.accuracy}m " +
                        "speed=${location.speed}m/s @ ${AppLogger.coordinate(location.latitude, location.longitude)}"
                )
            }
            return
        }

        val currentSpeedMps = speedFilter.getProcessedSpeed(location)
        val currentTime = location.time
        val isMoving = currentSpeedMps > MOVING_SPEED_MPS

        val displaySpeedKmh = if (isMoving) {
            speedSmoother.getSmoothedSpeedKmh(currentSpeedMps).toFloat()
        } else {
            speedSmoother.reset()
            0f
        }

        if (isMoving && displaySpeedKmh in 0f..MAX_PLAUSIBLE_SPEED_KMH) {
            sessionMaxSpeedKmh = maxOf(sessionMaxSpeedKmh, displaySpeedKmh)
        }

        if (isMoving) {
            cornerDetector.onLocation(location, currentSpeedMps)
        }

        var elevationDelta = 0.0
        var distanceDelta = 0f

        lastLocation?.let { prev ->
            if (location.hasAltitude()) {
                elevationDelta = elevationSmoother.calculateGain(location.altitude)
            }
            if (isMoving) {
                val step = prev.distanceTo(location)
                if (step in 0f..MAX_STEP_METERS) {
                    distanceDelta = step
                } else if (step > MAX_STEP_METERS) {
                    AppLogger.w(
                        AppLogger.Category.TRIP,
                        "Rejected GPS teleport step=${"%.1f".format(step)}m " +
                            "(max ${MAX_STEP_METERS}m)"
                    )
                }
            }
        }

        _tripStats.update { currentStats ->
            var newMoving = currentStats.movingTime
            var newStopped = currentStats.stoppedTime

            stopDetector.updateTimes(currentTime, isMoving) { movingDeltaMs, stoppedDeltaMs ->
                newMoving += movingDeltaMs / 1000L
                newStopped += stoppedDeltaMs / 1000L
            }

            val newDistance = currentStats.distanceMeters + distanceDelta
            val movingHours = newMoving / 3600f
            val newAvgSpeed = if (movingHours > 0f) {
                (newDistance / 1000f) / movingHours
            } else {
                0f
            }

            val maxLateral = maxOf(
                currentStats.maxLateralGForce,
                gForceTracker.maxSessionLateralGForce,
                cornerDetector.maxEstimatedLateralG
            )

            currentStats.copy(
                speed = displaySpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = newDistance,
                maxSpeed = maxOf(currentStats.maxSpeed, sessionMaxSpeedKmh),
                avgSpeed = newAvgSpeed,
                totalElevationGain = (currentStats.totalElevationGain + elevationDelta).toFloat(),
                currentGForce = gForceTracker.currentGForce,
                maxGForce = maxOf(currentStats.maxGForce, gForceTracker.maxSessionGForce),
                currentLateralGForce = gForceTracker.currentLateralGForce,
                maxLateralGForce = maxLateral,
                cornerCount = cornerDetector.cornerCount,
                gpsAccuracyMeters = accuracy,
                gpsQuality = gpsQuality
            )
        }

        lastLocation = location
        _routeCoordinates.update {
            it + RouteCoordinate(location.latitude, location.longitude)
        }

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

        if (LogThrottle.shouldLog("trip.location", 30_000L)) {
            AppLogger.i(
                AppLogger.Category.TRIP,
                "Tick @ ${AppLogger.coordinate(location.latitude, location.longitude)} — " +
                    AppLogger.tripSummary(_tripStats.value)
            )
        }
    }

    /**
     * Stops the ride. Returns `false` when the ride was discarded for being too short.
     */
    fun stopTrip(minDistanceMeters: Float = MIN_SAVE_DISTANCE_METERS): Boolean {
        if (!isTracking) {
            AppLogger.d(AppLogger.Category.TRIP, "Stop ignored — not tracking")
            return false
        }
        isTracking = false
        isPaused = false
        speedSmoother.reset()
        gForceTracker.stopTracking()
        val endTimeMs = System.currentTimeMillis()
        val stoppedTripId = currentTripId

        _tripStats.update { currentStats ->
            var finalMoving = currentStats.movingTime
            var finalStopped = currentStats.stoppedTime

            stopDetector.updateTimes(endTimeMs, isMoving = false) { movingDeltaMs, stoppedDeltaMs ->
                finalMoving += movingDeltaMs / 1000L
                finalStopped += stoppedDeltaMs / 1000L
            }

            val movingHours = finalMoving / 3600f
            val totalKm = currentStats.distanceMeters / 1000f
            val finalAvgSpeed = if (movingHours > 0f) totalKm / movingHours else 0f

            currentStats.copy(
                speed = 0f,
                currentGForce = 0f,
                currentLateralGForce = 0f,
                movingTime = finalMoving,
                stoppedTime = finalStopped,
                avgSpeed = finalAvgSpeed,
                cornerCount = cornerDetector.cornerCount,
                maxLateralGForce = maxOf(
                    currentStats.maxLateralGForce,
                    cornerDetector.maxEstimatedLateralG
                )
            )
        }

        val stats = _tripStats.value
        val saved = if (stats.distanceMeters < minDistanceMeters) {
            tripRepository.deleteTrip(stoppedTripId)
            AppLogger.i(
                AppLogger.Category.TRIP,
                "Trip discarded id=$stoppedTripId dist=${stats.distanceMeters}m < ${minDistanceMeters}m"
            )
            false
        } else {
            tripRepository.saveTrip(stoppedTripId, stats)
            AppLogger.i(
                AppLogger.Category.TRIP,
                "Trip stopped id=$stoppedTripId — ${AppLogger.tripSummary(stats)}"
            )
            true
        }

        _routeCoordinates.value = emptyList()
        currentTripId = 0L
        stopDetector.reset()
        cornerDetector.reset()
        LogThrottle.resetAll()
        publishSession()
        return saved
    }

    private fun publishSession() {
        _sessionState.value = RideSessionState(
            stats = _tripStats.value,
            isActive = isTracking,
            isPaused = isPaused
        )
    }

    companion object {
        const val MIN_SAVE_DISTANCE_METERS = 50f
        private const val MOVING_SPEED_MPS = 0.1f
        private const val MAX_PLAUSIBLE_SPEED_KMH = 300f
        private const val MAX_STEP_METERS = 80f
    }
}
