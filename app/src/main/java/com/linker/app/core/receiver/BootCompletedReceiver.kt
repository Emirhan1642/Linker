package com.linker.app.core.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.analytics.FirebaseAnalytics
import com.linker.app.MainActivity
import com.linker.app.R
import com.linker.app.data.service.OfflineMessagingServiceManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for BOOT_COMPLETED event.
 * 
 * Implements Requirement 11.7:
 * - Auto-start offline messaging service on device boot
 * - Uses EntryPointAccessors pattern (NOT @AndroidEntryPoint)
 * 
 * **Why EntryPoint Pattern:**
 * BroadcastReceivers registered in AndroidManifest.xml cannot use @AndroidEntryPoint
 * because they are instantiated by the system, not by Hilt. The EntryPoint pattern
 * allows us to access Hilt dependencies manually.
 * 
 * **Usage:**
 * ```kotlin
 * val entryPoint = EntryPointAccessors.fromApplication(
 *     context.applicationContext,
 *     BootCompletedReceiverEntryPoint::class.java
 * )
 * val serviceManager = entryPoint.offlineMessagingServiceManager()
 * ```
 * 
 * **Important:**
 * - DO NOT use @AndroidEntryPoint with manifest-registered BroadcastReceivers
 * - Always use applicationContext, not activity context
 * - EntryPoint must be installed in SingletonComponent
 * 
 * @see <a href="https://dagger.dev/hilt/entry-points">Hilt Entry Points</a>
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val NOTIFICATION_ID_SERVICE_FAILURE = 1001
        
        @Volatile
        private var cachedEntryPoint: BootCompletedReceiverEntryPoint? = null
        
        private fun getEntryPoint(context: Context): BootCompletedReceiverEntryPoint {
            return cachedEntryPoint ?: synchronized(this) {
                cachedEntryPoint ?: EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    BootCompletedReceiverEntryPoint::class.java
                ).also { cachedEntryPoint = it }
            }
        }
    }
    
    /**
     * Hilt entry point for accessing dependencies.
     */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface BootCompletedReceiverEntryPoint {
        fun offlineMessagingServiceManager(): OfflineMessagingServiceManager
    }
    
    private fun getEncryptedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PreferenceConstants.OFFLINE_MESSAGING_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences, falling back to regular", e)
            context.getSharedPreferences(PreferenceConstants.OFFLINE_MESSAGING_PREFS, Context.MODE_PRIVATE)
        }
    }
    
    private fun logBootEvent(
        context: Context,
        eventName: String,
        params: Map<String, Any> = emptyMap()
    ) {
        try {
            val analytics = FirebaseAnalytics.getInstance(context)
            val bundle = Bundle().apply {
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Double -> putDouble(key, value)
                    }
                }
            }
            analytics.logEvent(eventName, bundle)
            

            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log boot event: ${e.message}")
        }
    }

    private suspend fun startServiceWithRetry(
        serviceManager: OfflineMessagingServiceManager,
        maxRetries: Int = 3
    ): Boolean {
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt < maxRetries) {
            try {
                Log.d(TAG, "Attempting to start service (attempt ${attempt + 1}/$maxRetries)")
                
                val started = serviceManager.startService()
                
                if (started) {
                    Log.d(TAG, "Service started successfully on attempt ${attempt + 1}")
                    return true
                }
                
                Log.w(TAG, "Service start returned false, retrying...")
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Service start failed on attempt ${attempt + 1}: ${e.message}")
            }
            
            attempt++
            
            if (attempt < maxRetries) {
                val delayMs = 1000L * (1 shl (attempt - 1))
                Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }
        
        Log.e(TAG, "Failed to start service after $maxRetries attempts", lastException)
        return false
    }

    private fun showServiceFailureNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "service_errors",
                    "Service Errors",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications about service startup issues"
                }
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = NotificationCompat.Builder(context, "service_errors")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Offline Messaging Service")
                .setContentText("Failed to start on boot. Tap to retry.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(createRetryPendingIntent(context))
                .build()
            
            notificationManager.notify(NOTIFICATION_ID_SERVICE_FAILURE, notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    private fun createRetryPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "retry_service_start")
        }
        
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }
        
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring non-boot action: ${intent.action}")
            return
        }
        
        try {
            val pm = context.packageManager
            pm.getPermissionInfo(
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
                0
            )
            Log.d(TAG, "RECEIVE_BOOT_COMPLETED permission available")
        } catch (e: Exception) {
            Log.e(TAG, "RECEIVE_BOOT_COMPLETED permission issue", e)
        }
        
        Log.d(TAG, "BOOT_COMPLETED received, processing...")
        logBootEvent(context, "boot_completed_received")
        
        val sharedPreferences = getEncryptedPreferences(context)
        val autoStartEnabled = sharedPreferences.getBoolean(PreferenceConstants.KEY_AUTO_START_ON_BOOT, false)
        
        val bootCount = sharedPreferences.getInt(PreferenceConstants.KEY_BOOT_COUNT, 0) + 1
        sharedPreferences.edit()
            .putLong(PreferenceConstants.KEY_LAST_BOOT_TIME, System.currentTimeMillis())
            .putInt(PreferenceConstants.KEY_BOOT_COUNT, bootCount)
            .apply()
        
        if (!autoStartEnabled) {
            Log.d(TAG, "Auto-start disabled, skipping service start")
            logBootEvent(context, "boot_auto_start_disabled")
            return
        }
        
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logBootEvent(context, "boot_service_start_attempt")
                
                val entryPoint = getEntryPoint(context)
                val serviceManager = entryPoint.offlineMessagingServiceManager()
                
                @Suppress("SENSELESS_COMPARISON")
                if (serviceManager == null) {
                    Log.e(TAG, "Service manager is null, cannot start service")
                    logBootEvent(context, "boot_service_manager_null")
                    return@launch
                }
                
                if (!serviceManager.isReady()) {
                    Log.w(TAG, "Service manager not ready, waiting...")
                    delay(2000)
                    
                    if (!serviceManager.isReady()) {
                        Log.e(TAG, "Service manager still not ready after wait")
                        logBootEvent(context, "boot_service_manager_not_ready")
                        return@launch
                    }
                }
                
                val started = startServiceWithRetry(serviceManager)
                
                if (started) {
                    Log.d(TAG, "Offline messaging service started on boot")
                    logBootEvent(context, "boot_service_started", mapOf("success" to true))
                } else {
                    Log.e(TAG, "Failed to start offline messaging service on boot")
                    logBootEvent(context, "boot_service_failed", mapOf("success" to false))
                    showServiceFailureNotification(context)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service on boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
