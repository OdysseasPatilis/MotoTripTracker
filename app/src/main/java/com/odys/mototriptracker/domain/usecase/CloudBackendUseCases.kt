package com.odys.mototriptracker.domain.usecase

import com.odys.mototriptracker.domain.CloudBackendSettings
import com.odys.mototriptracker.domain.CloudRiderProfile
import com.odys.mototriptracker.util.AppLogger
import javax.inject.Inject

data class CloudBackendSnapshot(
    val baseUrl: String,
    val displayName: String,
    val isEnabled: Boolean,
)

class ObserveCloudBackendUseCase @Inject constructor(
    private val settings: CloudBackendSettings,
    private val profile: CloudRiderProfile,
) {
    operator fun invoke(): CloudBackendSnapshot = CloudBackendSnapshot(
        baseUrl = settings.baseUrl,
        displayName = profile.displayName,
        isEnabled = settings.isEnabled,
    )
}

class SaveCloudBackendSettingsUseCase @Inject constructor(
    private val settings: CloudBackendSettings,
    private val profile: CloudRiderProfile,
) {
    suspend operator fun invoke(url: String, displayName: String): CloudBackendSnapshot {
        settings.setBaseUrl(url)
        profile.setDisplayNameLocal(displayName)
        val baseUrl = settings.baseUrl
        if (baseUrl.isNotBlank()) {
            runCatching { profile.syncDisplayName(baseUrl) }
                .onFailure {
                    AppLogger.e(AppLogger.Category.APP, "Profile sync on save failed", it)
                }
        }
        return CloudBackendSnapshot(
            baseUrl = settings.baseUrl,
            displayName = profile.displayName,
            isEnabled = settings.isEnabled,
        )
    }
}
