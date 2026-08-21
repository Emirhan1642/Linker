package com.linker.app.data.service

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.linker.app.core.receiver.PreferenceConstants
import com.linker.app.core.service.OfflineMessagingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for starting and stopping OfflineMessagingService.
 *
 * This class is thread-safe and uses StateFlow for reactive state management.
 * Service state is persisted in SharedPreferences to survive app restarts.
 *
 * Implements Requirement 11.5:
 * - Start/stop service with API level checks
 * - Track service running state using StateFlow (NOT getRunningServices - deprecated)
 * - Persist service state in SharedPreferences
 *
 * @property context Application context injected by Hilt
 */
@Singleton
class OfflineMessagingServiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    private val sharedPreferences = context.getSharedPreferences(
        PreferenceConstants.OFFLINE_MESSAGING_PREFS,
        Context.MODE_PRIVATE
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private var isReceiverRegistered = false

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isRunning = intent?.getBooleanExtra("isRunning", false) ?: false
            updateServiceState(isRunning)
        }
    }

    init {
        // Load initial state asynchronously
        scope.launch {
            val savedState = sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
            _isServiceRunning.value = savedState
        }

        registerReceiver()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                serviceStateReceiver,
                IntentFilter(OfflineMessagingService.ACTION_SERVICE_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
            Log.d(TAG, "Service state receiver registered")
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unregisterReceiver()
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(serviceStateReceiver)
                isReceiverRegistered = false
                Log.d(TAG, "Service state receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver already unregistered", e)
            }
        }
    }

    /**
     * Start the offline messaging service.
     *
     * @return true if started successfully, false otherwise
     */
    fun startService(): Boolean {
        return try {
            Log.d(TAG, "Starting service...")
            val intent = Intent(context, OfflineMessagingService::class.java).apply {
                action = OfflineMessagingService.ACTION_START
            }

            // Use ContextCompat for better compatibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Using startForegroundService (API 26+)")

                // Check if we can start foreground service (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (!activityManager.isBackgroundRestricted) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        Log.w(TAG, "Background restrictions prevent service start")
                        return false
                    }
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
            } else {
                Log.d(TAG, "Using startService (API < 26)")
                context.startService(intent)
            }

            // Update state optimistically
            updateServiceState(true)
            Log.d(TAG, "Service start command sent")

            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.e(TAG, "Foreground service start not allowed (Android 12+)", e)
            }
            updateServiceState(false)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service: ${e.message}", e)
            updateServiceState(false)
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
            updateServiceState(false)

            Log.d(TAG, "Service stopped successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
            false
        }
    }

    /**
     * Toggle scanning in the service.
     *
     * @return true if command sent successfully, false otherwise
     */
    fun toggleScanning(): Boolean {
        return try {
            val intent = Intent(context, OfflineMessagingService::class.java).apply {
                action = OfflineMessagingService.ACTION_TOGGLE_SCANNING
            }

            context.startService(intent)
            Log.d(TAG, "Toggle scanning command sent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling scanning: ${e.message}", e)
            false
        }
    }

    /**
     * Update service running state.
     *
     * This method is thread-safe and synchronously persists state to SharedPreferences
     * before updating the StateFlow.
     *
     * @param isRunning true if service is running, false otherwise
     */
    private fun updateServiceState(isRunning: Boolean) {
        synchronized(this) {
            sharedPreferences.edit()
                .putBoolean(KEY_SERVICE_RUNNING, isRunning)
                .commit()

            _isServiceRunning.value = isRunning
            Log.d(TAG, "Service state updated: $isRunning")
        }
    }

    /**
     * Check if service is currently running.
     *
     * Uses StateFlow instead of deprecated getRunningServices().
     */
    fun isServiceRunning(): Boolean {
        return _isServiceRunning.value
    }

    /**
     * Check if service manager is ready to start the service.
     */
    fun isReady(): Boolean {
        return true
    }

    companion object {
        private const val TAG = "OfflineMessagingServiceManager"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
