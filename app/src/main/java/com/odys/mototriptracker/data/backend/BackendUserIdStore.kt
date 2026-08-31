package com.odys.mototriptracker.data.backend

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendUserIdStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreate(): String =
        prefs.getString(KEY_USER_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                prefs.edit().putString(KEY_USER_ID, id).apply()
            }

    companion object {
        private const val PREFS_NAME = "mototrip_backend"
        private const val KEY_USER_ID = "user_id"
    }
}
