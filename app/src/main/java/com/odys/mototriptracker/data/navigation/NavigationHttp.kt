package com.odys.mototriptracker.data.navigation

import com.odys.mototriptracker.di.AppHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Shared GET helper for navigation HTTP (Directions, Places REST, Photon, Nominatim). */
@Singleton
class NavigationHttp @Inject constructor(
    @param:AppHttpClient private val httpClient: OkHttpClient,
) {
    fun get(url: String, userAgent: String? = null): String? {
        val request = Request.Builder().url(url).get().apply {
            if (userAgent != null) header("User-Agent", userAgent)
        }.build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }
}

suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitPlacesTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result -> cont.resume(result) }
        addOnFailureListener { error -> cont.resumeWith(Result.failure(error)) }
        addOnCanceledListener { cont.cancel() }
    }
