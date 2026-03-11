package com.odys.mototriptracker.domain

class SpeedFilter(
    private val windowSize: Int = 5
) {

    private val speeds = ArrayDeque<Float>()

    fun filter(speed: Float): Float {
        speeds.addLast(speed)

        if (speeds.size > windowSize)
            speeds.removeFirst()

        return speeds.average().toFloat()
    }
}