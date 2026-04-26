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
import com.linker.app.core.security.RootDetector
import com.linker.app.core.security.SecurityLogger
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.security.SecurityRiskLevel
import com.linker.app.core.work.MessageQueueWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Linker Application Class
 *
 * Entry point for the application with Hilt dependency injection.
 * Initializes core services and configurations.
 * 
 * DEPENDENCY INJECTION:
 * Uses Hilt field injection for Application class. This is the standard
 * pattern as Application is instantiated by the Android framework.
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

        // SECURITY: Check device security status on startup
        checkDeviceSecurity()

        // SECURITY: Initialize API keys in encrypted storage on first launch
        if (!securityManager.areKeysInitialized()) {
            securityManager.initializeKeys(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
                cloudinaryCloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
                cloudinaryApiKey = BuildConfig.CLOUDINARY_API_KEY,
                cloudinaryApiSecret = BuildConfig.CLOUDINARY_API_SECRET
            )
            SecurityLogger.logApiKeyInitialization()
        }

        scheduleMessageQueueSync()
        scheduleSessionCleanup()
        initializeMonitoring()
    }

    /**
     * Check device security status
     * 
     * SECURITY: Detect rooted devices and emulators to assess security risk.
     * This helps identify potentially compromised environments.
     * 
     * IMPLEMENTATION NOTES:
     * - Logs security risk level for monitoring
     * - For production, consider blocking HIGH/CRITICAL risk devices
     * - Can be extended to show warning dialogs to users
     */
    private fun checkDeviceSecurity() {
        val riskLevel = RootDetector.getSecurityRiskLevel()
        
        // Log security risk level
        SecurityLogger.logRootDetection(riskLevel)
        
        when (riskLevel) {
            SecurityRiskLevel.LOW -> {
                android.util.Log.d("LinkerApp", "Device security: LOW risk (normal device)")
            }
            SecurityRiskLevel.MEDIUM -> {
                android.util.Log.w("LinkerApp", "Device security: MEDIUM risk (emulator detected)")
                // For production: Consider showing warning or limiting features
            }
            SecurityRiskLevel.HIGH -> {
                android.util.Log.w("LinkerApp", "Device security: HIGH risk (rooted device detected)")
                // For production: Consider blocking app or showing warning
            }
            SecurityRiskLevel.CRITICAL -> {
                android.util.Log.e("LinkerApp", "Device security: CRITICAL risk (rooted emulator)")
                // For production: Strongly consider blocking app
            }
        }
    }

    /**
     * Initialize crash reporting and analytics
     * 
     * IMPLEMENTATION NOTES:
     * - Firebase Crashlytics is already included via firebase-bom
     * - Crashlytics auto-initializes, no code needed
     * - For custom analytics, add Firebase Analytics initialization here
     * - For production, consider adding user consent checks (GDPR)
     */
    private fun initializeMonitoring() {
        // Crashlytics is auto-initialized by Firebase SDK
        // To manually initialize or configure:
        // FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        
        // Analytics initialization (if needed):
        // FirebaseAnalytics.getInstance(this)
        
        android.util.Log.d("LinkerApp", "Monitoring initialized (Crashlytics auto-enabled)")
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
    
    /**
     * Schedule periodic cleanup of expired passive sessions
     * 
     * Runs every 15 minutes to free up resources from inactive passive accounts.
     */
    private fun scheduleSessionCleanup() {
        val request = PeriodicWorkRequestBuilder<com.linker.app.core.session.SessionCleanupWorker>(
            15, TimeUnit.MINUTES
        ).build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "linker_session_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
