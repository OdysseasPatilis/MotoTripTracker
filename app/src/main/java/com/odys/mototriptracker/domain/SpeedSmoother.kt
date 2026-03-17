package com.odys.mototriptracker.domain

import kotlin.math.roundToInt

class SpeedSmoother(
    // A window of 3 is the sweet spot.
    // Higher (e.g., 5) is smoother but causes a lag when braking.
    private val windowSize: Int = 3
) {
    private val speedBuffer = ArrayDeque<Float>()

    fun getSmoothedSpeedKmh(newSpeedMps: Float): Int {
        // 1. Convert meters per second to kilometers per hour
        val speedKmh = newSpeedMps * 3.6f

        // 2. Add the newest speed to our list
        speedBuffer.addLast(speedKmh)

        // 3. If our list gets too long, kick out the oldest speed
        if (speedBuffer.size > windowSize) {
            speedBuffer.removeFirst()
        }

        // 4. Calculate the average of the buffer
        val averageSpeed = speedBuffer.average()

        // 5. Round it to a nice whole number for the UI display
        return averageSpeed.roundToInt()
    }

    fun reset() {
        speedBuffer.clear()
    }
}