package com.odys.mototriptracker.data.navigation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class DestinationHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Recent destinations for the search sheet — mirrors iOS DestinationSearchHistory.
 * Cap 20, newest first; nearby coordinates are treated as the same place.
 */
@Singleton
class DestinationSearchHistory @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<DestinationHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        DestinationHistoryEntry(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            subtitle = obj.optString("subtitle", ""),
                            latitude = obj.getDouble("latitude"),
                            longitude = obj.getDouble("longitude"),
                            timestampMs = obj.optLong("timestampMs", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(name: String, subtitle: String, latitude: Double, longitude: Double) {
        val items = all().toMutableList()
        items.removeAll {
            abs(it.latitude - latitude) < DEDUPE_DEGREES &&
                abs(it.longitude - longitude) < DEDUPE_DEGREES
        }
        items.add(
            0,
            DestinationHistoryEntry(
                name = name,
                subtitle = subtitle,
                latitude = latitude,
                longitude = longitude,
            )
        )
        save(items.take(MAX_ENTRIES))
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    private fun save(items: List<DestinationHistoryEntry>) {
        val array = JSONArray()
        items.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("subtitle", entry.subtitle)
                    .put("latitude", entry.latitude)
                    .put("longitude", entry.longitude)
                    .put("timestampMs", entry.timestampMs)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    companion object {
        const val MAX_ENTRIES = 20
        private const val PREFS_NAME = "mototrip_nav"
        private const val KEY_HISTORY = "destination_history"
        /** ~25 m — treat as the same place for dedupe. */
        private const val DEDUPE_DEGREES = 0.00025
    }
}
