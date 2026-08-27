package com.odys.mototriptracker.domain

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RideMoment(
    val id: String,
    val title: String,
    val value: String,
    val detail: String,
    /** Material-ish key used by the summary UI (parity with iOS SF Symbol intent). */
    val iconKey: String
)

data class RideMoments(
    val moments: List<RideMoment>
)

/**
 * Builds story-style ride highlights from the route — not a second copy of Stats.
 * Parity with iOS `RideMomentsCalculator`.
 *
 * Android route timestamps are epoch **milliseconds**; trip moving/stopped times are **seconds**.
 */
object RideMomentsCalculator {

    private const val STOP_SPEED_MPS = 0.5f
    private const val MIN_STOP_SECONDS = 20L
    private const val MIN_CLIMB_METERS = 12.0
    private const val MIN_DESCENT_METERS = 12.0
    private const val CRUISE_WINDOW_MS = 45_000L
    private const val MIN_CRUISE_KMH = 35.0

    fun calculate(trip: TripEntity, points: List<RoutePointEntity>): RideMoments {
        val sorted = points.sortedBy { it.timestamp }
        val startMs = sorted.firstOrNull()?.timestamp
            ?: return RideMoments(moments = ambientMoments(trip, sorted))

        val moments = buildList {
            topSpeedHighlight(sorted, startMs)?.let(::add)
            hardestPull(sorted, startMs)?.let(::add)
            peakClimb(sorted, startMs)?.let(::add)
            peakDescent(sorted, startMs)?.let(::add)
            summitHighlight(sorted, startMs)?.let(::add)
            longestStop(sorted, startMs)?.let(::add)
            sustainedCruise(sorted, startMs)?.let(::add)
            twistiesHighlight(trip)?.let(::add)
            flowHighlight(trip)?.let(::add)
            addAll(ambientMoments(trip, sorted))
        }

        val seen = linkedSetOf<String>()
        val unique = moments.filter { seen.add(it.id) }
        return RideMoments(moments = unique.take(6))
    }

    private fun topSpeedHighlight(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        val best = points.maxByOrNull { it.speedMps } ?: return null
        if (best.speedMps <= 1f) return null
        val kmh = (best.speedMps * 3.6f).roundToInt()
        val elapsedSec = ((best.timestamp - rideStartMs) / 1000L).coerceAtLeast(0L)
        val distanceM = distanceAlongRoute(to = best, points = points)
        return RideMoment(
            id = "peak-speed",
            title = "Peak rush",
            value = "$kmh km/h",
            detail = "Hit ${elapsedLabel(elapsedSec)} in · ${"%.1f".format(distanceM / 1000.0)} km mark",
            iconKey = "speed"
        )
    }

    private fun hardestPull(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        if (points.size < 3) return null
        var bestG = 0.0
        var bestIndex = 0

        for (i in 1 until points.size) {
            val dtMs = points[i].timestamp - points[i - 1].timestamp
            if (dtMs <= 200L || dtMs >= 5_000L) continue
            val dv = points[i].speedMps - points[i - 1].speedMps
            if (dv <= 0f) continue
            val g = min((dv / (dtMs / 1000.0) / 9.81), 1.2)
            if (g > bestG) {
                bestG = g
                bestIndex = i
            }
        }

        if (bestG < 0.25) return null
        val elapsedSec = ((points[bestIndex].timestamp - rideStartMs) / 1000L).coerceAtLeast(0L)
        return RideMoment(
            id = "hard-pull",
            title = "Hardest pull",
            value = String.format("%.2f G", bestG),
            detail = "Strongest acceleration · ${elapsedLabel(elapsedSec)} in",
            iconKey = "bolt"
        )
    }

    private fun peakClimb(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        val segment = bestAltitudeSegment(points, ascending = true) ?: return null
        if (segment.gain < MIN_CLIMB_METERS) return null
        val elapsedSec = ((segment.endTimeMs - rideStartMs) / 1000L).coerceAtLeast(0L)
        return RideMoment(
            id = "biggest-climb",
            title = "Biggest climb",
            value = "+${segment.gain.roundToInt()} m",
            detail = "Steepest continuous ascent · ended ${elapsedLabel(elapsedSec)} in",
            iconKey = "terrain"
        )
    }

