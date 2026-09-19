package com.odys.mototriptracker.domain

/** Cloud sync settings visible to the summary / profile UI. */
interface CloudBackendSettings {
    val baseUrl: String
    val isEnabled: Boolean
    fun setBaseUrl(raw: String)
}

/** Local rider display name for cloud uploads. */
interface CloudRiderProfile {
    val displayName: String
    fun setDisplayNameLocal(name: String)
    suspend fun syncDisplayName(baseUrl: String)
}
