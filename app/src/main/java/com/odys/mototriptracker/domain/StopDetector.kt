package com.odys.mototriptracker.domain

class StopDetector(
    private val stopSpeedThreshold: Float = 2f // km/h
) {

    fun isMoving(speed: Float): Boolean {
        return speed > stopSpeedThreshold
    }
}