package com.linker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.linker.app.presentation.navigation.LinkerNavHost
import com.linker.app.presentation.theme.LinkerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity - Single Activity Architecture
 * 
 * Hosts the entire app using Jetpack Compose and Navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            LinkerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LinkerNavHost()
                }
            }
        }
    }
}
