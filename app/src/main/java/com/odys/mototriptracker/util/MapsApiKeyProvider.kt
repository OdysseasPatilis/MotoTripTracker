package com.odys.mototriptracker.util

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsApiKeyProvider @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getApiKey(): String? {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        }.getOrNull()
    }
}
