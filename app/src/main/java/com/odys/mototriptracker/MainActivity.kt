package com.odys.mototriptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.odys.mototriptracker.ui.navigation.MotoTripNavHost
import com.odys.mototriptracker.ui.splash.AnimatedSplashScreen
import com.odys.mototriptracker.ui.theme.MotoTripTrackerTheme
import com.odys.mototriptracker.ui.theme.ThemeStore
import com.odys.mototriptracker.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val keepSystemSplash = AtomicBoolean(true)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSystemSplash.get() }

        super.onCreate(savedInstanceState)
        AppLogger.i(AppLogger.Category.APP, "MainActivity onCreate")
        enableEdgeToEdge()

        setContent {
            var showAnimatedSplash by remember { mutableStateOf(true) }

            SideEffect {
                keepSystemSplash.set(false)
            }

            MotoTripTrackerTheme(themeStore = themeStore) {
                if (showAnimatedSplash) {
                    AnimatedSplashScreen(
                        onFinished = { showAnimatedSplash = false }
                    )
                } else {
                    MotoTripNavHost()
                }
            }
        }
    }
}
