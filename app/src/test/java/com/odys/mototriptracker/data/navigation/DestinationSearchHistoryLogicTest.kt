package com.odys.mototriptracker.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSearchHistoryLogicTest {

    @Test
    fun prepend_putsNewestFirstAndCaps() {
        val existing = (1..20).map { i ->
            DestinationHistoryEntry(
                id = "id-$i",
                name = "Place $i",
                subtitle = "",
                latitude = 37.0 + i * 0.01,
                longitude = 23.0,
            )
        }
        val next = DestinationSearchHistoryLogic.prepend(
            existing = existing,
            name = "New",
            subtitle = "Athens",
            latitude = 38.0,
            longitude = 24.0,
            id = "new",
            timestampMs = 1L,
        )
        assertEquals(20, next.size)
        assertEquals("new", next.first().id)
        assertEquals("New", next.first().name)
        assertTrue(next.none { it.id == "id-20" })
    }

    @Test
    fun prepend_dedupesNearbyCoordinates() {
        val existing = listOf(
            DestinationHistoryEntry(
                id = "old",
                name = "Old name",
                subtitle = "",
                latitude = 37.98000,
                longitude = 23.72000,
            ),
        )
        val next = DestinationSearchHistoryLogic.prepend(
            existing = existing,
            name = "Updated",
            subtitle = "",
            latitude = 37.98001,
            longitude = 23.72001,
            id = "new",
        )
        assertEquals(1, next.size)
        assertEquals("new", next.single().id)
        assertEquals("Updated", next.single().name)
    }
}
