package com.odys.mototriptracker.domain

/** Controls the foreground ride-tracking service. */
interface TripServiceController {
    fun startService()
    fun stopService()
    fun pauseService()
    fun resumeService()
}
