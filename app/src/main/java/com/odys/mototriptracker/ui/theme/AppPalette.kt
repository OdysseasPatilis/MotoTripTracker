package com.odys.mototriptracker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    DARK,
    LIGHT;

    fun toggled(): ThemeMode = if (this == DARK) LIGHT else DARK
}

data class AppPalette(
    val bgDeep: Color,
    val bgCard: Color,
    val bgPanel: Color,
    val bgBar: Color,
    val bgSurface: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textSecondary: Color,
    val emptyText: Color,
    val divider: Color,
    val borderSubtle: Color,
    val neonGreen: Color,
    val neonBlue: Color,
    val neonRed: Color,
    val stopRed: Color,
    val arcTrack: Color,
    val gForceTick: Color,
    val routeAmber: Color,
    val routeTeal: Color,
    val routeCoral: Color,
    val purpleAccent: Color,
    val purpleAccentEnd: Color,
    val mint: Color,
    val deleteButtonBg: Color,
    val layerActive: Color,
    val mapCardBg: Color,
    val batteryOutline: Color,
    val batteryLabel: Color,
    val startButtonDisabledBg: Color,
    val startButtonDisabledText: Color,
    val pauseBorder: Color,
    val heroLabel: Color,
    val speedLimitRing: Color = Color(0xFFE30613)
) {
    val startGradient: Brush
        get() = Brush.horizontalGradient(listOf(neonGreen, neonBlue))

    val heroGradient: Brush
        get() = Brush.horizontalGradient(listOf(purpleAccent, purpleAccentEnd))
}

object AppPalettes {
    val dark = AppPalette(
        bgDeep = Color(0xFF0A0A0F),
        bgCard = Color(0xFF111120),
        bgPanel = Color(0xFF1A1A2E),
        bgBar = Color(0xFF0F0F1A),
        bgSurface = Color(0xFF4050E5),
        textPrimary = Color.White,
        textMuted = Color(0xFF4A4A6A),
        textSecondary = Color.White.copy(alpha = 0.5f),
        emptyText = Color(0xFF2A2A4A),
        divider = Color(0xFF1A1A30),
        borderSubtle = Color.White.copy(alpha = 0.12f),
        neonGreen = Color(0xFF00E5A0),
        neonBlue = Color(0xFF00B4FF),
        neonRed = Color(0xFFFF4A6A),
        stopRed = Color(0xFFE24B4A),
        arcTrack = Color(0xFF252547),
        gForceTick = Color(0xFF3A3A5A),
        routeAmber = Color(0xFFEF9F27),
        routeTeal = Color(0xFF1D9E75),
        routeCoral = Color(0xFFD85A30),
        purpleAccent = Color(0xFF5B5FEF),
        purpleAccentEnd = Color(0xFF7C4DFF),
        mint = Color(0xFF5EFFC8),
        deleteButtonBg = Color(0xFF3A1A1A),
        layerActive = Color(0xFF5B5FEF),
        mapCardBg = Color(0xFF1E1E24),
        batteryOutline = Color.White.copy(alpha = 0.33f),
        batteryLabel = Color.White.copy(alpha = 0.5f),
        startButtonDisabledBg = Color.Gray.copy(alpha = 0.3f),
        startButtonDisabledText = Color.Gray,
        pauseBorder = Color.White.copy(alpha = 0.12f),
        heroLabel = Color.White.copy(alpha = 0.67f)
    )

    val light = AppPalette(
        bgDeep = Color(0xFFF3F4F8),
        bgCard = Color.White,
        bgPanel = Color(0xFFE8EAF2),
        bgBar = Color.White,
        bgSurface = Color(0xFF4050E5),
        textPrimary = Color(0xFF12121A),
        textMuted = Color(0xFF6B6B82),
        textSecondary = Color(0xFF12121A).copy(alpha = 0.45f),
        emptyText = Color(0xFF9A9AB0),
        divider = Color(0xFFE2E4EE),
        borderSubtle = Color(0xFF12121A).copy(alpha = 0.08f),
        neonGreen = Color(0xFF00B87A),
        neonBlue = Color(0xFF0090D0),
        neonRed = Color(0xFFE03555),
        stopRed = Color(0xFFD63A3A),
        arcTrack = Color(0xFFD5D8E6),
        gForceTick = Color(0xFFB8BBCC),
        routeAmber = Color(0xFFD98900),
        routeTeal = Color(0xFF178A66),
        routeCoral = Color(0xFFC24E28),
        purpleAccent = Color(0xFF5B5FEF),
        purpleAccentEnd = Color(0xFF7C4DFF),
        mint = Color(0xFF00C9A0),
        deleteButtonBg = Color(0xFFFFE5E5),
        layerActive = Color(0xFF5B5FEF),
        mapCardBg = Color.White,
        batteryOutline = Color(0xFF12121A).copy(alpha = 0.35f),
        batteryLabel = Color(0xFF12121A).copy(alpha = 0.45f),
        startButtonDisabledBg = Color(0xFFD8DAE4),
        startButtonDisabledText = Color(0xFF8A8AA0),
        pauseBorder = Color(0xFF12121A).copy(alpha = 0.10f),
        heroLabel = Color.White.copy(alpha = 0.67f)
    )
}

val LocalAppPalette = staticCompositionLocalOf { AppPalettes.dark }
