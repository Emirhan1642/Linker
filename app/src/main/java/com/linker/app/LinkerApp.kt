package com.linker.app

import android.app.Application
import com.linker.app.BuildConfig
import com.linker.app.core.security.SecurityManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Linker Application Class
 *
 * Entry point for the application with Hilt dependency injection.
 * Initializes core services and configurations.
 */
@HiltAndroidApp
class LinkerApp : Application() {

    @Inject lateinit var securityManager: SecurityManager

    override fun onCreate() {
        super.onCreate()

        // SECURITY: Initialize API keys in encrypted storage on first launch
        if (!securityManager.areKeysInitialized()) {
            securityManager.initializeKeys(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                cloudinaryCloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                cloudinaryApiKey = BuildConfig.CLOUDINARY_API_KEY,
                cloudinaryApiSecret = BuildConfig.CLOUDINARY_API_SECRET
            )
        }

        // TODO: Initialize crash reporting (e.g., Firebase Crashlytics)
        // TODO: Initialize analytics
        // TODO: Set up WorkManager for background tasks
    }
}
