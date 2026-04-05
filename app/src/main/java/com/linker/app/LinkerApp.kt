package com.linker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.linker.app.BuildConfig
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.work.MessageQueueWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Linker Application Class
 *
 * Entry point for the application with Hilt dependency injection.
 * Initializes core services and configurations.
 */
@HiltAndroidApp
class LinkerApp : Application(), Configuration.Provider {

    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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

        scheduleMessageQueueSync()

        // TODO: Initialize crash reporting (e.g., Firebase Crashlytics)
        // TODO: Initialize analytics
    }

    private fun scheduleMessageQueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MessageQueueWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "linker_message_queue_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
