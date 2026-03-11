package com.odys.mototriptracker.domain

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

class TripManager(
    private val speedFilter: SpeedFilter,
    private val stopDetector: StopDetector
) {

    private val _tripStats = MutableStateFlow(TripStats())
    val tripStats: StateFlow<TripStats> = _tripStats

    private var lastLocation: Location? = null
    private var lastUpdateTime: Long = 0

    fun startTrip() {
        _tripStats.value = TripStats(
            tripStartTime = System.currentTimeMillis()
        )
        lastLocation = null
        lastUpdateTime = System.currentTimeMillis()
    }

    fun onLocationUpdate(location: Location) {

        val filteredSpeed = speedFilter.filter(location.speed * 3.6f)

        val now = System.currentTimeMillis()
        val deltaTime = (now - lastUpdateTime) / 1000

        val stats = _tripStats.value

        val moving = stopDetector.isMoving(filteredSpeed)

        val newMovingTime =
            if (moving) stats.movingTime + deltaTime else stats.movingTime

        val newStoppedTime =
            if (!moving) stats.stoppedTime + deltaTime else stats.stoppedTime

        val distance = lastLocation?.distanceTo(location) ?: 0f

        val newDistance = stats.distanceMeters + distance

        val newMaxSpeed = max(stats.maxSpeed, filteredSpeed)

        _tripStats.value = stats.copy(
            speed = filteredSpeed,
            movingTime = newMovingTime,
            stoppedTime = newStoppedTime,
            distanceMeters = newDistance,
            maxSpeed = newMaxSpeed
        )

        lastLocation = location
        lastUpdateTime = now
    }

    fun stopTrip() {
        // nothing special for now
    }
}