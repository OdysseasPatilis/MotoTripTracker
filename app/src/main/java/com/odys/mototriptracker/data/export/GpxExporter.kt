package com.odys.mototriptracker.data.export

import com.odys.mototriptracker.data.checkpoint.RoutePointEntity
import com.odys.mototriptracker.data.trip.TripEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object GpxExporter {

    fun build(trip: TripEntity, points: List<RoutePointEntity>): String {
        val sorted = points.sortedBy { it.timestamp }
        val name = trip.displayTitle()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<gpx version="1.1" creator="MotoTripTracker" xmlns="http://www.topografix.com/GPX/1/1">""")
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(name)}</name>")
            if (trip.startTime > 0L) {
                appendLine("    <time>${timeFormat.format(trip.startTime)}</time>")
            }
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(name)}</name>")
            appendLine("    <trkseg>")
            sorted.forEach { point ->
                append("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
                appendLine()
                appendLine("        <ele>${point.altitude}</ele>")
                if (point.timestamp > 0L) {
                    appendLine("        <time>${timeFormat.format(point.timestamp)}</time>")
                }
                appendLine("      </trkpt>")
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}

fun TripEntity.displayTitle(): String =
    title?.takeIf { it.isNotBlank() }
        ?: run {
            val formatter = SimpleDateFormat("MMM dd, yyyy - HH:mm", Locale.getDefault())
            "Ride ${formatter.format(startTime)}"
        }
