package com.odys.mototriptracker.data.network

import com.odys.mototriptracker.di.AppHttpClient
import com.odys.mototriptracker.util.AppLogger
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared Overpass transport: FormBody POST + sticky mirror rotation on failure.
 * Callers own query strings and JSON parsing.
 */
@Singleton
class OverpassClient @Inject constructor(
    @param:AppHttpClient private val httpClient: OkHttpClient,
) {
    @Volatile
    private var preferredEndpointIndex = 0

    /**
     * Posts [query] to Overpass mirrors until one returns a non-blank body.
     * On success, that mirror becomes preferred for the next call.
     */
    fun post(query: String, userAgent: String = DEFAULT_USER_AGENT): String? {
        val endpoints = rotatedEndpoints()
        for ((indexInRotation, endpoint) in endpoints.withIndex()) {
            val body = request(endpoint, query, userAgent) ?: continue
            preferredEndpointIndex =
                (preferredEndpointIndex + indexInRotation) % OverpassEndpoints.ALL.size
            return body
        }
        AppLogger.w(AppLogger.Category.NETWORK, "All Overpass mirrors failed")
        return null
    }

    private fun rotatedEndpoints(): List<String> {
        val list = OverpassEndpoints.ALL.toMutableList()
        if (preferredEndpointIndex in list.indices) {
            val preferred = list.removeAt(preferredEndpointIndex)
            list.add(0, preferred)
        }
        return list
    }

    private fun request(endpoint: String, query: String, userAgent: String): String? {
        val form = FormBody.Builder().add("data", query).build()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(form)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build()
        return try {
            httpClient.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w(
                        AppLogger.Category.NETWORK,
                        "Overpass HTTP ${response.code} from ${hostOf(endpoint)}",
                    )
                    return null
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            AppLogger.w(
                AppLogger.Category.NETWORK,
                "Overpass ${hostOf(endpoint)} failed: ${e.message}",
            )
            null
        }
    }

    private fun hostOf(endpoint: String): String =
        endpoint.toHttpUrlOrNull()?.host ?: endpoint

    companion object {
        const val DEFAULT_USER_AGENT =
            "MotoTripTracker/1.0 (Android; motorcycle trip tracker)"
    }
}
