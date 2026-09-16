package com.odys.mototriptracker.data.navigation

import kotlin.math.abs

/** Pure recent-destination list rules (dedupe nearby + cap). */
object DestinationSearchHistoryLogic {
    const val MAX_ENTRIES = DestinationSearchHistory.MAX_ENTRIES
    /** ~25 m — treat as the same place for dedupe. */
    const val DEDUPE_DEGREES = 0.00025

    fun prepend(
        existing: List<DestinationHistoryEntry>,
        name: String,
        subtitle: String,
        latitude: Double,
        longitude: Double,
        id: String = java.util.UUID.randomUUID().toString(),
        timestampMs: Long = System.currentTimeMillis(),
    ): List<DestinationHistoryEntry> {
        val filtered = existing.filterNot {
            abs(it.latitude - latitude) < DEDUPE_DEGREES &&
                abs(it.longitude - longitude) < DEDUPE_DEGREES
        }
        val entry = DestinationHistoryEntry(
            id = id,
            name = name,
            subtitle = subtitle,
            latitude = latitude,
            longitude = longitude,
            timestampMs = timestampMs,
        )
        return (listOf(entry) + filtered).take(MAX_ENTRIES)
    }
}
