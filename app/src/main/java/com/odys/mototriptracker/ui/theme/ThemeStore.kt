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

    private val _manualOverrideKmh = MutableStateFlow(readManualOverride())
    val manualOverrideKmh: StateFlow<Int?> = _manualOverrideKmh.asStateFlow()

    fun paletteFor(mode: ThemeMode = _mode.value): AppPalette {
        return if (mode == ThemeMode.DARK) AppPalettes.dark else AppPalettes.light
    }

    fun toggleTheme() {
        val next = _mode.value.toggled()
        _mode.value = next
        prefs.edit().putString(KEY_THEME_MODE, next.name).apply()
    }

    fun effectiveLimitKmh(autoLimitKmh: Int?): Int =
        _manualOverrideKmh.value ?: autoLimitKmh ?: DEFAULT_SPEED_LIMIT

    fun hasAutoLimit(autoLimitKmh: Int?): Boolean =
        autoLimitKmh != null && _manualOverrideKmh.value == null

    fun isUsingManualOverride(): Boolean = _manualOverrideKmh.value != null

    fun cycleSpeedLimit(autoLimitKmh: Int?) {
        val limits = SPEED_LIMITS
        val current = _manualOverrideKmh.value ?: autoLimitKmh ?: DEFAULT_SPEED_LIMIT
        val index = limits.indexOf(current)
        val next = if (index >= 0) {
            limits[(index + 1) % limits.size]
        } else {
            DEFAULT_SPEED_LIMIT
        }
        _manualOverrideKmh.value = next
        persistManualOverride()
    }

    fun clearManualOverride() {
        _manualOverrideKmh.value = null
        prefs.edit().remove(KEY_MANUAL_OVERRIDE).apply()
    }

    private fun readMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    private fun readManualOverride(): Int? {
        if (!prefs.contains(KEY_MANUAL_OVERRIDE)) return null
        val stored = prefs.getInt(KEY_MANUAL_OVERRIDE, DEFAULT_SPEED_LIMIT)
        return stored.takeIf { it in SPEED_LIMITS }
    }

    private fun persistManualOverride() {
        val value = _manualOverrideKmh.value
        if (value != null) {
            prefs.edit().putInt(KEY_MANUAL_OVERRIDE, value).apply()
        } else {
            prefs.edit().remove(KEY_MANUAL_OVERRIDE).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "moto_app_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_MANUAL_OVERRIDE = "moto_manual_speed_limit"
        private const val DEFAULT_SPEED_LIMIT = 50
        private val SPEED_LIMITS = listOf(30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)
    }
}
