package com.odys.mototriptracker.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val DarkMaterialScheme = darkColorScheme(
    primary = AppPalettes.dark.neonGreen,
    secondary = AppPalettes.dark.neonBlue,
    background = AppPalettes.dark.bgDeep,
    surface = AppPalettes.dark.bgPanel
)

private val LightMaterialScheme = lightColorScheme(
    primary = AppPalettes.light.neonGreen,
    secondary = AppPalettes.light.neonBlue,
    background = AppPalettes.light.bgDeep,
    surface = AppPalettes.light.bgPanel
)

@Composable
fun MotoTripTrackerTheme(
    themeStore: ThemeStore,
    content: @Composable () -> Unit
) {
    val mode by themeStore.mode.collectAsStateWithLifecycle()
    val palette = themeStore.paletteFor(mode)
    val darkTheme = mode == ThemeMode.DARK

    val view = LocalView.current
    val activity = LocalActivity.current
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalThemeStore provides themeStore
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MotoTripTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) AppPalettes.dark else AppPalettes.light
    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme,
            typography = Typography,
            content = content
        )
    }
}
