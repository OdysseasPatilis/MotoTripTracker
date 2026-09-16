package com.odys.mototriptracker.ui.history

import com.odys.mototriptracker.data.trip.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RideHistoryFilterLogicTest {

    private fun trip(
        id: Long,
        startTime: Long,
        favorite: Boolean = false,
        title: String = "Ride $id",
    ) = TripEntity(
        id = id,
        startTime = startTime,
        endTime = startTime + 3_600_000,
        distanceMeters = 12_500f,
        avgSpeed = 40f,
        maxSpeed = 80f,
        isFavorite = favorite,
        title = title,
    )

    @Test
    fun filter_favoritesTabOnly() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val rides = listOf(
            trip(1, now.timeInMillis, favorite = true),
            trip(2, now.timeInMillis, favorite = false),
        )
        val visible = RideHistoryFilterLogic.filterRides(
            rides = rides,
            tab = RideHistoryTab.FAVORITES,
            query = "",
            filters = RideHistoryFilters(),
            now = now,
        )
        assertEquals(listOf(1L), visible.map { it.id })
    }

    @Test
    fun filter_todayPreset() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 16, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val today = now.timeInMillis
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        val rides = listOf(trip(1, today), trip(2, yesterday))
        val visible = RideHistoryFilterLogic.filterRides(
            rides = rides,
            tab = RideHistoryTab.ALL,
            query = "",
            filters = RideHistoryFilters(datePreset = DateFilterPreset.TODAY),
            now = now,
        )
        assertEquals(listOf(1L), visible.map { it.id })
    }

    @Test
    fun filter_queryMatchesTitle() {
        val now = Calendar.getInstance()
        val rides = listOf(
            trip(1, now.timeInMillis, title = "Coastal loop"),
            trip(2, now.timeInMillis, title = "City hop"),
        )
        val visible = RideHistoryFilterLogic.filterRides(
            rides = rides,
            tab = RideHistoryTab.ALL,
            query = "coast",
            filters = RideHistoryFilters(),
            now = now,
        )
        assertEquals(1, visible.size)
        assertTrue(visible.single().title!!.contains("Coastal", ignoreCase = true))
    }
}
