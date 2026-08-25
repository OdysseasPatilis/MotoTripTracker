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

    private val _speedLimitKmh = MutableStateFlow(prefs.getInt(KEY_SPEED_LIMIT, DEFAULT_SPEED_LIMIT))
    val speedLimitKmh: StateFlow<Int> = _speedLimitKmh.asStateFlow()

    fun paletteFor(mode: ThemeMode = _mode.value): AppPalette {
        return if (mode == ThemeMode.DARK) AppPalettes.dark else AppPalettes.light
    }

    fun toggleTheme() {
        val next = _mode.value.toggled()
        _mode.value = next
        prefs.edit().putString(KEY_THEME_MODE, next.name).apply()
    }

    fun cycleSpeedLimit() {
        val limits = SPEED_LIMITS
        val current = _speedLimitKmh.value
        val index = limits.indexOf(current)
        val next = if (index >= 0) {
            limits[(index + 1) % limits.size]
        } else {
            DEFAULT_SPEED_LIMIT
        }
        _speedLimitKmh.value = next
        prefs.edit().putInt(KEY_SPEED_LIMIT, next).apply()
    }

    private fun readMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SPEED_LIMIT = "speed_limit_kmh"
        private const val DEFAULT_SPEED_LIMIT = 50
        private val SPEED_LIMITS = listOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)
    }
}