    private fun peakDescent(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        val segment = bestAltitudeSegment(points, ascending = false) ?: return null
        if (segment.gain < MIN_DESCENT_METERS) return null
        val elapsedSec = ((segment.endTimeMs - rideStartMs) / 1000L).coerceAtLeast(0L)
        return RideMoment(
            id = "biggest-drop",
            title = "Biggest drop",
            value = "−${segment.gain.roundToInt()} m",
            detail = "Longest downhill stretch · ${elapsedLabel(elapsedSec)} in",
            iconKey = "descent"
        )
    }

    private fun summitHighlight(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        val top = points.maxByOrNull { it.altitude } ?: return null
        val floor = points.minOfOrNull { it.altitude } ?: top.altitude
        val rise = top.altitude - floor
        if (rise < 15.0) return null
        val elapsedSec = ((top.timestamp - rideStartMs) / 1000L).coerceAtLeast(0L)
        return RideMoment(
            id = "summit",
            title = "Summit",
            value = "${top.altitude.roundToInt()} m",
            detail = "Highest point · ${elapsedLabel(elapsedSec)} into the ride",
            iconKey = "flag"
        )
    }

    private fun longestStop(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        val stop = longestStopSegment(points) ?: return null
        if (stop.durationSec < MIN_STOP_SECONDS) return null
        val elapsedSec = ((stop.startTimeMs - rideStartMs) / 1000L).coerceAtLeast(0L)
        val whenLabel = when {
            elapsedSec < 90L -> "near the start"
            points.lastOrNull()?.let { stop.startTimeMs > it.timestamp - 120_000L } == true ->
                "near the end"
            else -> "${elapsedLabel(elapsedSec)} in"
        }
        return RideMoment(
            id = "longest-stop",
            title = "Longest pause",
            value = formatDuration(stop.durationSec),
            detail = "Stopped $whenLabel",
            iconKey = "pause"
        )
    }

    private fun sustainedCruise(
        points: List<RoutePointEntity>,
        rideStartMs: Long
    ): RideMoment? {
        if (points.size < 8) return null
        var bestAvg = 0.0
        var bestEndMs = rideStartMs
        var left = 0

        for (right in points.indices) {
            while (left < right && points[right].timestamp - points[left].timestamp > CRUISE_WINDOW_MS) {
                left++
            }
            val span = points[right].timestamp - points[left].timestamp
            if (span < (CRUISE_WINDOW_MS * 0.75).toLong()) continue

            var sum = 0.0
            val count = (right - left + 1).toDouble()
            for (i in left..right) sum += points[i].speedMps
            val avgKmh = (sum / count) * 3.6
            if (avgKmh > bestAvg) {
                bestAvg = avgKmh
                bestEndMs = points[right].timestamp
            }
        }

        if (bestAvg < MIN_CRUISE_KMH) return null
        val elapsedSec = ((bestEndMs - rideStartMs) / 1000L).coerceAtLeast(0L)
        return RideMoment(
            id = "cruise",
            title = "Best cruise",
            value = "${bestAvg.roundToInt()} km/h",
            detail = "Fastest ~${CRUISE_WINDOW_MS / 1000}s stretch · ${elapsedLabel(elapsedSec)} in",
            iconKey = "wind"
        )
    }

    private fun twistiesHighlight(trip: TripEntity): RideMoment? {
        val distanceKm = trip.distanceMeters / 1000.0
        if (trip.cornerCount < 3 || distanceKm < 1.0) return null
        val per10 = trip.cornerCount / distanceKm * 10.0
        if (per10 < 4.0) return null
        val vibe = when {
            per10 >= 12 -> "Proper twisties"
            per10 >= 8 -> "Plenty of bends"
            else -> "Nice flowing turns"
        }
        return RideMoment(
            id = "twisties",
            title = "Twisties",
            value = String.format("%.0f / 10 km", per10),
            detail = "$vibe · ${trip.cornerCount} corners total",
            iconKey = "twisties"
        )
    }

