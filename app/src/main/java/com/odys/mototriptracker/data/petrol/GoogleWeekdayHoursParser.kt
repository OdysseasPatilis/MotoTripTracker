package com.odys.mototriptracker.data.petrol

import java.util.Calendar
import java.util.Locale

/**
 * Parses Google Places weekday lines such as:
 * - "Monday: 6:00 AM – 10:00 PM"
 * - "Monday: 6:00–22:00"
 * - "Monday: Closed"
 * - "Monday: Open 24 hours"
 */
object GoogleWeekdayHoursParser {
    fun statusNow(
        weekdayHours: List<String>,
        calendar: Calendar = Calendar.getInstance()
    ): OpeningHoursEvaluator.Status {
        if (weekdayHours.isEmpty()) return OpeningHoursEvaluator.Status.UNKNOWN
        val todayNames = todayNames(calendar)
        val line = weekdayHours.firstOrNull { entry ->
            val prefix = entry.substringBefore(':', missingDelimiterValue = "").trim().lowercase(Locale.US)
            todayNames.any { prefix.startsWith(it) }
        } ?: return OpeningHoursEvaluator.Status.UNKNOWN

        val value = line.substringAfter(':', missingDelimiterValue = "").trim().lowercase(Locale.US)
        if (value.isEmpty()) return OpeningHoursEvaluator.Status.UNKNOWN
        if (value.contains("closed")) return OpeningHoursEvaluator.Status.CLOSED
        if (value.contains("24 hour") || value.contains("24-hour") || value == "open") {
            return OpeningHoursEvaluator.Status.OPEN
        }

        val ranges = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (ranges.isEmpty()) return OpeningHoursEvaluator.Status.UNKNOWN

        val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        var sawParsable = false
        for (range in ranges) {
            val parts = range.split(Regex("[–—-]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != 2) continue
            val open = parseClock(parts[0]) ?: continue
            val close = parseClock(parts[1]) ?: continue
            sawParsable = true
            if (isInRange(nowMinutes, open, close)) return OpeningHoursEvaluator.Status.OPEN
        }
        return if (sawParsable) OpeningHoursEvaluator.Status.CLOSED else OpeningHoursEvaluator.Status.UNKNOWN
    }

    private fun todayNames(calendar: Calendar): List<String> {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        return when (day) {
            Calendar.SUNDAY -> listOf("sunday", "sun", "κυριακή", "κυρ")
            Calendar.MONDAY -> listOf("monday", "mon", "δευτέρα", "δευ")
            Calendar.TUESDAY -> listOf("tuesday", "tue", "τρίτη", "τρι")
            Calendar.WEDNESDAY -> listOf("wednesday", "wed", "τετάρτη", "τετ")
            Calendar.THURSDAY -> listOf("thursday", "thu", "πέμπτη", "πεμ")
            Calendar.FRIDAY -> listOf("friday", "fri", "παρασκευή", "παρ")
            Calendar.SATURDAY -> listOf("saturday", "sat", "σάββατο", "σαβ")
            else -> emptyList()
        }
    }

    private fun parseClock(raw: String): Int? {
        val cleaned = raw.lowercase(Locale.US)
            .replace('\u202f', ' ')
            .replace('\u00a0', ' ')
            .trim()
        val am = cleaned.contains("am")
        val pm = cleaned.contains("pm")
        val numeric = cleaned
            .replace("am", "")
            .replace("pm", "")
            .replace(".", ":")
            .trim()
        val match = Regex("""(\d{1,2})(?::(\d{2}))?""").find(numeric) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        if (hour !in 0..24 || minute !in 0..59) return null
        if (am || pm) {
            if (hour == 12) hour = 0
            if (pm) hour += 12
        }
        return hour * 60 + minute
    }

    private fun isInRange(now: Int, open: Int, close: Int): Boolean =
        if (close > open) now in open until close else now >= open || now < close
}
