package com.odys.mototriptracker.data.petrol

import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Evaluates common OSM `opening_hours` values for open / closed / unknown. */
object OpeningHoursEvaluator {
    enum class Status { OPEN, CLOSED, UNKNOWN }

    fun status(raw: String?, date: Date = Date(), calendar: Calendar = Calendar.getInstance()): Status {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Status.UNKNOWN
        text = text.replace('–', '-')

        val lowered = text.lowercase(Locale.US)
        if (lowered == "24/7" || lowered == "open") return Status.OPEN
        if (lowered == "closed" || lowered == "off") return Status.CLOSED

        val rules = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        var sawParsable = false
        var matchedOpen = false
        var matchedClosed = false

        for (rule in rules) {
            val ruleLower = rule.lowercase(Locale.US)
            if (ruleLower == "24/7") {
                sawParsable = true
                matchedOpen = true
                continue
            }
            if (ruleLower.endsWith(" off") || ruleLower == "off" || ruleLower == "closed") {
                parseDays(rule)?.let { days ->
                    sawParsable = true
                    if (daysContains(days, date, calendar)) {
                        matchedClosed = true
                        matchedOpen = false
                    }
                }
                continue
            }

            val parsed = parseDayTimeRule(rule) ?: continue
            sawParsable = true
            if (daysContains(parsed.days, date, calendar) &&
                timeInRange(parsed.openMinutes, parsed.closeMinutes, date, calendar)
            ) {
                matchedOpen = true
                matchedClosed = false
            }
        }

        if (!sawParsable) return Status.UNKNOWN
        if (matchedOpen) return Status.OPEN
        if (matchedClosed) return Status.CLOSED
        return Status.CLOSED
    }

    private data class DayTimeRule(val days: Set<Int>, val openMinutes: Int, val closeMinutes: Int)

    /** Calendar weekday: Sunday=1 … Saturday=7 (matches Java Calendar). */
    private val dayMap = mapOf(
        "su" to Calendar.SUNDAY,
        "mo" to Calendar.MONDAY,
        "tu" to Calendar.TUESDAY,
        "we" to Calendar.WEDNESDAY,
        "th" to Calendar.THURSDAY,
        "fr" to Calendar.FRIDAY,
        "sa" to Calendar.SATURDAY
    )

    private fun parseDayTimeRule(rule: String): DayTimeRule? {
        val parts = rule.trim().split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) return null
        val days = parseDays(parts[0]) ?: return null
        val times = parseTimeRange(parts[1]) ?: return null
        return DayTimeRule(days, times.first, times.second)
    }

    private fun parseDays(fragment: String): Set<Int>? {
        val cleaned = fragment.lowercase(Locale.US)
            .replace(" ", "")
            .replace("off", "")
            .trim('-')
        if (cleaned.isEmpty()) return null

        if (cleaned.contains('-')) {
            val ends = cleaned.split('-')
            if (ends.size != 2) return null
            val start = dayMap[ends[0].take(2)] ?: return null
            val end = dayMap[ends[1].take(2)] ?: return null
            return weekdayRange(start, end)
        }

        if (cleaned.contains(',')) {
            val set = mutableSetOf<Int>()
            for (part in cleaned.split(',')) {
                set += dayMap[part.take(2)] ?: return null
            }
            return set
        }

        return setOf(dayMap[cleaned.take(2)] ?: return null)
    }

    private fun weekdayRange(start: Int, end: Int): Set<Int> {
        val set = mutableSetOf<Int>()
        var day = start
        repeat(7) {
            set += day
            if (day == end) return set
            day = if (day == Calendar.SATURDAY) Calendar.SUNDAY else day + 1
        }
        return set
    }

    private fun parseTimeRange(text: String): Pair<Int, Int>? {
        val parts = text.trim().split('-')
        if (parts.size != 2) return null
        val open = parseHhMm(parts[0]) ?: return null
        val close = parseHhMm(parts[1]) ?: return null
        return open to close
    }

    private fun parseHhMm(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..48 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun daysContains(days: Set<Int>, date: Date, calendar: Calendar): Boolean {
        calendar.time = date
        return days.contains(calendar.get(Calendar.DAY_OF_WEEK))
    }

    private fun timeInRange(open: Int, close: Int, date: Date, calendar: Calendar): Boolean {
        calendar.time = date
        val now = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return if (close > open) now in open until close else now >= open || now < close
    }
}
