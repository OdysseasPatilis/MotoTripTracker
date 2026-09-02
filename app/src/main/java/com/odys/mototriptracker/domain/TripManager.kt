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
    private var pausedAtMs = 0L
    /** Last accepted motion state — used to keep the clock alive when accuracy is rejected. */
    private var lastWasMoving = false

    private val speedSmoother = SpeedSmoother()
    private var elevationSmoother = ElevationSmoother()
    private val cornerDetector = CornerDetector()

    private var sessionMaxSpeedKmh = 0f

    fun startTrip() {
        isTracking = true
        isPaused = false
        pausedAtMs = 0L
        lastLocation = null
        lastWasMoving = false
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

        val now = System.currentTimeMillis()
        // Flush time from the last GPS fix up to the pause button as stopped.
        _tripStats.update { current ->
            var moving = current.movingTime
            var stopped = current.stoppedTime
            stopDetector.updateTimes(now, isMoving = false) { movingDeltaMs, stoppedDeltaMs ->
                moving += movingDeltaMs / 1000L
                stopped += stoppedDeltaMs / 1000L
            }
            current.copy(
                speed = 0f,
                currentGForce = 0f,
                currentLateralGForce = 0f,
                movingTime = moving,
                stoppedTime = stopped
            )
        }

        isPaused = true
        pausedAtMs = now
        lastWasMoving = false
        gForceTracker.stopTracking()
        stopDetector.reset()
        speedSmoother.reset()
        lastLocation = null

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

        val now = System.currentTimeMillis()
        val pauseSeconds = if (pausedAtMs > 0L) {
            ((now - pausedAtMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }
        pausedAtMs = 0L
        isPaused = false
        stopDetector.reset()
        speedSmoother.reset()
        lastLocation = null
        gForceTracker.startTracking(resetSession = false)

        if (pauseSeconds > 0L) {
            _tripStats.update { it.copy(stoppedTime = it.stoppedTime + pauseSeconds) }
        }
        AppLogger.i(
            AppLogger.Category.TRIP,
            "Trip resumed (+${pauseSeconds}s stopped) ${AppLogger.tripSummary(_tripStats.value)}"
        )
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
            // Keep moving/stopped clocks alive during poor-accuracy stretches (screen-off, tunnels).
            _tripStats.update { current ->
                var moving = current.movingTime
                var stopped = current.stoppedTime
                stopDetector.updateTimes(location.time, isMoving = lastWasMoving) { movingDeltaMs, stoppedDeltaMs ->
                    moving += movingDeltaMs / 1000L
                    stopped += stoppedDeltaMs / 1000L
                }
                current.copy(
                    movingTime = moving,
                    stoppedTime = stopped,
                    gpsAccuracyMeters = accuracy,
                    gpsQuality = gpsQuality
                )
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
        lastWasMoving = isMoving
        val rawSpeedKmh = currentSpeedMps * 3.6f

        val displaySpeedKmh = if (isMoving) {
            speedSmoother.getSmoothedSpeedKmh(currentSpeedMps).toFloat()
        } else {
            speedSmoother.reset()
            0f
        }

        // Peak uses raw GPS speed so trip max aligns with TOP_SPEED waypoints.
        if (isMoving && rawSpeedKmh in 0f..MAX_PLAUSIBLE_SPEED_KMH) {
            sessionMaxSpeedKmh = maxOf(sessionMaxSpeedKmh, rawSpeedKmh)
        }

        if (isMoving) {
            cornerDetector.onLocation(location, currentSpeedMps)
        }

        var elevationDelta = 0.0
        val prev = lastLocation
        if (prev != null && location.hasAltitude()) {
            elevationDelta = elevationSmoother.calculateGain(location.altitude)
        }

        _tripStats.update { currentStats ->
            var newMoving = currentStats.movingTime
            var newStopped = currentStats.stoppedTime

            val timeAccepted = stopDetector.updateTimes(currentTime, isMoving) { movingDeltaMs, stoppedDeltaMs ->
                newMoving += movingDeltaMs / 1000L
                newStopped += stoppedDeltaMs / 1000L
            }

            // Never grow distance when the time gap was discarded — that produces absurd avg speeds.
            val distanceDelta = if (isMoving && timeAccepted && prev != null) {
                val step = prev.distanceTo(location).toDouble()
                val timeDeltaSec = (currentTime - prev.time).coerceAtLeast(0L) / 1000.0
                RideDistanceFilter.distanceDelta(
                    geographicMeters = step,
                    speedMps = currentSpeedMps.toDouble(),
                    timeDeltaSeconds = timeDeltaSec
                ).toFloat()
            } else {
                0f
            }

            if (prev != null && isMoving && timeAccepted) {
                val step = prev.distanceTo(location)
                if (step > 0f && distanceDelta == 0f) {
                    AppLogger.w(
                        AppLogger.Category.TRIP,
                        "Rejected GPS step=${"%.1f".format(step)}m " +
                            "spd=${"%.1f".format(currentSpeedMps)}m/s"
                    )
                }
            }

            val newDistance = currentStats.distanceMeters + distanceDelta
            val peakForAvg = maxOf(sessionMaxSpeedKmh, rawSpeedKmh).toDouble()
            val newAvgSpeed = RideDistanceFilter.averageSpeedKmh(
                distanceMeters = newDistance.toDouble(),
                movingTimeSeconds = newMoving,
                maxSpeedKmh = peakForAvg
            )

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
     * Stops the ride. Returns whether the trip was saved and its local id when saved.
     */
    fun stopTrip(minDistanceMeters: Float = MIN_SAVE_DISTANCE_METERS): StopTripResult {
        if (!isTracking) {
            AppLogger.d(AppLogger.Category.TRIP, "Stop ignored — not tracking")
            return StopTripResult(saved = false, tripId = null)
        }

        val endTimeMs = System.currentTimeMillis()
        val startTimeMs = _tripStats.value.tripStartTime
        val openPauseSeconds = if (isPaused && pausedAtMs > 0L) {
            ((endTimeMs - pausedAtMs) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }

        isTracking = false
        isPaused = false
        pausedAtMs = 0L
        lastWasMoving = false
        speedSmoother.reset()
        gForceTracker.stopTracking()
        val stoppedTripId = currentTripId

        _tripStats.update { currentStats ->
            var finalMoving = currentStats.movingTime
            var finalStopped = currentStats.stoppedTime + openPauseSeconds

            // If we weren't paused, flush from last GPS fix to Stop.
            stopDetector.updateTimes(endTimeMs, isMoving = false) { movingDeltaMs, stoppedDeltaMs ->
                finalMoving += movingDeltaMs / 1000L
                finalStopped += stoppedDeltaMs / 1000L
            }

            // Wall-clock session length is the source of truth (matches route replay).
            // Any unaccounted seconds (GPS blackouts, etc.) count as moving.
            if (startTimeMs > 0L) {
                val wallSeconds = ((endTimeMs - startTimeMs) / 1000L).coerceAtLeast(0L)
                val accounted = finalMoving + finalStopped
                if (wallSeconds > accounted) {
                    finalMoving += wallSeconds - accounted
                }
            }

            val finalAvgSpeed = RideDistanceFilter.averageSpeedKmh(
                distanceMeters = currentStats.distanceMeters.toDouble(),
                movingTimeSeconds = finalMoving,
                maxSpeedKmh = maxOf(currentStats.maxSpeed, sessionMaxSpeedKmh).toDouble()
            )

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
        return StopTripResult(saved = saved, tripId = if (saved) stoppedTripId else null)
    }

    data class StopTripResult(
        val saved: Boolean,
        val tripId: Long?,
    )

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
    }
}
