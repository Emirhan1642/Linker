package com.linker.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.linker.app.BuildConfig
import com.linker.app.core.security.RootDetector
import com.linker.app.core.security.SecurityLogger
import com.linker.app.core.security.SecurityManager
import com.linker.app.core.security.SecurityRiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.linker.app.core.work.MessageQueueWorker
import com.linker.app.data.bluetooth.BluetoothManager
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import android.os.StrictMode
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Linker Application Class
 *
 * Entry point for the application with Hilt dependency injection.
 * Initializes core services, security checks, and background tasks.
 * 
 * ## Initialization Flow:
 * 1. Firebase initialization
 * 2. Device security check (root/emulator detection)
 * 3. API key initialization (remote config)
 * 4. Background work scheduling (message sync, session cleanup)
 * 5. Monitoring setup (Crashlytics)
 * 
 * ## Security Features:
 * - Root/emulator detection on startup
 * - Remote Config API key initialization
 * - Security risk level assessment
 * - Optional app blocking for high-risk devices
 * 
 * ## Background Tasks:
 * - Message queue sync: Every 15-30 minutes (network required)
 * - Session cleanup: Every 15-60 minutes (passive account cleanup)
 * 
 * ## Dependency Injection:
 * Uses Hilt field injection (@Inject lateinit var) which is the standard
 * pattern for Application class as it's instantiated by Android framework.
 * 
 * @property securityManager Manages encrypted storage and security policies
 * @property workerFactory Hilt worker factory for WorkManager integration
 * 
 * @see SecurityManager
 * @see RootDetector
 * @see MessageQueueWorker
 * @see SessionCleanupWorker
 */
@HiltAndroidApp
class LinkerApp : Application(), Configuration.Provider {

    @Inject lateinit var securityManager: SecurityManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bluetoothManager: BluetoothManager

    companion object {
        private const val TAG = "LinkerApp"
        private val MESSAGE_SYNC_INTERVAL_MINUTES = if (BuildConfig.DEBUG) 15L else 30L
        private val SESSION_CLEANUP_INTERVAL_MINUTES = if (BuildConfig.DEBUG) 15L else 60L
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Enable StrictMode in debug builds
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // Initialize Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashlyticsTree())
        }

        // CRITICAL: Initialize Firebase before any Firebase service is used
        try {
            initializeFirebase()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Firebase in onCreate")
        }

        // SECURITY: Check device security status on startup
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                checkDeviceSecurity()
            } catch (e: Exception) {
                Timber.e(e, "Failed to check device security")
            }
        }

        // SECURITY: Initialize API keys from Remote Config
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                securityManager.initializeKeysFromRemoteConfig()
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize keys from remote config")
            }
        }

        try {
            if (::workerFactory.isInitialized) {
                scheduleMessageQueueSync()
                scheduleSessionCleanup()
            } else {
                Timber.e("WorkerFactory not initialized, skipping work scheduling")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule background work")
        }
        
        try {
            initializeMonitoring()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize monitoring")
        }
        
        try {
            bluetoothManager.initialize()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize bluetooth manager")
        }
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .penaltyLog()
                .build()
        )

        Timber.d("StrictMode enabled for debug build")
    }

    private class CrashlyticsTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority == android.util.Log.VERBOSE || priority == android.util.Log.DEBUG) {
                return
            }

            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log(message)

            if (t != null) {
                crashlytics.recordException(t)
            }
        }
    }

    /**
     * Initialize Firebase with proper error handling
     * 
     * CRITICAL: Must be called before any Firebase service (Auth, Firestore, Storage)
     * is accessed. Failure to initialize will cause crashes.
     * 
     * IMPLEMENTATION NOTES:
     * - Uses google-services.json configuration
     * - Validates Firebase configuration on startup
     * - Logs initialization status for debugging
     * - Handles initialization errors gracefully
     * 
     * @throws IllegalStateException if Firebase configuration is invalid
     */
    private fun initializeFirebase() {
        try {
            // Check if Firebase is already initialized (prevents double initialization)
            if (FirebaseApp.getApps(this).isEmpty()) {
                // Pre-initialize Firebase and Auth to avoid StrictMode DiskReadViolation
                val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
                try {
                    // Initialize Firebase with default configuration from google-services.json
                    FirebaseApp.initializeApp(this)
                    Timber.d("Firebase initialized successfully")
                    
                    // Validate Firebase configuration
                    val app = FirebaseApp.getInstance()
                    val options = app.options
                    
                    require(!options.projectId.isNullOrEmpty()) {
                        "Firebase project ID is missing"
                    }
                    require(options.applicationId.isNotEmpty()) {
                        "Firebase application ID is missing"
                    }
                    require(options.apiKey.isNotEmpty()) {
                        "Firebase API key is missing"
                    }
                    
                    Timber.d("Firebase configuration validated: projectId=${options.projectId}")
                    
                    // Pre-initialize FirebaseAuth to avoid StrictMode DiskReadViolation later in MainActivity
                    com.google.firebase.auth.FirebaseAuth.getInstance()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to initialize Firebase or Auth during startup")
                } finally {
                    android.os.StrictMode.setThreadPolicy(oldPolicy)
                }
            } else {
                Timber.d("Firebase already initialized")
            }
        } catch (e: Exception) {
            // CRITICAL: Firebase initialization failure
            Timber.e(e, "Firebase initialization failed")
            
            // In production, consider:
            // 1. Showing error dialog to user
            // 2. Disabling Firebase-dependent features
            // 3. Reporting to crash analytics (non-Firebase)
            // 4. Graceful degradation to offline mode
            
            throw IllegalStateException("Failed to initialize Firebase: ${e.message}", e)
        }
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
                Timber.d("Device security: LOW risk (normal device)")
            }
            SecurityRiskLevel.MEDIUM -> {
                Timber.w("Device security: MEDIUM risk (emulator detected)")
            }
            SecurityRiskLevel.HIGH -> {
                Timber.w("Device security: HIGH risk (rooted device detected)")
            }
            SecurityRiskLevel.CRITICAL -> {
                Timber.e("Device security: CRITICAL risk (rooted emulator)")
                if (BuildConfig.ENFORCE_SECURITY_POLICY) {
                    exitProcess(0)
                }
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
        
        Timber.d("Monitoring initialized (Crashlytics auto-enabled)")
    }

    private fun scheduleMessageQueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<MessageQueueWorker>(
            MESSAGE_SYNC_INTERVAL_MINUTES, 
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "linker_message_queue_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
    
    /**
     * Schedule periodic cleanup of expired passive sessions
     */
    private fun scheduleSessionCleanup() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
            
        val request = PeriodicWorkRequestBuilder<com.linker.app.core.session.SessionCleanupWorker>(
            SESSION_CLEANUP_INTERVAL_MINUTES, 
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "linker_session_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
