package com.linker.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Linker Application Class
 * 
 * Entry point for the application with Hilt dependency injection.
 * Initializes core services and configurations.
 */
@HiltAndroidApp
class LinkerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // TODO: Initialize crash reporting (e.g., Firebase Crashlytics)
        // TODO: Initialize analytics
        // TODO: Set up WorkManager for background tasks
    }
}
