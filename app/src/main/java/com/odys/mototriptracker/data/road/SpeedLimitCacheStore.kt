package com.odys.mototriptracker.data.road

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists grid-keyed speed limits so rides can reuse nearby values offline.
 */
@Singleton
class SpeedLimitCacheStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_CACHE, null) ?: return mutableMapOf()
        return runCatching {
            val json = JSONObject(raw)
            val map = mutableMapOf<String, Int>()
            json.keys().forEach { key ->
                val value = json.optInt(key, -1)
                if (value in 5..200) map[key] = value
            }
            map
        }.getOrDefault(mutableMapOf())
    }

    fun save(cache: Map<String, Int?>) {
        val json = JSONObject()
        cache.entries
            .asSequence()
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .sortedByDescending { it.second }
            .take(MAX_ENTRIES)
            .forEach { (key, value) -> json.put(key, value) }
        prefs.edit { putString(KEY_CACHE, json.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "moto_speed_limit_cache"
        private const val KEY_CACHE = "grid_limits"
        private const val MAX_ENTRIES = 2_000
    }
}
