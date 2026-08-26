package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity

data class RideMoment(
    val title: String,
    val value: String,
    val detail: String
)

data class RideMoments(
    val moments: List<RideMoment>
)

object RideMomentsCalculator {

    private const val STOP_SPEED_MPS = 0.5f
    private const val MIN_STOP_SECONDS = 15L
    private const val MIN_CLIMB_METERS = 8.0

    fun calculate(trip: TripEntity, points: List<RoutePointEntity>): RideMoments {
        val sorted = points.sortedBy { it.timestamp }
        val moments = buildList {
            if (trip.maxSpeed > 0f) {
                add(
                    RideMoment(
                        title = "Top speed",
                        value = "${trip.maxSpeed.toInt()} km/h",
                        detail = "Peak velocity this ride"
                    )
                )
            }
            if (trip.maxGForce > 0f) {
                add(
                    RideMoment(
                        title = "Max G",
                        value = String.format("%.2f G", trip.maxGForce),
                        detail = "Hardest acceleration spike"
                    )
                )
            }
            if (trip.elevationGain >= 5f) {
                add(
                    RideMoment(
                        title = "Elevation",
                        value = "+${trip.elevationGain.toInt()} m",
                        detail = "Total climbing"
                    )
                )
            }

            longestStopSeconds(sorted)?.let { stopSec ->
                if (stopSec >= MIN_STOP_SECONDS) {
                    add(
                        RideMoment(
                            title = "Longest stop",
                            value = formatDuration(stopSec),
                            detail = "Longest pause while recording"
                        )
                    )
                }
            }

            peakClimbMeters(sorted)?.let { climb ->
                if (climb >= MIN_CLIMB_METERS) {
                    add(
                        RideMoment(
                            title = "Biggest climb",
                            value = "+${climb.toInt()} m",
                            detail = "Steepest continuous ascent"
                        )
                    )
                }
            }

            if (trip.avgSpeed > 0f && trip.movingTime > 0L) {
                add(
                    RideMoment(
                        title = "Moving pace",
                        value = "${trip.avgSpeed.toInt()} km/h",
                        detail = "Average while moving"
                    )
                )
            }

            if (trip.maxLateralGForce > 0.15f) {
                add(
                    RideMoment(
                        title = "Max lean G",
                        value = String.format("%.2f G", trip.maxLateralGForce),
                        detail = "Peak lateral force"
                    )
                )
            }

            if (trip.cornerCount > 0) {
                add(
                    RideMoment(
                        title = "Corners",
                        value = trip.cornerCount.toString(),
                        detail = "Detected turns this ride"
                    )
                )
            }
        }

        return RideMoments(moments = moments.take(8))
    }

    private fun longestStopSeconds(points: List<RoutePointEntity>): Long? {
        if (points.size < 2) return null
        var longest = 0L
        var stopStart: Long? = null

        for (i in points.indices) {
            val point = points[i]
            val stopped = point.speedMps < STOP_SPEED_MPS
            if (stopped) {
                if (stopStart == null) stopStart = point.timestamp
            } else if (stopStart != null) {
                val durationSec = ((point.timestamp - stopStart) / 1000L).coerceAtLeast(0L)
                longest = maxOf(longest, durationSec)
                stopStart = null
            }
        }

        val last = points.last()
        if (stopStart != null && last.speedMps < STOP_SPEED_MPS) {
            val durationSec = ((last.timestamp - stopStart) / 1000L).coerceAtLeast(0L)
            longest = maxOf(longest, durationSec)
        }

        return longest.takeIf { it > 0L }
    }

    private fun peakClimbMeters(points: List<RoutePointEntity>): Double? {
        if (points.size < 3) return null
        var peak = 0.0
        var currentClimb = 0.0
        var prevAlt = points.first().altitude

        for (i in 1 until points.size) {
            val alt = points[i].altitude
            val delta = alt - prevAlt
            if (delta > 0.3) {
                currentClimb += delta
                peak = maxOf(peak, currentClimb)
            } else if (delta < -0.5) {
                currentClimb = 0.0
            }
            prevAlt = alt
        }

        return peak.takeIf { it > 0.0 }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
