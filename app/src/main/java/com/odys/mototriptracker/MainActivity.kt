package com.odys.mototriptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.odys.mototriptracker.ui.navigation.MotoTripNavHost
import com.odys.mototriptracker.ui.theme.MotoTripTrackerTheme
import com.odys.mototriptracker.ui.theme.ThemeStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoTripTrackerTheme(themeStore = themeStore) {
                MotoTripNavHost()
            }
        }
    }
}
