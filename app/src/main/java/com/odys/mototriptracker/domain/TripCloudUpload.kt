package com.odys.mototriptracker.domain

/** Uploads a locally persisted trip to the optional cloud backend. */
interface TripCloudUpload {
    fun enqueueUpload(localTripId: Long)
    suspend fun uploadNow(localTripId: Long)
}
