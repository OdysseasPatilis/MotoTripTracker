package com.odys.mototriptracker.ui.history

import com.odys.mototriptracker.data.export.displayTitle
import com.odys.mototriptracker.data.trip.TripEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Pure ride-history filtering (tab / date / search). */
object RideHistoryFilterLogic {

    fun filterRides(
        rides: List<TripEntity>,
        tab: RideHistoryTab,
        query: String,
        filters: RideHistoryFilters,
        now: Calendar = Calendar.getInstance(),
    ): List<TripEntity> {
        val scoped = when (tab) {
            RideHistoryTab.ALL -> rides
            RideHistoryTab.FAVORITES -> rides.filter { it.isFavorite }
        }
        val dateScoped = scoped.filter { matchesDateFilter(it, filters, now) }
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isEmpty()) return dateScoped
        return dateScoped.filter { matchesQuery(it, normalized) }
    }

    fun matchesDateFilter(
        ride: TripEntity,
        filters: RideHistoryFilters,
        now: Calendar = Calendar.getInstance(),
    ): Boolean {
        val range = dateRangeFor(filters, now) ?: return true
        val time = ride.startTime
        return time in range.first..range.second
    }

    fun matchesQuery(ride: TripEntity, query: String): Boolean {
        val title = ride.displayTitle().lowercase(Locale.getDefault())
        val start = formatSearchDate(ride.startTime)
        val end = formatSearchDate(ride.endTime)
        val distance = String.format(Locale.getDefault(), "%.1f", ride.distanceMeters / 1000f)
        val avg = ride.avgSpeed.toInt().toString()
        val max = ride.maxSpeed.toInt().toString()
        return title.contains(query) ||
            start.contains(query) ||
            end.contains(query) ||
            distance.contains(query) ||
            avg.contains(query) ||
            max.contains(query) ||
            (query.contains("favor") && ride.isFavorite)
    }

    /** Inclusive [startMs, endMs], or null when no date filter. */
    fun dateRangeFor(
        filters: RideHistoryFilters,
        now: Calendar = Calendar.getInstance(),
    ): Pair<Long, Long>? {
        return when (filters.datePreset) {
            DateFilterPreset.ANY -> null
            DateFilterPreset.TODAY -> dayRange(now.clone() as Calendar)
            DateFilterPreset.YESTERDAY -> {
                val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                dayRange(yesterday)
            }
            DateFilterPreset.THIS_WEEK -> {
                val start = (now.clone() as Calendar).apply {
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                    setToStartOfDay()
                }
                val end = (now.clone() as Calendar).apply { setToEndOfDay() }
                start.timeInMillis to end.timeInMillis
            }
            DateFilterPreset.THIS_MONTH -> {
                val start = (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    setToStartOfDay()
                }
                val end = (now.clone() as Calendar).apply { setToEndOfDay() }
                start.timeInMillis to end.timeInMillis
            }
            DateFilterPreset.CUSTOM -> {
                val from = filters.customFromMs ?: return null
                val to = filters.customToMs ?: from
                minOf(from, to) to maxOf(from, to)
            }
        }
    }

    private fun formatSearchDate(timeMs: Long): String {
        if (timeMs <= 0L) return ""
        val formats = listOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MMM dd",
            "MMMM",
            "yyyy",
            "EEE",
        )
        return formats.joinToString(" ") { pattern ->
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timeMs)).lowercase(Locale.getDefault())
        }
    }

    private fun dayRange(day: Calendar): Pair<Long, Long> {
        val start = (day.clone() as Calendar).apply { setToStartOfDay() }.timeInMillis
        val end = (day.clone() as Calendar).apply { setToEndOfDay() }.timeInMillis
        return start to end
    }

    private fun Calendar.setToStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }
}