    private fun flowHighlight(trip: TripEntity): RideMoment? {
        val total = trip.movingTime + trip.stoppedTime
        if (total < 300L) return null
        val movingRatio = trip.movingTime.toDouble() / total.toDouble()
        if (movingRatio >= 0.88) {
            return RideMoment(
                id = "flow",
                title = "Open road",
                value = "${(movingRatio * 100).roundToInt()}% moving",
                detail = "Barely stopped — a flowing ride",
                iconKey = "open_road"
            )
        }
        if (movingRatio <= 0.55 && trip.stoppedTime >= 120L) {
            return RideMoment(
                id = "flow",
                title = "Stop & go",
                value = formatDuration(trip.stoppedTime),
                detail = "Lots of pausing — city traffic or sightseeing",
                iconKey = "stop_go"
            )
        }
        return null
    }

    private fun ambientMoments(
        trip: TripEntity,
        points: List<RoutePointEntity>
    ): List<RideMoment> {
        val hour = Calendar.getInstance().apply { timeInMillis = trip.startTime }
            .get(Calendar.HOUR_OF_DAY)
        val items = mutableListOf<RideMoment>()

        when (hour) {
            in 5..7 -> items += RideMoment(
                id = "time-of-day",
                title = "Dawn ride",
                value = formatDate(trip.startTime),
                detail = "Early start — roads are usually quieter",
                iconKey = "dawn"
            )
            in 20..23, in 0..4 -> items += RideMoment(
                id = "time-of-day",
                title = "Night ride",
                value = formatDate(trip.startTime),
                detail = "After-dark session",
                iconKey = "night"
            )
        }

        val distanceKm = trip.distanceMeters / 1000.0
        if (points.size >= 2 && distanceKm >= 15.0 && trip.maxSpeed >= 100f) {
            items += RideMoment(
                id = "long-haul",
                title = "Distance run",
                value = String.format("%.0f km", distanceKm),
                detail = "A proper mileage day with highway pace",
                iconKey = "distance"
            )
        }

        return items
    }

    private data class AltitudeSegment(val gain: Double, val endTimeMs: Long)
    private data class StopSegment(val durationSec: Long, val startTimeMs: Long)

    private fun bestAltitudeSegment(
        points: List<RoutePointEntity>,
        ascending: Boolean
    ): AltitudeSegment? {
        if (points.size < 3) return null
        var peak = 0.0
        var peakEnd = points[0].timestamp
        var current = 0.0
        var prev = points[0].altitude

        for (i in 1 until points.size) {
            val alt = points[i].altitude
            val delta = if (ascending) alt - prev else prev - alt
            if (delta > 0.3) {
                current += delta
                if (current > peak) {
                    peak = current
                    peakEnd = points[i].timestamp
                }
            } else if (delta < -0.5) {
                current = 0.0
            }
            prev = alt
        }
        return if (peak > 0) AltitudeSegment(peak, peakEnd) else null
    }

    private fun longestStopSegment(points: List<RoutePointEntity>): StopSegment? {
        if (points.size < 2) return null
        var bestDuration = 0L
        var bestStart = points[0].timestamp
        var stopStart: Long? = null

        for (point in points) {
            if (point.speedMps < STOP_SPEED_MPS) {
                if (stopStart == null) stopStart = point.timestamp
            } else if (stopStart != null) {
                val duration = ((point.timestamp - stopStart) / 1000L).coerceAtLeast(0L)
                if (duration > bestDuration) {
                    bestDuration = duration
                    bestStart = stopStart
                }
                stopStart = null
            }
        }

        val last = points.last()
        if (stopStart != null && last.speedMps < STOP_SPEED_MPS) {
            val duration = ((last.timestamp - stopStart) / 1000L).coerceAtLeast(0L)
            if (duration > bestDuration) {
                bestDuration = duration
                bestStart = stopStart
            }
        }

        return if (bestDuration > 0) StopSegment(bestDuration, bestStart) else null
    }

    private fun distanceAlongRoute(
        to: RoutePointEntity,
        points: List<RoutePointEntity>
    ): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            total += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (b.id == to.id || kotlin.math.abs(b.timestamp - to.timestamp) < 10L) break
        }
        return total
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(min(1.0, sqrt(a)))
    }

    private fun elapsedLabel(seconds: Long): String = formatDuration(seconds)

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

    private fun formatDate(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        return SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault()).format(Date(timeMs))
    }
}
