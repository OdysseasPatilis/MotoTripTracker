package com.odys.mototriptracker.domain

interface SpeedLimitProvider {
    suspend fun getSpeedLimitKmh(latitude: Double, longitude: Double): Int?
}
