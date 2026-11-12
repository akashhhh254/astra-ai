package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.core.appcheck.configureAppCheck
import com.example.data.local.AstraPreferences
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AstraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Theme & User Preferences
        AstraPreferences.init(this)

        // Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        // Enable Edge to Edge
        enableEdgeToEdge()

        // Render Application
        setContent {
            val themeMode by AstraPreferences.themeMode.collectAsState()

            AstraTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
