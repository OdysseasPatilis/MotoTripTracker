package com.odys.mototriptracker.data.backend

import com.odys.mototriptracker.BuildConfig

object BackendConfig {
    /** Empty = cloud upload disabled. Set via `BACKEND_BASE_URL` in local.properties. */
    val baseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    val isEnabled: Boolean
        get() = baseUrl.isNotBlank()
}
