package com.odys.mototriptracker.data.backend

import android.content.Context
import com.odys.mototriptracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime backend URL, matching iOS BackendSettings.
 * SharedPreferences override wins; otherwise falls back to `BACKEND_BASE_URL` from local.properties.
 */
@Singleton
class BackendSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val baseUrl: String
        get() {
            val fromPrefs = prefs.getString(KEY_BASE_URL, null)
            if (!fromPrefs.isNullOrBlank()) return normalize(fromPrefs)
            return normalize(BuildConfig.BACKEND_BASE_URL)
        }

    val isEnabled: Boolean
        get() = baseUrl.isNotBlank()

    fun setBaseUrl(raw: String) {
        prefs.edit().putString(KEY_BASE_URL, normalize(raw)).apply()
    }

    fun clearBaseUrl() {
        prefs.edit().remove(KEY_BASE_URL).apply()
    }

    companion object {
        private const val PREFS_NAME = "mototrip_backend"
        private const val KEY_BASE_URL = "backend_base_url"

        /** Ensures a scheme is present — bare `192.168.x.x:8080` is not a valid HTTP URL. */
        fun normalize(raw: String): String {
            var value = raw.trim()
            while (value.endsWith('/')) {
                value = value.dropLast(1)
            }
            if (value.isEmpty()) return ""
            return if (value.contains("://")) value else "http://$value"
        }
    }
}
