package com.odys.mototriptracker.data.navigation

/**
 * A map POI the user tapped — shown in the Go card before route preview.
 * Mirrors iOS `PickedMapPlace`.
 */
data class PickedMapPlace(
    val name: String,
    val category: String? = null,
    val address: String = "",
    val phone: String? = null,
    val websiteHost: String? = null,
    val websiteUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val placeId: String? = null,
    val isResolving: Boolean = false,
) {
    val id: String get() = "${placeId ?: "$latitude,$longitude"},$name"
}
