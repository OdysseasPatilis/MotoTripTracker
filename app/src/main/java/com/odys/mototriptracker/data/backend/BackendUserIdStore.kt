package com.odys.mototriptracker.data.backend

import android.content.Context
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anonymous rider identity for cloud sync.
 * Prefers a server-issued profile id from `POST /v1/profiles`; local-only UUIDs
 * from older builds are replaced on the next successful registration.
 */
@Singleton
class BackendUserIdStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val cachedProfileId: String?
        get() = if (prefs.getBoolean(KEY_SERVER_SYNCED, false)) {
            prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

    val displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_DISPLAY_NAME

    fun setDisplayNameLocal(name: String) {
        val trimmed = name.trim().ifEmpty { DEFAULT_DISPLAY_NAME }
        prefs.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
    }

    /**
     * Returns a server profile id, creating one via REST when needed.
     * Requires a non-blank [baseUrl].
     */
    suspend fun ensureServerProfile(baseUrl: String): String = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Backend URL not configured" }
        cachedProfileId?.let { return@withContext it }

        val name = displayName
        val body = JSONObject()
            .put("displayName", name)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl/v1/profiles")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Profile create failed HTTP ${response.code}: $responseBody")
            }
            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val serverName = json.optString("displayName", name).ifBlank { name }
            prefs.edit()
                .putString(KEY_USER_ID, id)
                .putString(KEY_DISPLAY_NAME, serverName)
                .putBoolean(KEY_SERVER_SYNCED, true)
                .apply()
            AppLogger.i(AppLogger.Category.APP, "Server profile registered id=$id name=$serverName")
            id
        }
    }

    /** Creates or patches the server profile so [name] is the display name. */
    suspend fun updateDisplayName(baseUrl: String, name: String): String = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) { "Backend URL not configured" }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "displayName must not be blank" }
        setDisplayNameLocal(trimmed)

        val existingId = cachedProfileId
        if (existingId == null) {
            ensureServerProfile(baseUrl)
            return@withContext displayName
        }

        val body = JSONObject()
            .put("displayName", trimmed)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$baseUrl/v1/profiles/$existingId")
            .patch(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Profile patch failed HTTP ${response.code}: $responseBody")
            }
            val json = JSONObject(responseBody)
            val serverName = json.optString("displayName", trimmed).ifBlank { trimmed }
            prefs.edit().putString(KEY_DISPLAY_NAME, serverName).apply()
            serverName
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val PREFS_NAME = "mototrip_backend"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SERVER_SYNCED = "profile_server_synced"
        private const val DEFAULT_DISPLAY_NAME = "Rider"
    }
}
