package com.timflow.hydroapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.timflow.hydroapp.ui.MapScreen
import com.timflow.hydroapp.ui.theme.HydroAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Application entry point.
 *
 * Hosts the single-activity Compose navigation graph.  Location permission
 * requests are handled inside [MapScreen] / [MapViewModel] so the activity
 * itself stays lean.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HydroAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MapScreen()
                }
            }
        }
    }
}
