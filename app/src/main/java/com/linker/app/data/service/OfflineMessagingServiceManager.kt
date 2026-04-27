package com.linker.app.data.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.linker.app.core.service.OfflineMessagingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for starting and stopping OfflineMessagingService.
 * 
 * Implements Requirement 11.5:
 * - Start/stop service with API level checks
 * - Track service running state using StateFlow (NOT getRunningServices - deprecated)
 * - Persist service state in SharedPreferences
 */
@Singleton
class OfflineMessagingServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val sharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    private val _isServiceRunning = MutableStateFlow(
        sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
    )
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    companion object {
        private const val PREFS_NAME = "offline_messaging_prefs"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
    
    /**
     * Start the offline messaging service.
     * 
     * @return true if started successfully, false otherwise
     */
    fun startService(): Boolean {
        return try {
            val intent = Intent(context, OfflineMessagingService::class.java).apply {
                action = OfflineMessagingService.ACTION_START
            }
            
            // Start foreground service based on API level
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // Update state
            updateServiceState(true)
            
            true
        } catch (e: Exception) {
            // Log error
            false
        }
    }
    
    /**
     * Stop the offline messaging service.
     * 
     * @return true if stopped successfully, false otherwise
     */
    fun stopService(): Boolean {
        return try {
            val intent = Intent(context, OfflineMessagingService::class.java).apply {
                action = OfflineMessagingService.ACTION_STOP
            }
            
            context.startService(intent)
            
            // Update state
            updateServiceState(false)
            
            true
        } catch (e: Exception) {
            // Log error
            false
        }
    }
    
    /**
     * Toggle scanning in the service.
     */
    fun toggleScanning() {
        try {
            val intent = Intent(context, OfflineMessagingService::class.java).apply {
                action = OfflineMessagingService.ACTION_TOGGLE_SCANNING
            }
            
            context.startService(intent)
        } catch (e: Exception) {
            // Log error
        }
    }
    
    /**
     * Update service running state.
     * 
     * Persists to SharedPreferences and updates StateFlow.
     */
    private fun updateServiceState(isRunning: Boolean) {
        // Persist to SharedPreferences
        sharedPreferences.edit()
            .putBoolean(KEY_SERVICE_RUNNING, isRunning)
            .apply()
        
        // Update StateFlow
        _isServiceRunning.value = isRunning
    }
    
    /**
     * Check if service is currently running.
     * 
     * Uses StateFlow instead of deprecated getRunningServices().
     */
    fun isServiceRunning(): Boolean {
        return _isServiceRunning.value
    }
}
