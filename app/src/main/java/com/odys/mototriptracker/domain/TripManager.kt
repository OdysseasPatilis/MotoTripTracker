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

    private var lastLocation: Location? = null
    private var isTracking = false
    private var isPaused = false

    private val speedSmoother = SpeedSmoother()
    private var elevationSmoother = ElevationSmoother()

    private var sessionMaxSpeedKmh = 0f

    fun startTrip() {
        isTracking = true
        isPaused = false
        lastLocation = null
        sessionMaxSpeedKmh = 0f
        stopDetector.reset()
        speedSmoother.reset()

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
        if (!isTracking || isPaused) return
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
        // Avoid a teleport jump across the pause gap when GPS resumes.
        lastLocation = null

        _tripStats.update { it.copy(speed = 0f, currentGForce = 0f) }
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
        if (!isTracking || isPaused) return

        if (!speedFilter.isValid(location)) {
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

        var elevationDelta = 0.0
        var distanceDelta = 0f

        lastLocation?.let { prev ->
            if (location.hasAltitude()) {
                elevationDelta = elevationSmoother.calculateGain(location.altitude)
            }
            // Only accumulate distance while actually moving to avoid GPS drift at stops.
            if (isMoving) {
                val step = prev.distanceTo(location)
                // Reject teleport jumps from bad GPS fixes.
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

            val hardwareCurrentG = gForceTracker.currentGForce
            val hardwareMaxG = maxOf(currentStats.maxGForce, gForceTracker.maxSessionGForce)

            currentStats.copy(
                speed = displaySpeedKmh,
                movingTime = newMoving,
                stoppedTime = newStopped,
                distanceMeters = newDistance,
                maxSpeed = maxOf(currentStats.maxSpeed, sessionMaxSpeedKmh),
                avgSpeed = newAvgSpeed,
                totalElevationGain = (currentStats.totalElevationGain + elevationDelta).toFloat(),
                currentGForce = hardwareCurrentG,
                maxGForce = hardwareMaxG
            )
        }

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

        if (LogThrottle.shouldLog("trip.location", 30_000L)) {
            AppLogger.i(
                AppLogger.Category.TRIP,
                "Tick @ ${AppLogger.coordinate(location.latitude, location.longitude)} — " +
                    AppLogger.tripSummary(_tripStats.value)
            )
        }
    }

    fun stopTrip() {
        if (!isTracking) {
            AppLogger.d(AppLogger.Category.TRIP, "Stop ignored — not tracking")
            return
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

            // Flush remaining interval after last GPS tick as stopped (button pressed while idle)
            // or leave attribution to last known state if still moving — treat as stopped at end.
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
                movingTime = finalMoving,
                stoppedTime = finalStopped,
                avgSpeed = finalAvgSpeed
            )
        }

        tripRepository.saveTrip(stoppedTripId, _tripStats.value)
        stopDetector.reset()
        AppLogger.i(
            AppLogger.Category.TRIP,
            "Trip stopped id=$stoppedTripId — ${AppLogger.tripSummary(_tripStats.value)}"
        )
        LogThrottle.resetAll()
        publishSession()
    }

    private fun publishSession() {
        _sessionState.value = RideSessionState(
            stats = _tripStats.value,
            isActive = isTracking,
            isPaused = isPaused
        )
    }

    companion object {
        /** Matches SpeedFilter floor (~0.1 m/s after zeroing drift under 3 km/h). */
        private const val MOVING_SPEED_MPS = 0.1f
        private const val MAX_PLAUSIBLE_SPEED_KMH = 300f
        private const val MAX_STEP_METERS = 80f
    }
}
