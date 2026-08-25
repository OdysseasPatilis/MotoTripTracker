package com.odys.mototriptracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.odys.mototriptracker.ui.navigation.MotoTripNavHost
import com.odys.mototriptracker.ui.theme.MotoTripTrackerTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoTripTrackerTheme {
                MotoTripNavHost()
            }
        }
    }
}
