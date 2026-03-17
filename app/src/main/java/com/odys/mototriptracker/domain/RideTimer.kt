package com.odys.mototriptracker.domain

import android.os.SystemClock

class RideTimer {
    var movingMillis = 0L
        private set
    var stoppedMillis = 0L
        private set

    private var lastTickTime = 0L
    private var lastLocationTime = 0L

    fun start() {
        val now = SystemClock.elapsedRealtime()
        lastTickTime = now
        lastLocationTime = now
    }

    // 🫀 THE HEARTBEAT: Called every 1 second by our Coroutine
    fun tick() {
        if (lastTickTime == 0L) return

        val now = SystemClock.elapsedRealtime()

        // How much time ACTUALLY passed? (Usually 1000ms, but if the
        // phone screen turned off and slept, this catches it perfectly!)
        val deltaMs = now - lastTickTime
        lastTickTime = now

        // How long has it been since the GPS last told us we moved?
        val timeSinceLastPing = now - lastLocationTime

        // AUTO-PAUSE: If it's been more than 5 minutes, ignore this time.
        if (timeSinceLastPing > 300_000L) {
            return
        }

        // THE GAP LOGIC:
        // If we haven't received a 2-meter movement ping in over 4 seconds, we are stopped.
        if (timeSinceLastPing > 4000L) {
            stoppedMillis += deltaMs
        } else {
            movingMillis += deltaMs
        }
    }

    // 📍 THE SWITCH: Called ONLY when the FusedLocationProvider gets a new ping
    fun registerLocationPing() {
        lastLocationTime = SystemClock.elapsedRealtime()
    }
}