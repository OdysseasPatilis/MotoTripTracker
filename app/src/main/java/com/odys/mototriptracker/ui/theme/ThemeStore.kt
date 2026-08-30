package com.odys.mototriptracker.ui.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeStore @Inject constructor(
    @param:ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    init {
        // Drop legacy manual override so auto road limits are never masked.
        if (prefs.contains(KEY_MANUAL_OVERRIDE)) {
            prefs.edit().remove(KEY_MANUAL_OVERRIDE).apply()
        }
    }

    fun paletteFor(mode: ThemeMode = _mode.value): AppPalette {
        return if (mode == ThemeMode.DARK) AppPalettes.dark else AppPalettes.light
    }

    fun toggleTheme() {
        val next = _mode.value.toggled()
        _mode.value = next
        prefs.edit().putString(KEY_THEME_MODE, next.name).apply()
    }

    fun effectiveLimitKmh(autoLimitKmh: Int?): Int =
        autoLimitKmh ?: DEFAULT_SPEED_LIMIT

    fun hasAutoLimit(autoLimitKmh: Int?): Boolean = autoLimitKmh != null

    private fun readMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MANUAL_OVERRIDE = "moto_manual_speed_limit"
        private const val DEFAULT_SPEED_LIMIT = 50
    }
}
