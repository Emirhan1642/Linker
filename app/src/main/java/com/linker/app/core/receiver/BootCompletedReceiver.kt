package com.linker.app.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.linker.app.data.service.OfflineMessagingServiceManager
import dagger.hilt.android.EntryPointAccessors

/**
 * BroadcastReceiver for BOOT_COMPLETED event.
 * 
 * Implements Requirement 11.7:
 * - Auto-start offline messaging service on device boot
 * - Uses EntryPointAccessors pattern (NOT @AndroidEntryPoint)
 * 
 * IMPORTANT: DO NOT use @AndroidEntryPoint with BroadcastReceivers.
 * Use EntryPointAccessors pattern to get Hilt dependencies.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val PREFS_NAME = "offline_messaging_prefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
    }
    
    /**
     * Hilt entry point for accessing dependencies.
     */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface BootCompletedReceiverEntryPoint {
        fun offlineMessagingServiceManager(): OfflineMessagingServiceManager
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        
        Log.d(TAG, "BOOT_COMPLETED received")
        
        // Check if auto-start is enabled
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStartEnabled = sharedPreferences.getBoolean(KEY_AUTO_START, false)
        
        if (!autoStartEnabled) {
            Log.d(TAG, "Auto-start disabled, skipping service start")
            return
        }
        
        try {
            // Get dependencies using EntryPointAccessors
            val appContext = context.applicationContext
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                BootCompletedReceiverEntryPoint::class.java
            )
            
            val serviceManager = entryPoint.offlineMessagingServiceManager()
            
            // Start service
            val started = serviceManager.startService()
            
            if (started) {
                Log.d(TAG, "Offline messaging service started on boot")
            } else {
                Log.e(TAG, "Failed to start offline messaging service on boot")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service on boot: ${e.message}", e)
        }
    }
}
